# com.szchoiceway.customerui

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `07ae2c09a7a706ff1660629bb56b0814448d84d128646328a44eb41a5cad9ebb`
- **Versión:** `1.02026-03-30:15-53`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 1 · **Estado:** `decompilada_parcial`
- **DEX:** 3 · **Librerías nativas:** 0
- **Permisos declarados:** 22
<!-- GENERATED:END -->

## Relevancia para la telemetría

Consumidor general del servicio y del provider. Útil para descubrir contratos
alternativos, pero no contiene el lector serie.

## Interfaces y datos

- Copias AIDL de `IEventService`/`ICallbackfn`.
- Helpers de conexión a EventCenter y múltiples `SysProviderOpt`.
- Receptor del broadcast `ZXW_SENDBROADCAST8902MOD`, subtipo 25.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | AIDL y `EventServiceHelps` | Se apoya en el servicio central del fabricante. |
| Media | vistas de vehículo/status bar | Consume estados derivados; no añade campos nativos confirmados. |
| Alta | `BaseLauncherView` | Lee del broadcast de EventCenter autonomía (`xushilicheng`), combustible (`youLiang`) y consumo (`youhao`). |

## Búsquedas realizadas

- `IEventService`, `ICallbackfn`, `EventServiceHelps`, `SysProviderOpt`.

## Preguntas pendientes

- No necesita más análisis para consumo/autonomía: confirma los extras, pero
  no los calcula ni los obtiene de otra fuente.
- Revisar sólo si buscamos una acción concreta del MMI o un broadcast ausente.
