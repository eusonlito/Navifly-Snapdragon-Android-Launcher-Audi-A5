# Especificación de Telemetría CAN Bus - Audi A5 (Plataforma Szchoiceway)

Este documento sirve como la especificación técnica definitiva y memoria de ingeniería inversa del protocolo de comunicación de la MCU del vehículo en la plataforma Android de Szchoiceway.

---

## 1. Arquitectura de Comunicación (IPC Binder)

A diferencia de los sistemas Android tradicionales que dependen de broadcasts locales (`Intent`), la telemetría del chasis de alta frecuencia en dispositivos Szchoiceway se transmite a través de un canal exclusivo de **IPC Binder** integrado en el servicio:

* **Servicio Remoto:** `com.szchoiceway.eventcenter.EventService`
* **Acción del Intent:** `com.szchoiceway.eventcenter.EventService`
* **Paquete:** `com.szchoiceway.eventcenter`

### El Mecanismo de Callback
La aplicación se conecta al servicio y registra un callback implementando la interfaz remota `ICallbackfn` mediante el método de registro de salpicadero (`setDashBoardCallback`), que utiliza el ID de transacción Binder **`120`**.

El método receptor tiene la firma:
```java
void notifyEvt(int msg_what, int arg1, int arg2, byte[] bArr, String str);
```

---

## 2. Decodificación de Mensajes Crudos (Bus CAN)

Los datos del chasis viajan empaquetados en un array de bytes (`bArr`) que se actualiza continuamente. La siguiente especificación detalla el significado físico de cada tipo de mensaje (`msg_what`):

### A. Mensaje Maestro de Telemetría (`msg_what == 90`)
Este mensaje es el canal principal de datos físicos del motor y velocidad de desplazamiento. El array `bArr` tiene una longitud mínima de 20 bytes.

* **Velocidad Instantánea (km/h):**
  * **Bytes:** `bArr[11]` (alto) y `bArr[12]` (bajo).
  * **Fórmula:** `int speed = ((bArr[11] & 0xFF) * 256) + (bArr[12] & 0xFF)`
  * **Rango:** `0` a `300` km/h.

* **Revoluciones por Minuto (RPM):**
  * **Bytes:** `bArr[13]` (alto) y `bArr[14]` (bajo).
  * **Fórmula:** `int rpm = ((bArr[13] & 0xFF) * 256) + (bArr[14] & 0xFF)`
  * **Rango:** `0` a `8000` RPM.

* **Valor de distancia corto (significado no confirmado):**
  * **Bytes:** `bArr[5]` (alto) y `bArr[6]` (bajo).
  * **Fórmula:** `int range = ((bArr[5] & 0xFF) * 256) + (bArr[6] & 0xFF)`
  * **Nota:** Panel lo llama `m_itvXuShiLiCheng`, pero el nombre no demuestra
    que sea autonomía. En los recorridos reales permanece a cero; no debe usarse
    como autonomía nativa sin una correlación controlada.

* **Velocidad Media (km/h):**
  * **Bytes:** `bArr[9]` (alto) y `bArr[10]` (bajo).
  * **Fórmula:** `int avgSpeed = ((bArr[9] & 0xFF) * 256) + (bArr[10] & 0xFF)`

* **Nivel de Combustible (Litros):**
  * **Bytes:** `bArr[15]` (alto) y `bArr[16]` (bajo).
  * **Fórmula:** `int fuel = ((bArr[15] & 0xFF) * 256) + (bArr[16] & 0xFF)`

* **Odómetro Total (Kilómetros, firmware Audi A5):**
  * **Bytes:** `bArr[20]`, `bArr[21]` y `bArr[22]`.
  * **Fórmula:** `int odometer = ((bArr[20] & 0xFF) << 16) |
    ((bArr[21] & 0xFF) << 8) | (bArr[22] & 0xFF)`
  * **Validación:** La secuencia real `03 5B 6F` produce `220015 km`, coherente
    con el cuadro físico del vehículo.

