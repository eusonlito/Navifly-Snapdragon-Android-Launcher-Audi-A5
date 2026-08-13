# com.szchoiceway.updatemcu

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `2149cdb5c416dc9edf3203916c7a333a6405aaa8b34d00ade2c2129e62ea8602`
- **Versión:** `1.02025-11-27:16-17`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 1 · **Estado:** `decompilada_parcial`
- **DEX:** 1 · **Librerías nativas:** 0
- **Permisos declarados:** 5
<!-- GENERATED:END -->

## Relevancia para la telemetría

Crítica para entender actualización, pero peligrosa e innecesaria para lectura
de telemetría. No se ejecutará durante la investigación.

## Interfaces y datos

- Cliente Binder de EventCenter.
- Servicios de actualización MCU principal, secundaria, BT y externa.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `AutoUpdateMcuService` | Entra en modo upgrade y transmite bloques mediante EventCenter/broadcasts. |
| Alta | Validación de archivo/modelo | Confirma que EventCenter también es canal de escritura crítico. |

## Búsquedas realizadas

- `enterUpgradeMode`, `send0xE7Data`, comandos `A0/E0..E3`, `IEventService`.

## Preguntas pendientes

- No investigar comandos de escritura sin imagen de recuperación y banco aislado.
