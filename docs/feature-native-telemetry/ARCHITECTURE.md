# Arquitectura nativa confirmada

```text
Consola/MMI y CAN
       │
       ▼
MCU del equipo ── UART `/dev/ttyHS1` @ 115200
       │
       ▼
EventCenter (UID 1000, firma de plataforma)
       ├── Binder `IEventService` / `ICallbackfn`
       ├── broadcasts explícitos y globales
       ├── `SysVarProvider` (SQLite `SysVar.db`)
       └── cámara, marcha atrás, iluminación y servicios auxiliares
              │
              ├── Dashboard/Panel
              ├── CustomerUI/Settings/FatSet/AVM
              └── a5-launcher, mediante el contrato compatible existente
```

## Entrada hardware

`EventUtils.getMcuComDevicePath()` devuelve `/dev/ttyHS1` salvo plataformas RK,
donde contempla `/dev/ttyS9`. `EventService.openSerialPort()` crea un `Device`
a `115200`, abre `SerialPortManager` y arranca hilos separados de lectura,
procesamiento y envío. `libserial_port.so` implementa la apertura JNI.

## Procesamiento

Las tramas recibidas se despachan por familia y comando. En esta unidad los
eventos del vehículo relevantes aparecen bajo `00/A1/<subcomando>`. EventCenter
mantiene estado derivado y lo vuelve a publicar en contratos Android.

## IPC

Dashboard registra `setDashBoardCallback`; la transacción Binder observada es
120. El callback recibe `notifyEvt(what, arg1, arg2, bytes, text)`. EventCenter
parece guardar una única referencia de dashboard, no una colección: registrar
otro consumidor podría sustituir al Panel. La integración debe comprobar esta
limitación antes de coexistir.

## SysVar

`content://com.szchoiceway.eventcenter.SysVarProvider/SysVar` persiste pares
`keyname`/`keyvalue` en `SysVar.db` y notifica cambios. Es almacenamiento de
estado/configuración, no la fuente física primaria.

## Barrera de privilegios

EventCenter y las aplicaciones principales son apps de sistema con UID 1000 y
firmante SHA-256 `c8a2e9bc…192ab8`. Dar a una APK normal la biblioteca serie no
le concede acceso al nodo ni supera SELinux. Por ello un reemplazo nativo exige
integración en la imagen o privilegios equivalentes; no basta instalar un APK.