* **Temperatura Exterior (°C):**
  * **Bytes:** `bArr[17]` (alto) y `bArr[18]` (bajo).
  * **Fórmula (Cásting Signed Short de 16 bits):**
    ```kotlin
    val tempByte1 = bArr[17].toInt() and 0xFF
    val tempByte2 = bArr[18].toInt() and 0xFF
    val tempRaw = (((tempByte1 shl 8) or tempByte2).toShort()).toInt()
    val outsideTemp = tempRaw * 0.1
    ```
  * **Rango:** `-40°C` a `77.5°C`.

---

### B. Mensaje de temperatura de climatización (`msg_what == 95`)

En el firmware del Audi A5 las 1.814 tramas observadas tienen exactamente dos
bytes. El primero varía entre `0x0B` y `0x0E`; el segundo permanece en cero.

EventCenter construye este mensaje a partir de
`mCarAirStruct.iAirLeftTemp/iAirRightTemp` cuando `KSW_SHOW_AIR` está activo; en
caso contrario envía `{0, 0}`. No es temperatura de refrigerante. La conversión
visual depende del codificado del climatizador y no está validada para este
coche, por lo que el launcher no lo utiliza.

---

### C. Mensaje de Estado de Puertas (`msg_what == 93`)
Se actualiza inmediatamente al abrir o cerrar cualquier componente del chasis. El byte de control es `bArr[5]`.

* **Estructura del Bitmask (`bArr[5]`):**
  * **Bit 5 (Valor `32` / `0x20`):** Puerta Delantera Derecha Abierta.
  * **Bit 4 (Valor `16` / `0x10`):** Puerta Delantera Izquierda Abierta.
  * **Bit 7 (Valor `128` / `0x80`):** Puerta Trasera Derecha Abierta.
  * **Bit 6 (Valor `64` / `0x40`):** Puerta Trasera Izquierda Abierta.
  * **Bit 3 (Valor `8` / `0x08`):** Capó/frontal abierto.
  * **Bit 2 (Valor `4` / `0x04`):** Maletero abierto.

La asignación procede directamente de los nombres de variables de EventCenter.
El SVG visto desde arriba puede requerir transformar la orientación visual, pero
no se deben intercambiar los bits en la capa de datos.

---

### D. Cinturones de Seguridad y Freno de Mano (`msg_what == 91`)
El evento `91` actúa como notificación asíncrona de cambio. El estado real se consume de forma segura del almacén del sistema (`ContentProvider`):
* **Freno de mano activo (Parking Brake):** `sysProvider.getRecordBoolean("KESAIWEI_RECORD_PARK", false)`
* **Cinturón del conductor (Seat Belt):** `sysProvider.getRecordBoolean("KESAIWEI_RECORD_BELT", false)`

La captura nueva contiene 15 notificaciones 91 y cuatro tramas distintas. El bit
`0x08` de `bArr[5]` coincide con el freno de mano, mientras que el cinturón se
confirma exclusivamente con los cambios de `KESAIWEI_RECORD_BELT`.

---

### E. Resumen de estado y actitud (`msg_what == 96`)

EventCenter construye siempre diez bytes: tipo de marcha, luces de posición,
intermitente izquierdo, intermitente derecho y seis bytes de
`mAxisAttitudeData`. Panel separa los pares 4–5, 6–7 y 8–9 como valores con signo,
aunque su escala física sigue sin estar confirmada. En la captura se observaron:

* `00000000000000000000` — 1.298 veces.
* `00010000000000000000` — 516 veces.

Por tanto, sí contiene marcha e iluminación en sus cuatro primeros bytes; no
contiene autonomía, consumo ni clima. Los ejes permanecen sin uso en la interfaz.

La captura del 05-08-2026 contiene 1.923 mensajes idénticos
(`00010000000000000000`), coherentes con un tipo de marcha 0, luces activas,
intermitentes apagados y actitud sin datos. Se requieren maniobras controladas
para validar los ejes.

