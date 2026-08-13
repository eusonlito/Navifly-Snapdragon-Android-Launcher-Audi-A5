# Protocolo MCU confirmado

Los offsets son del array completo recibido por EventCenter. La evidencia se
considera alta cuando el propio servicio asigna nombre al campo y el log real
lo reproduce; media cuando sólo lo consume Panel.

## `00/A1/10` — estado inmediato

| Campo | Codificación | Confianza |
|---|---|---|
| Luces de posición | `b[5] & 0x01` | Alta |
| Auto-park | `b[5] & 0x02` | Alta |
| Tecla P | `b[5] & 0x04` | Alta |
| Freno de mano | `b[5] & 0x08` | Alta |
| Cinturón | `b[6] & 0x01` | Alta |
| Tipo de marcha | `(b[6] >> 1) & 0x03` | Alta |
| Intermitente izquierdo | `b[6] & 0x08` | Alta |
| Intermitente derecho | `b[6] & 0x10` | Alta |

El valor de marcha `3` significa marcha atrás. Falta asignar de forma controlada
los valores 0–2; no deben traducirse por intuición.

## `00/A1/12` — aperturas

| Bit de `b[5]` | Nombre interno de EventCenter |
|---:|---|
| `0x80` | trasera derecha |
| `0x40` | trasera izquierda |
| `0x20` | delantera derecha |
| `0x10` | delantera izquierda |
| `0x08` | capó/frontal |
| `0x04` | maletero/trasera |

Esta asignación explica el anterior efecto espejo en el launcher. La orientación
visual del SVG debe tratarse aparte de la semántica del coche.

## `00/A1/13` — volante

Dirección: bit alto de `b[5]`; magnitud: `((b[5] & 0x7f) << 8) | b[6]`, limitada
por EventCenter a 540 grados.

## `00/A1/14` y `15` — radar

`14` contiene cuatro sensores delanteros en `b[5..8]`; `15`, cuatro traseros.
Cuando el tamaño declarado es 7, `b[9..10]` añaden laterales. Falta calibrar la
unidad física de cada valor.

## `00/A1/19` — bloque de conducción

EventCenter reenvía la trama como mensaje dashboard 90. Panel interpreta:

| Campo | Bytes | Decodificación |
|---|---:|---|
| autonomía anunciada (`xushilicheng`) | 5–6 | entero big-endian |
| consumo anunciado (`youhao`) | 6–7 | entero big-endian, `÷ 10`, expresado como `m/L` |
| velocidad media | 9–10 | entero big-endian |
| velocidad instantánea | 11–12 | entero big-endian |
| RPM | 13–14 | entero big-endian |
| combustible | 15–16 | entero big-endian |
| temperatura | 17–18 | entero con signo |
| odómetro en modos 1/3 | 20–22 | entero big-endian de 24 bits |

Además genera el mensaje dashboard 96, de diez bytes:

1. tipo de marcha;
2. luces de posición;
3. intermitente izquierdo;
4. intermitente derecho;
5. seis bytes de actitud/ejes (`mAxisAttitudeData`).

Por tanto, el mensaje 96 no es desconocido: es un resumen de estado más tres
valores de actitud de 16 bits cuya escala aún falta confirmar.

La solapación entre los campos 5–6 y 6–7 es intencionada en este firmware: se
ha confirmado contra el bytecode DEX de
`EventService.ksw_0x00_0xA1_0x19_refreshData`, no sólo contra la
decompilación Java. El callback 90 que consume Panel **no** interpreta el
consumo: salta de 5–6 a 9–10. EventCenter lo publica únicamente en el
broadcast global legacy como el extra `youhao` (`"0.0m/L"`, por ejemplo).
El byte 8 y el byte de unidades 19 no participan en esos dos recorridos.

## Registro compatible en `a5-logger`

El logger conserva el payload completo en hexadecimal y añade una vista
`parsed_data` para los mensajes 90, 91, 93, 95 y 96. Cada línea incorpora una
secuencia y `elapsed_realtime_nanos`, lo que permite correlacionar Binder,
broadcasts, providers, GPS y aplicación visible sin depender del reloj civil.

El callback de dashboard no es una lista: EventCenter sobrescribe una única
referencia. La captura AIDL del logger es por ello un modo diagnóstico exclusivo,
no un observador transparente que pueda convivir con otro consumidor. El resto
de canales son de lectura/observación y el logger nunca abre `/dev/ttyHS1` ni
envía comandos a la MCU.

## Captura controlada del 09-08-2026

Fuente: `can_bus_log_20260809_231849.jsonl`, SHA-256
`5a2647f3a624af7c6201ea111d222a092d9b486dcb07a3667e405a331fd7c18f`.
Contiene 7.507 registros durante 311,26 segundos, sin eventos descartados.

Resultados confirmados:

