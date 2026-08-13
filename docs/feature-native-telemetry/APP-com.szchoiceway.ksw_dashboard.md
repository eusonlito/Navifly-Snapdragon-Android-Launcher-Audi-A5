# com.szchoiceway.ksw_dashboard

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `e2e89823972089572822eb51ef4437eaa5d608df60057e43d65caa1b6a872347`
- **Versión:** `1.02026-03-30:15-56`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 0 · **Estado:** `decompilada_parcial`
- **DEX:** 2 · **Librerías nativas:** 0
- **Permisos declarados:** 3
<!-- GENERATED:END -->

## Relevancia para la telemetría

Crítica como consumidor de referencia. No lee hardware: registra el callback de
EventCenter y demuestra cómo el fabricante interpreta los mensajes 90/91/93/96.

## Interfaces y datos

- Binder `IEventService.setDashBoardCallback`.
- Callback `notifyEvt`; rutas confirmadas para mensajes 90, 91, 93, 95 y 96.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `MainActivity.refreshData` | Mapea velocidad, RPM, combustible, temperatura, velocidad media y odómetro. |
| Alta | callback en `MainActivity` | Confirma que Panel depende de EventCenter, no del puerto serie. |
| Alta | `MainActivity.refreshData` | No lee `b[7..8]` ni muestra el consumo `youhao`; no conserva un valor alternativo de consumo. |
| Media | parser del mensaje 96 | Divide tres pares de actitud, pero no documenta su escala física. |

## Búsquedas realizadas

- `setDashBoardCallback`, `notifyEvt`, `refreshData`, mensajes `90..96`.

## Preguntas pendientes

- Determinar la escala de los tres ejes y si alguna interfaz los visualiza.
- Confirmar si el callback único impide registrar Panel y logger a la vez.