### F. Validación adicional del 05-08-2026

El recorrido `can_bus_log_20260805_212226.jsonl` valida los siguientes datos:

- 1.923 muestras de telemetría principal durante 4 min 48 s.
- velocidad máxima de 46 km/h y régimen máximo de 2.308 RPM;
- combustible real con resolución entera, de 51 a 50 litros;
- odómetro real, de 221.183 a 221.184 km;
- distancia de 0,92 km según GPS y 0,86 km integrando velocidad CAN;
- apertura y cierre de la puerta delantera derecha mediante el mensaje 93;
- cambios reproducibles de cinturón, freno de mano y luces mediante sus claves
  de `SysVarProvider`.

La autonomía y la velocidad media incluidas en el mensaje 90 permanecen a
cero. El mensaje 95 tampoco ofrece una temperatura de refrigerante creíble en
esta unidad. Esos campos no deben presentarse como datos nativos del coche.

En esta captura `backcar_connected`, `backcar_360_active` y `camera_owner` sólo
aparecen como estados iniciales (`false`, `false` y `0`) y no cambian. Por tanto,
el recorrido no permite identificar todavía la señal que abre la interfaz de
marcha atrás ni extraer las distancias de los sensores de aparcamiento.

---

## 3. Elementos no cubiertos nativamente

Durante la deconstrucción analítica de la app de tablero de fábrica, se identificó que **ciertos parámetros requeridos por la interfaz NO son emitidos de forma nativa por la MCU** o no son transferidos a la capa de usuario del sistema operativo:

1. **Marcha seleccionada:** `getGearType()` permite reconocer `R` y `N`. En los
   modos de avance, el launcher conserva el estimador numérico 1–6 basado en la
   relación RPM/velocidad, porque es la funcionalidad elegida para la interfaz.
2. **Consumo medio y autonomía:** no existe un bus de consumo acumulado ni una
   autonomía nativa validados. El launcher estima primero un caudal en litros
   por hora a partir de RPM y velocidad. Mientras circula conserva la heurística
   provisional de consumo instantáneo y la convierte mediante
   `caudal = l/100 km × km/h / 100`; con el motor encendido y el coche detenido
   integra `0,7 l/h`. Cada intervalo monotónico añade combustible y distancia,
   y el consumo mostrado es siempre `litros acumulados / kilómetros × 100`
   desde la primera distancia positiva, sin umbral mínimo. Antes de recorrer
   distancia muestra `0,0`. La estimación visible se acota a `15,0 l/100 km`,
   el máximo útil del cuadro: durante el arranque o una parada prolongada el
   ralentí puede añadir litros con una distancia casi nula y la división dejaría
   de ser representativa. El acumulador interno continúa registrando ese
   combustible para que el consumo posterior y la autonomía no lo ignoren.

   Un toque sobre `Consumo` abre una comparación de diagnóstico. `Consumo
   Calculado` es la estimación anterior; `Consumo Simple` divide entre la
   distancia los descensos de litros enteros confirmados por CAN durante el
   viaje. El segundo valor no se limita a 15 L/100 km y avanza por escalones,
   porque el vehículo no entrega decimales. Se conserva al recrear el servicio
   durante el mismo arranque y no interviene en el valor principal ni en la
   autonomía.

   El nivel CAN, que sólo cambia en litros enteros, inicializa un depósito
   virtual del que se descuenta el caudal estimado para evitar saltos. Cada
   descenso confirmado de tres litros corrige suavemente el factor del modelo;
   un aumento de al menos tres litros reinicializa la referencia como repostaje.
   Para la autonomía se combina un 60 % del consumo de los últimos 20 km y un
   40 % del consumo total del viaje. Si en el futuro el campo nativo de
   autonomía entrega un valor mayor que cero, ese dato tiene prioridad. Todos
   estos resultados siguen siendo estimaciones de interfaz, no datos CAN.
