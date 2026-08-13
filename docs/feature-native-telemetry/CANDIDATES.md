# Aplicaciones SzChoiceWay candidatas a datos de vehículo

Este documento es la decisión operativa para evitar reabrir APKs ya
analizados. Sólo se añade una aplicación a la cola cuando responda a una
pregunta concreta de telemetría.

| Orden | Aplicación | Qué aporta realmente | Decisión |
|---:|---|---|---|
| 1 | `com.szchoiceway.eventcenter` | Único propietario de UART/MCU; callback 90 y broadcast 8902MOD. | Fuente canónica. Investigar aquí cualquier campo nuevo. |
| 2 | `com.szchoiceway.customerui` | Confirma y consume los extras pasivos `xushilicheng`, `youLiang` y `youhao`. | Útil como contrato de broadcast; no volver a decompilar para estos valores. |
| 3 | `com.szchoiceway.ksw_dashboard` | Define los bytes que Panel toma del callback 90. | Referencia de decodificación; no es fuente ni conserva consumo. |
| 4 | `com.szchoiceway.logcapture` | Logcat privilegiado de todos los buffers. | Sólo para una captura dirigida de trazas de EventCenter. |
| 5 | `com.szchoiceway.providers.settings` | Almacén compartido de claves/valores. | Consultar únicamente claves dinámicas nuevas; no obtiene CAN. |
| 6 | `com.szchoiceway.testtools` | Diagnóstico que envía comandos MCU. | Excluido de pruebas de conducción: no aporta lecturas y puede alterar el hardware. |
| 7 | `com.szchoiceway.fatset` y `settings` | Configuración de fábrica/CAN. | No tocar para buscar datos. Con Audi MMI 3G Basic se conserva `[0] 3G`. |
| — | `updatemcu`, `avm` y overlays | Actualización o presentación de cámara/PDC. | Fuera de alcance: no los usamos para telemetría del launcher. |

## Conclusión actual

El firmware **sí define** un campo de autonomía y otro de consumo medio en el
mensaje de conducción `00/A1/19`. En esta unidad los bytes correspondientes
han sido cero en la captura real del 09-08-2026; CustomerUI, Dashboard y el
provider no contienen una segunda fuente que los complete. Por tanto, cambiar
de consumidor no resolverá consumo ni autonomía: sólo un nuevo dato emitido
por la MCU/EventCenter podría hacerlo.

## Próxima investigación válida

El lote privilegiado del 10-08-2026 descarta datos ocultos de consumo,
autonomía o crucero en `00/A1/19`. El conductor confirma que activó el control
de crucero durante la captura de las 20:59:12; no apareció otro comando UART ni
cambió ningún byte no atribuido. Se cierra la vía de aplicaciones SzChoiceWay
para obtener estado o velocidad configurada del crucero. No debe repetirse la
misma prueba sin una fuente de hardware distinta.
