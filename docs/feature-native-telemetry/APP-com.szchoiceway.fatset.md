# com.szchoiceway.fatset

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `fb5a56a36207bffb5524e982cbbff252fe1e56f90cb1f17b3e1fdbb680c82163`
- **Versión:** `1.02026-03-30:15-56`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 1 · **Estado:** `decompilada_parcial`
- **DEX:** 1 · **Librerías nativas:** 2
- **Permisos declarados:** 20
<!-- GENERATED:END -->

## Relevancia para la telemetría

Configuración de fábrica. Puede revelar claves y modos del MCU, pero modificar
valores entraña riesgo y no aporta telemetría en tiempo real.

## Interfaces y datos

- Cliente AIDL de EventCenter.
- Lectura/escritura extensa de `SysProviderOpt`.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | Sin implementación serie propia | Configura EventCenter mediante sus contratos. |
| Alta | UID 1000/firma de plataforma | Sus capacidades no son transferibles a una APK ordinaria. |
| Alta | `KSW_DATA_CAN_PROTOCOL_RELAY_MODE` | Es un interruptor de relay CAN, no un lector adicional ni un selector seguro de protocolo Audi. |

## Búsquedas realizadas

- `IEventService`, `SysProviderOpt`, valores de tipo CAN/vehículo.

## Preguntas pendientes

- Con MMI 3G Basic y perfil `[0] 3G` operativo, no cambiar opciones de este APK
  como método de descubrir consumo/autonomía: no hay evidencia de que añadan
  campos al frame `00/A1/19`.