3. **Tiempo y distancia de la sesión:** la sesión comienza con la primera muestra
   de velocidad mayor que cero. Su duración es la diferencia entre ese instante
   y `SystemClock.elapsedRealtime()`, un reloj monotónico que no se altera cuando
   el dispositivo corrige por Internet una fecha inicial incorrecta. Una vez
   iniciada, la duración sigue avanzando durante semáforos y otras paradas. La
   distancia integra la velocidad CAN con ese mismo reloj. `TelemetryService`
   mantiene estos cálculos, además del consumo medio estimado, aunque Waze u otra
   aplicación esté en primer plano. Inicio, distancia y acumuladores se guardan
   periódicamente junto a `Settings.Global.BOOT_COUNT`: sobreviven a una
   recreación del proceso dentro del mismo arranque y se descartan al reiniciar
   el dispositivo.
4. **Distancia desde el último repostaje:** se integra en segundo plano con la
   misma velocidad CAN y se conserva entre reinicios del dispositivo. El valor
   vuelve automáticamente a cero cuando dos muestras consecutivas, con el coche
   detenido, confirman un aumento de al menos tres litros. Este umbral y la
   confirmación evitan interpretar como repostaje las oscilaciones normales del
   aforador. Además, mientras el vehículo permanece detenido la referencia no
   aprende descensos: así, dos lecturas bajas transitorias durante el arranque
   no rebajan la referencia y su posterior recuperación no puede simular un
   repostaje. Los descensos sólo actualizan la referencia una vez que el coche
   circula. Un salto único de 3 litros o más se considera ambiguo incluso si
   continúa al iniciar la marcha, porque su recuperación podría cumplir por sí
   sola el umbral de repostaje. Los saltos normales de 1–2 litros sí se confirman
   en movimiento. La referencia de combustible y la distancia se guardan en
   `distance_since_refuel`, separadas de la sesión del arranque.
5. **Modo Clima:** No se transmiten bits de dirección de aire ni estado de
   compresor AC en el dashboard. El launcher no muestra ningún bloque de clima.
6. **Odómetro Total (KM totales):** En este firmware se recibe como entero de
   24 bits en los bytes `20`, `21` y `22` del mensaje 90. Por ejemplo,
   `03 5B 6F` equivale a `220015 km`. El nombre `mTotalLiCheng` de Panel está en
   desuso, pero esos mismos bytes coinciden con el odómetro físico del vehículo.

---

## 4. Valores iniciales y procedencia en el emulador

El emulador no dispone del servicio físico de la MCU. Por tanto, una captura del
emulador combina estados neutros y valores iniciales, y no demuestra que esos
datos procedan del vehículo:

| Campo | Valor inicial | Procedencia al conectar la unidad |
| :--- | :--- | :--- |
| Velocidad / RPM | `0` | Mensaje 90 |
| Puertas | Cerradas | Mensaje 93 |
| Freno de mano / cinturón | Inactivos | Provider del sistema tras mensaje 91 |
| Combustible | `0 L` | Mensaje 90 |
| Autonomía | Sin dato (`—`) | Estimada al recibir combustible y comenzar a circular; el dato CAN tendría prioridad |
| Temperatura exterior | Sin dato (`—`) | Mensaje 90 |
| Marcha | `N` tras el estado inicial | `getGearType()` y estimador cinemático |
| Consumo | `0.0`, calculado desde la primera distancia | Litros estimados acumulados / distancia, no dato CAN |
| Odómetro | Sin dato (`—`) hasta la primera trama | Bytes 20-22 del mensaje 90 |

Los valores procedentes del coche no recibidos se representan con `—`. Marcha,
consumo y autonomía calculada son excepciones funcionales documentadas.

### 4.1 Marcha manual estimada para el Audi A5 2.0 TDI 2015

El firmware ChoiceWay no expone una marcha numérica 1–6 fiable. Los estados
`R` y `N` sí se respetan directamente desde `getGearType()`; durante la marcha
hacia delante se calcula la relación `RPM / km/h` y se compara con un perfil
específico de la caja manual longitudinal de seis velocidades del A5:

