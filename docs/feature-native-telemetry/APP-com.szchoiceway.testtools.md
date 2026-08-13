# com.szchoiceway.testtools

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `9da612ce9cb98aba4d40584b58cee893373d1f5dc4380802831d90aa20c38b70`
- **Versión:** `251022-16:50:34`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 1 · **Estado:** `decompilada_parcial`
- **DEX:** 1 · **Librerías nativas:** 1
- **Permisos declarados:** 10
<!-- GENERATED:END -->

## Relevancia para la telemetría

Herramientas de diagnóstico. Prioridad alta como apoyo futuro, no como fuente
confirmada de tramas CAN.

## Interfaces y datos

- Servicio `TestToolsService` y pantallas internas.
- Acceso a `SysProviderOpt`; no aparece `SerialPortManager` propio.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Media | Búsqueda MCU/serial | No se encontró un segundo lector de `/dev/ttyHS1`. |
| Media | Librería nativa sólo de cámara | No contiene transporte CAN evidente. |
| Alta | `ToolsModel.startMcuCheck` | Sólo emite comandos de comprobación MCU y espera `MCU_CHECK_TEST`; no expone telemetría de conducción. |

## Búsquedas realizadas

- `MCU`, `serial`, `EventService`, `SysProviderOpt`, servicios declarados.

## Preguntas pendientes

- No usar sus comprobaciones MCU durante investigación de conducción: envían
  comandos al hardware y no añaden datos de consumo, crucero o autonomía.