- 1.690 ciclos completos de los mensajes 90, 95 y 96;
- velocidad CAN entre 0 y 46 km/h, RPM entre 0 y 2.048, combustible fijo en
  25 litros y odómetro de 221.591 a 221.592 km;
- bytes 5–7 y velocidad media del mensaje 90 permanecen siempre a cero;
- el mensaje 95 alterna entre `0900` y `0B00`, reforzando que son códigos de
  climatización y no refrigerante;
- el mensaje 96 permanece en `00010000000000000000`: luces de posición activas,
  marcha cruda 0, intermitentes apagados y tres ejes sin datos;
- los 14 mensajes 91 coinciden con los cambios observados en
  `KESAIWEI_RECORD_BELT`, `KESAIWEI_RECORD_PARK` y
  `KSW_DATA_SMALL_LIGHT_ON`;
- el mensaje 93 `...12100039` confirma `0x10` como puerta delantera izquierda;
- las relaciones RPM/velocidad aportan muestras coherentes de las marchas
  estimadas 1–5: 112,70; 59,92; 38,62; 28,41 y 24,01 RPM por km/h. No hubo
  muestras de 6.ª.

El escaneo de APK descubrió el broadcast global
`com.szchoiceway.eventcenter.EventUtils.ZXW_SENDBROADCAST8902MOD`. Sus 1.680
muestras duplican velocidad, RPM, combustible, temperatura exterior y la
autonomía anunciada del mensaje 90. Incluye además un campo `youhao`, pero fue
siempre `0,0m/L`; no proporciona consumo válido. Este broadcast ofrece una vía
pasiva útil para esos campos, aunque no incluye el odómetro presente en los
bytes 20–22 del callback.

El conductor confirma que durante esta captura sí activó intermitentes, marcha
atrás y sensores PDC. Aun así no se recibieron sus transiciones como callback,
broadcast, provider ni estado Binder. En particular no apareció
`MCU_CAR_CAN_RADAR_INFO`; esta captura no permite asignar escalas ni estados a
esos elementos.

La implementación de EventCenter explica el resultado: los comandos MCU
`00/A1/13`, `14` y `15` entregan volante y radares directamente a
`MipiModeUtil` y a `CameraUtilLD/XYQ`. Los broadcasts de estos últimos sólo se
emiten cuando `KESAIWEI_SYS_CAMERA_SELECTION == 3`; en la configuración actual
la presentación nativa utiliza otra ruta interna. La marcha atrás tampoco
cambió `getGearType`, `isBackcarConnected`, `getBackcar360` ni `cameraOwner`,
por lo que probablemente la conmutación de vídeo/overlay ocurre por debajo del
contrato Android observado. Repetir la misma captura pública no aportará esos
datos: el siguiente prototipo debe estudiar una fuente privilegiada o una
instrumentación controlada de EventCenter.

## Capturas privilegiadas del 10-08-2026

Tres ZIP generados por `com.szchoiceway.logcapture` permiten ver la capa que no
aparecía en la captura pública. En las ventanas de 15 segundos, `SerialPortData`
registra directamente las tramas UART y EventCenter deja trazas de sus ramas:

- `00/A1/16 01` inicia la vista de marcha atrás y `00/A1/16 00` la termina;
- `00/A1/14` contiene las variaciones de los cuatro sensores delanteros;
- `00/A1/15` contiene las variaciones de los cuatro sensores traseros;
- `00/A1/10` mantiene los estados inmediatos ya documentados;
- `00/A1/19` continúa llegando aproximadamente a 10 Hz.

Esto corrige una conclusión anterior: los datos de marcha atrás y PDC sí llegan
a EventCenter, pero se consumen en una ruta interna y no se publican por los
canales Android observados por `a5-logger`. Por decisión de producto, cámara,
PDC y overlays nativos quedan fuera del alcance de integración. Las capturas
se usarán en adelante sólo para buscar datos incorporables al launcher, como
consumo, autonomía, marcha o control de crucero.

El segundo lote del mismo día contiene once ventanas consecutivas y se resume
en `SYSTEM-LOGS.md`. Durante la conducción (2–51 km/h), `00/A1/19` mantiene a
cero autonomía, consumo, velocidad media y su byte sin asignar. No aparece
ningún comando adicional asociado al control de crucero. El combustible se
mantiene en 24 litros y el odómetro avanza de 221.593 a 221.594 km.

El conductor confirma además que el crucero se activó durante la captura
20:59:12–20:59:27. `SerialPortData` no registró ningún comando asociado: sólo
`19`, PDC/marcha atrás (`14`, `15`, `16`) y `00/11/01`. Los campos constantes
de las 111 tramas `19` tampoco cambiaron. Con el protocolo seleccionado, el
estado y la velocidad configurada del crucero no llegan a EventCenter.