| Marcha | RPM por km/h esperadas | Origen de la calibración |
| :---: | ---: | :--- |
| 1 | 115,0 | Muestras estables del viaje real del 24-07-2026 |
| 2 | 61,0 | Muestras estables del viaje real del 24-07-2026 |
| 3 | 38,5 | Muestras estables del viaje real del 24-07-2026 |
| 4 | 29,0 | Progresión de relaciones de la caja Audi 0B1 |
| 5 | 23,0 | Progresión de relaciones de la caja Audi 0B1 |
| 6 | 18,2 | Progresión de relaciones de la caja Audi 0B1 |

No se trata de un evento CAN de marcha engranada. El resultado depende de que
embrague, motor y ruedas estén acoplados, y las tres relaciones superiores
deben contrastarse con una futura captura que contenga conducción estable en
4.ª, 5.ª y 6.ª.

Para evitar saltos durante cambios, embrague pisado o muestras desincronizadas,
el estimador:

* por debajo de 900 RPM muestra `N`; desde 900 RPM estima la marcha siempre que
  haya al menos 4 km/h, sin una segunda zona muerta a bajo régimen;
* acepta como máximo un 16 % de desviación respecto a la relación esperada;
* exige dos muestras consecutivas para cambiar el número mostrado;
* consume una única muestra atómica de velocidad, RPM y modo por cada mensaje
  90, sin contar por separado las actualizaciones de cada campo;
* conserva brevemente la última marcha ante una muestra inválida y vuelve a
  `N` tras cuatro muestras inválidas consecutivas;
* aplica `R` y `N` inmediatamente cuando ChoiceWay los confirma.

---

## 5. Replay de un viaje real en builds debug

La variante `debug` admite un JSONL de replay empaquetado como asset. Cuando el
binding a
`com.szchoiceway.eventcenter.EventService` no está disponible, como sucede en el
emulador, `TelemetryService` reproduce automáticamente los callbacks AIDL del
archivo respetando su cronología original.

* `scripts/emulator.sh` acepta cualquier recorrido local mediante
  `--replay RUTA_JSONL`. El repositorio no incluye ni presupone un log de
  diagnóstico predeterminado.
* El CAN se empaqueta en una copia temporal del APK debug. El APK base, el log y
  la variante `release` permanecen intactos.
* El recorrido vuelve a comenzar tras una pausa de 2 segundos.
* Las tramas se convierten de `bytes_hex` a `ByteArray` y entran por el mismo
  callback y decodificador que usa la unidad física.
* Si el servicio real se conecta, el replay se cancela inmediatamente.
* La variante `release` no empaqueta el archivo ni puede activar el replay.
* CAN y GPS seleccionan una única base temporal para todo el ciclo. Prefieren
  `elapsed_realtime_nanos` cuando está presente en todos los eventos
  reproducibles, por lo que una corrección de fecha del dispositivo no altera
  pausas ni sincronización. Los logs antiguos sin ese campo usan `timestamp`
  como fallback para el ciclo completo; nunca se mezclan ambos relojes.
* `scripts/emulator.sh` reproduce además las entradas `GPS_LOCATION` del mismo
  JSONL mediante la API de control gRPC del emulador, conservando posición,
  velocidad y rumbo. El mapa no usa
  coordenadas de demostración ni una posición fija durante este flujo. Esta
  herramienta requiere instalar previamente `requirements-emulator.txt`.
* También reproduce por timestamp los estados iniciales y cambios de
  `KESAIWEI_RECORD_BELT`, `KESAIWEI_RECORD_PARK` y luces capturados desde el
  ContentProvider cuando están presentes en el fichero seleccionado.
* En los eventos 91, el bit `0x08` se usa como refuerzo para reproducir el estado
  del freno de mano.

