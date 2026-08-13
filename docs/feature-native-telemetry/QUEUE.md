# Cola de investigación

## Próximas capturas controladas

La captura del 09-08-2026 ya confirmó luces de posición, cinturón, freno de
mano, puerta delantera izquierda y relaciones de marcha 1–5. No es necesario
repetir esas maniobras salvo para investigar un problema concreto.

1. Mantener cada posición de marcha/selector y registrar el valor nativo 0–3;
   la captura anterior mantuvo siempre el valor 0.
2. Activar/desactivar control de crucero y variar su consigna, anotando velocidad
   fijada y estado visible en el cuadro.
3. Circular de forma estable en 6.ª para completar el perfil de relaciones.
4. Comparar autonomía y consumos del cuadro con cada campo crudo que cambie.
5. Buscar subcomandos desconocidos que varíen durante conducción sin investigar
   cámara, climatización, marcha atrás, PDC ni otros overlays.

## APK a revisar sólo si una pregunta lo exige

- `com.szchoiceway.customerui`: contratos alternativos de telemetría.
- `com.szchoiceway.testtools`: posibles pantallas de diagnóstico MCU.
- `com.szchoiceway.updatemcu`: transporte de actualización; no ejecutar.

## Regla de cierre

Cada pregunta resuelta se mueve a `PROTOCOL.md` y a la ficha de su APK con línea
o símbolo de evidencia. No se reabre una aplicación ya documentada sin indicar
qué pregunta nueva se busca responder.
