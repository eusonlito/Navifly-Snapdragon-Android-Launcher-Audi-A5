# com.szchoiceway.eventcenter

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `7a0bd57dd825816b7b4530fc1779af885649194a23532c77b68bcab627ab164c`
- **Versión:** `1.0-2026-03-30:15-56`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 0 · **Estado:** `decompilada_parcial`
- **DEX:** 1 · **Librerías nativas:** 9
- **Permisos declarados:** 36
<!-- GENERATED:END -->

## Relevancia para la telemetría

Crítica. Es el propietario del puerto MCU, el decodificador principal y el
servicio que publica estado al resto del sistema.

## Interfaces y datos

- UART `/dev/ttyHS1`, 115200 baudios, mediante `libserial_port.so`.
- Binder `IEventService`/`ICallbackfn`; `setDashBoardCallback` es transacción 120.
- Broadcasts de marcha, cinturón, freno, luces, puertas y cámara.
- Escritura de estado en `SysVarProvider`.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `EventUtils.getMcuComDevicePath`, `EventService.openSerialPort` | En esta plataforma usa `ttyHS1` a 115200. |
| Alta | `processKSWCmd` y handlers `00/A1/10..19` | Decodifica directamente la trama MCU. |
| Alta | `onCmdKSW0x00_0xA1_0x10Event` | Marcha, luces, intermitentes, cinturón y freno son nativos. |
| Alta | handlers `12`–`15` | Puertas, volante y radares están disponibles antes del dashboard. |
| Alta | `onCmdKSW0x00_0xA1_0x19Event` | Genera mensajes dashboard 90, 95 y 96. |
| Alta | `ksw_0x00_0xA1_0x19_refreshData` | Publica autonomía, combustible, RPM, velocidad y consumo `m/L` en el broadcast 8902MOD. |
| Alta | `IEventService` | Expone marcha, pero no getters públicos de consumo, autonomía, odómetro o control de crucero. |
| Alta | UID 1000, nodo `0660` UID/GID 1000 | Una APK ordinaria no puede sustituir el lector serie. |

## Búsquedas realizadas

- `SerialPortManager`, `/dev/ttyHS1`, `processKSWCmd`, `notifyEvt`.
- `mGearType`, `radarFront`, `radarBack`, `mAxisAttitudeData`.
- `cruise`, `ACC`: sin evidencia de control de crucero; `ACC` se usa sobre todo
  para alimentación/ignición.
- Reconstrucción DEX dirigida de `ksw_0x00_0xA1_0x19_refreshData`: el consumo
  procede de `b[6..7]`, se escala a `m/L` y se envía como `youhao`; no es una
  estimación de CustomerUI ni del dashboard.

## Preguntas pendientes

- Semántica de marcha 0–2.
- Escalas de radar y actitud.
- Comando del control de crucero.
- Por qué el puente CAN Audi 3G Basic mantiene `b[5..7]` a cero: el contrato
  admite autonomía/consumo, pero la MCU no los está rellenando.