La reproducción confirma que los bytes 20-22 del mensaje 90 contienen el
odómetro real. Los bytes 5-6 permanecen en cero durante toda esta captura; la
autonomía usa como fallback combustible / consumo medio calculado × 100.
La barra visual de combustible emplea internamente 63 litros como capacidad
total del depósito del perfil Audi A5 Sportback 2.0 TDI 150 CV de 2015. Esta
capacidad sólo normaliza el dibujo: el valor numérico continúa procediendo del
evento del vehículo y la interfaz no muestra unidades.

---

## 6. Contraste con Panel, openlauncher y openlauncher-TW

Se revisaron las fuentes de ambos launchers y la aplicación Panel decompilada:

* `openlauncher` y `openlauncher-TW` obtienen velocidad, distancia y datos de
  viaje mediante GPS y cálculos propios. No contienen una fuente adicional de
  telemetría Szchoiceway que permita aportar estos datos con fiabilidad.
* Panel confirma velocidad, RPM, combustible, temperatura, velocidad media y
  autonomía en el mensaje 90 con las posiciones ya documentadas.
* Panel interpreta bytes 20-22 como `XuShiLiCheng` para los modos internos
  `iModeSet == 1` o `iModeSet == 3`. Sin embargo, en nuestro Audi producen
  `220015`, coinciden con el odómetro físico y evolucionan lentamente durante el
  viaje. Para este firmware se tratan como odómetro real.
* El campo separado `mTotalLiCheng` de Panel nunca se asigna; el dato útil está en
  el bloque de 24 bits anterior.
* Panel no decodifica consumo medio. El valor numérico del launcher es una
  estimación local claramente documentada.

La prioridad sigue siendo utilizar datos del coche. Mientras no estén
disponibles, la versión normal conserva los cálculos de consumo y marcha que
forman parte del producto.

---

## 7. Capturador de eventos del vehículo

El capturador es un proyecto Android independiente ubicado en `../a5-logger`.
Se compila desde ese directorio con `./compile.sh` y copia el resultado en
`a5-launcher/../a5-logger/out/CanLogger.apk`.

La aplicación de diagnóstico se maneja con una sola acción principal:
`CAPTURAR EVENTOS`. No expone filtros, selección de aplicaciones, listas de
acciones ni el antiguo modo `Car-Only View`. Al comenzar, escanea automáticamente
los APK centrales `com.szchoiceway.*` y `com.choiceway.*`, además de los paquetes
automotrices detectados en el inventario:

* `com.tl.tpms` (TPMS);
* `com.ivicar.avm` (cámara 360);
* `com.ankai.cardvr` (DVR);
* `com.zjinnova.zlink` (CarPlay/Android Auto);
* `com.ecar.assistantnew` (servicios del vehículo);
* `com.zoulou.dab` (DAB);
* `com.ms.ms2160` (hardware de vídeo).

Las aplicaciones Android y de usuario no relacionadas no se escanean: sus APK
contienen miles de acciones genéricas sin valor CAN y harían más lenta la captura.
La captura comienza inmediatamente con un catálogo auditado de 86 acciones del
fabricante y del sistema. El análisis de los APK se ejecuta después en segundo
plano y añade nuevos filtros al receptor sin detener los ya activos, evitando
perder eventos durante una preparación que en el dispositivo puede tardar
varios segundos. El analizador reconoce cadenas incrustadas en DEX, recursos y
manifiestos tanto en ASCII como en UTF-16.

Se registra simultáneamente:

* todos los callbacks recibidos mediante `setDashBoardCallback()` del
  `EventService`, sin filtrar por `msg_what`;
* los broadcasts de CAN, MCU, cinturón, freno, marcha atrás y controles originales
  que Panel registra expresamente, más las acciones adicionales seleccionadas al
  escanear cualquier APK;
* todos los cambios legibles de `Settings.Global` y `Settings.System`, junto con
  sus instantáneas iniciales;
