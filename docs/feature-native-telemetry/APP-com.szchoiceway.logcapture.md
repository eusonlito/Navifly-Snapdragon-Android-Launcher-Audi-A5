# com.szchoiceway.logcapture

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `40b6b76c2e4305237d169ed5720cd0ecef813396fe73eb298eba8add8cd38a33`
- **Versión:** `1.0`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 2 · **Estado:** `decompilada`
- **DEX:** 1 · **Librerías nativas:** 0
- **Permisos declarados:** 5
<!-- GENERATED:END -->

## Relevancia para la telemetría

No produce telemetría, pero es una herramienta privilegiada útil para capturar
los diagnósticos que EventCenter ya escriba durante una maniobra controlada.

## Interfaces y datos

- Permiso `READ_LOGS` y servicio `LogService` exportado.
- Ejecuta `logcat -b main -b system -b radio -b kernel -v threadtime` y archiva
  la salida junto con configuración del dispositivo.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `LogcatHelper` | Sólo recopila logs y ficheros de configuración; no abre UART ni consume CAN directamente. |
| Media | salida de EventCenter | Puede revelar trazas de comandos que EventCenter decida registrar, sin sustituirlo ni enviar órdenes. |

Las capturas reales del 10-08-2026 confirman dos detalles operativos:

- cada ejecución vuelca el anillo de `logcat` ya existente y continúa durante
  unos 15 segundos; no contiene exclusivamente esos 15 segundos;
- `LogService` limpia el subdirectorio `AndroidLog` del destino recibido antes
  de escribir. `a5-logger` usa desde entonces un directorio padre único por
  ejecución para poder conservar y encadenar varias capturas sin USB.

En las ventanas capturadas aparecen las tramas UART crudas y los métodos de
EventCenter: `00/A1/10`, `14`, `15`, `16` y el bloque periódico `19`. Esto
demuestra que `logcat` permite observar mensajes que no llegaron al callback
AIDL del logger. Los mensajes `14`/`15` y `16` pertenecen a PDC/cámara y quedan
fuera del alcance actual del launcher; no se encontró en estas tres ventanas
un dato identificable de control de crucero, consumo real o autonomía real.

El conductor confirmó después que el control de crucero estaba activado en la
captura `AndroidLogcat-2026-08-10-20.59.12.zip`. El análisis acotado de su
ventana no muestra un comando UART adicional ni cambios en los campos
constantes de `00/A1/19`. LogCapture permite observar la ausencia con confianza
alta, pero no proporciona una ruta alternativa para obtener ese estado.

## Búsquedas realizadas

- `LogcatHelper`, `LogService`, permisos y buffers capturados.

## Preguntas pendientes

- Encadenar capturas dirigidas y filtrar `EventService`/`SerialPortData` si una
  maniobra controlada requiere averiguar un subcomando no publicado. No sirve
  para obtener un valor que la MCU no haya emitido.
