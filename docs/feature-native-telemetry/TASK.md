# Tarea: acceso nativo a la telemetría

Analizar si es viable sustituir `com.szchoiceway.eventcenter` por un componente
propio que lea el hardware/firmware directamente y exponga al launcher marcha,
consumos, autonomía, control de crucero y demás telemetría de conducción útil.

## Alcance funcional

Se investigan exclusivamente datos que puedan integrarse de forma estable en el
launcher. Quedan fuera de alcance climatización, cámara, parking/PDC y cualquier
overlay que el firmware dibuje por encima de las aplicaciones. El objetivo ya no
es reproducir esas interfaces ni sustituir EventCenter por completo, sino añadir
una capa propia y modular para la telemetría que EventCenter no publica.

## Restricciones

- Dispositivo concreto Navifly/Mekede con Android 14 (API 34).
- No modificar el launcher funcional durante la investigación.
- No enviar comandos al MCU hasta conocer su efecto y disponer de recuperación.
- Cada APK se extrae y analiza una sola vez por SHA-256.
- Las conclusiones deben incluir evidencia, confianza y preguntas pendientes.
- El ZIP original, APK, `.so` y fuentes decompiladas permanecen fuera de Git.

## Entregable inicial

Una decisión técnica fundamentada y una especificación parcial de los datos de
conducción; no un reemplazo prematuro de EventCenter.