* una instantánea inicial y los cambios del proveedor
  `content://com.szchoiceway.eventcenter.SysVarProvider/SysVar`, incluyendo claves
  hasta ahora desconocidas. Tras la instantánea completa, el logger compara los
  valores y sólo escribe las claves modificadas para no multiplicar el tamaño del
  fichero. Esto permite buscar fuentes nativas antes de calcular valores en el
  launcher.
* las muestras Android de ubicación GPS y de red, incluyendo timestamp
  monotónico, latitud, longitud, proveedor, precisión, altitud, velocidad y
  rumbo cuando estén disponibles. Estas muestras permiten reproducir después
  un recorrido real sincronizado con los eventos CAN.

Los extras desconocidos de los broadcasts se convierten a estructuras JSON
acotadas y seguras, incluidos arrays, bytes, colecciones y objetos Parcelable.
La escritura del JSONL se realiza en una cola dedicada y se vacía al detener la
captura; de esta forma una ráfaga de callbacks no bloquea el hilo Binder ni la
interfaz. Cada sesión deja constancia de los canales activos, número de acciones
registradas, ampliaciones procedentes de APK, errores de permisos y total de
eventos escritos.

Aunque el inventario no aparece en la interfaz simplificada, cada pulsación de
`CAPTURAR EVENTOS` vuelve a generar silenciosamente
`installed_apps_inventory.json` en el mismo directorio de documentos del logger.
El fichero conserva la etiqueta y el nombre de paquete de todas las aplicaciones
instaladas, no sólo las automotrices.

El decodificador del logger conserva el valor corto de los bytes 5–6 como
`short_distance_raw`, sin presentarlo como autonomía, y distingue el odómetro
real de los bytes 20–22. También evita la antigua interpretación incorrecta del
mensaje 95 como refrigerante: contiene códigos del climatizador.

Android no ofrece a una aplicación normal un receptor comodín para todos los
broadcasts del sistema. Las acciones deben enumerarse y los broadcasts protegidos,
explícitos o no exportados pueden seguir siendo invisibles. Por eso la captura
combina la lista real de Panel con Binder y el `SysVarProvider`; estas dos últimas
fuentes son las más completas para la telemetría del fabricante.

### Resultado de la captura del 24-07-2026

Un recorrido de diagnóstico local analizado durante el desarrollo contenía 5.461 callbacks
AIDL durante 249 segundos:

| Mensaje | Cantidad | Resultado |
| :--- | ---: | :--- |
| 90 | 1.814 | Velocidad, RPM, 56 L, temperatura exterior y odómetro 220024–220025 |
| 91 | 15 | Notificaciones de cinturón y freno; estado confirmado en el provider |
| 93 | 4 | Dos estados de puertas |
| 95 | 1.814 | Primer byte variable entre 11 y 14; Panel lo trata como temperatura |
| 96 | 1.814 | Bloque de sensores de 10 bytes; sólo cambia el segundo byte |

Se capturaron 166 claves iniciales del `SysVarProvider` y nueve cambios. Son
especialmente relevantes:

* `KESAIWEI_RECORD_BELT` y `KESAIWEI_RECORD_PARK`, ya integradas;
* `KSW_DATA_SMALL_LIGHT_ON`, integrado como fuente principal del modo
  claro/oscuro automático del mapa;
* `NAV_PACKAGENAME` y `NAV_ACTIVITYNAME`, que confirman Waze;
* `KSW_REAL_DISPLAY_RESOLUTION=896x2400`,
  `KSW_UI_RESOLUTION=2400x900` y `KSW_DATA_MIPI_SCREEN_NAME=CY2400X900`.

No se recibió ningún broadcast de fabricante durante la sesión. Los bytes 5–6
del mensaje 90 permanecieron siempre en cero y ninguna clave del provider contiene
autonomía, consumo medio o distancia restante. Por tanto, la autonomía superior
a 900 km visible en el cuadro original todavía no es accesible por una fuente
validada.

Como fallback, el launcher mantiene dos magnitudes separadas:

