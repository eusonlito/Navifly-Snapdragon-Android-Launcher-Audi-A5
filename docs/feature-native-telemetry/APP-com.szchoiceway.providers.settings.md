# com.szchoiceway.providers.settings

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `7d0ad489dc25a9e008093104cceb13ab3d67b23be70160267fc23f2be7061398`
- **Versión:** `14`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 0 · **Estado:** `decompilada`
- **DEX:** 1 · **Librerías nativas:** 0
- **Permisos declarados:** 0
<!-- GENERATED:END -->

## Relevancia para la telemetría

Crítica como almacenamiento compartido, pero no es fuente del hardware.

## Interfaces y datos

- Provider `com.szchoiceway.eventcenter.SysVarProvider`.
- URI `content://com.szchoiceway.eventcenter.SysVarProvider/SysVar`.
- SQLite `SysVar.db`, tabla `sysvar(keyname VARCHAR PRIMARY KEY, keyvalue VARCHAR)`.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `SysVarProvider` decompilado sin errores | CRUD de pares clave/valor y notificación de cambios. |
| Alta | Sin servicios ni librerías nativas | No lee MCU; EventCenter escribe los estados. |

## Búsquedas realizadas

- Autoridad, esquema SQLite, `query/insert/update/delete`, `notifyChange`.

## Preguntas pendientes

- Inventariar sólo claves dinámicas que no estén ya cubiertas por callbacks.
