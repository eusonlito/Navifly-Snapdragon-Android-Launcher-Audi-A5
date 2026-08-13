# com.szchoiceway.settings

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `7b2f4a2562f4a07529de496b7391530be61f8dcbac72400821f29eb802528c15`
- **Versión:** `1.02026-03-30:15-57`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 1 · **Estado:** `decompilada_parcial`
- **DEX:** 1 · **Librerías nativas:** 2
- **Permisos declarados:** 20
<!-- GENERATED:END -->

## Relevancia para la telemetría

Panel de ajustes de la unidad. Consumidor/configurador, no fuente de telemetría.

## Interfaces y datos

- Cliente AIDL de EventCenter y `SysProviderOpt`.
- Servicios de precarga y captura de logs.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `MainActivity` y fragments | Gestionan preferencias y llaman al servicio central. |
| Alta | Sin lector serie | No evita la dependencia de EventCenter. |

## Búsquedas realizadas

- `IEventService`, `SysProviderOpt`, ajustes de CAN/cámara/vehículo.

## Preguntas pendientes

- Revisar sólo para localizar el ajuste que habilite una función concreta.