* el consumo mostrado es la media física del viaje (`litros acumulados /
  kilómetros × 100`), aparece desde la primera distancia positiva, incorpora
  el caudal de ralentí y se limita visualmente a 15 L/100 km;
* la autonomía no usa directamente esa media volátil. Parte de un consumo
  aprendido persistente de 6,0 L/100 km, lo corrige lentamente en bloques de
  un kilómetro y deja que el consumo de los últimos 20 km gane hasta un 60 % de
  influencia durante los primeros 10 km recorridos.

Así, con el motor al ralentí se descuentan litros del depósito virtual y la
autonomía disminuye por el combustible realmente estimado, pero no se desploma
por dividir ese combustible entre una distancia inicial casi nula. El contador
de tiempo mantiene su contrato independiente: empieza con la primera velocidad
positiva. El aprendizaje de autonomía se conserva entre arranques, mientras que
tiempo, distancia y consumo del viaje se reinician con el dispositivo. Una
futura lectura nativa válida siempre tiene prioridad.

El combustible de ralentí se incluye en el consumo medio mostrado y se resta
del depósito virtual, pero no se convierte en consumo por kilómetro para el
modelo de autonomía. La base aprendida del viaje permanece fija durante esa
sesión; los bloques completados preparan la base del siguiente arranque sin
provocar escalones al cruzar cada kilómetro. Además, el último caudal sólo se
extrapola durante dos segundos: si EventCenter deja de enviar CAN, se detiene la
integración hasta recibir otra trama real.

Autonomía y odómetro usan la agrupación correspondiente al idioma/región
configurados.

### Protocolo de investigación del mensaje 96

La interfaz actual no incluye marcadores manuales ni un modo especial para este
mensaje: el logger captura el código 96 junto con todos los demás eventos. Esta
simplificación evita que los controles de investigación se confundan con filtros
o acciones sobre el vehículo.

Si se realiza otra prueba controlada, se debe anotar externamente la hora de
cada cambio de estado y mantenerlo durante al menos 10 segundos. Después puede
correlacionarse esa hora con los timestamps del JSONL. La prueba debe hacerse
con el vehículo detenido y en un lugar seguro.

El logger añade a `parsed_data` la marcha cruda, luces, intermitentes y los tres
pares de actitud con signo de cada mensaje 96. Se conservan el hexadecimal
completo y la escala cruda de los ejes para no asignar unidades físicas todavía
no demostradas.

### Capturas nocturnas del 24-07-2026

Se revisaron también dos capturas locales no versionadas:

* `can_bus_log_20260724_210050.jsonl`: 2.439 callbacks AIDL en 807 ciclos;
* `can_bus_log_20260724_213040.jsonl`: 9.927 callbacks AIDL en 3.307 ciclos.

Ambos confirman la decodificación del mensaje 90. Se observan velocidades,
revoluciones, combustible entre 41 y 43 L, temperatura exterior entre 22,5 y
23 °C y odómetro real avanzando de 220.194 a 220.195 km. Los campos de autonomía
y velocidad media permanecen en cero, por lo que estas capturas tampoco aportan
una autonomía CAN utilizable.

El mensaje 95 permanece fijo en `0B00` durante ambas sesiones. El mensaje 96
permanece fijo en `00010000000000000000` en sus 4.114 apariciones conjuntas; no
hay marcadores manuales ni transiciones que permitan asociar sus bytes a luces,
intermitentes, volante o pendiente. Los nuevos registros no justifican añadir
ningún indicador al launcher.

El mensaje 91 presenta tres variantes por sesión y el provider registra cambios
de `KESAIWEI_RECORD_PARK` y `KESAIWEI_RECORD_BELT`, reafirmando que el callback
debe actuar como notificación y que el estado fiable debe leerse del provider.
Sólo aparecen dos cambios de puertas en la primera sesión y ninguno en la
segunda. No se recibió ningún broadcast adicional del fabricante.
