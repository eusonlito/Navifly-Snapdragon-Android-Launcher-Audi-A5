# Tarea: mapa vectorial integrado en el cockpit

## Objetivo

Integrar el mapa vectorial como fondo continuo del área central del cockpit,
manteniendo los relojes de velocidad y revoluciones en primer plano y sin
afectar a la disponibilidad del launcher cuando no haya GPS o red.

## Requisitos

- El mapa debe ocupar todo el espacio entre la cabecera y la barra inferior.
- Los relojes conservan tamaño, centros, telemetría y fondo opaco por encima del
  mapa.
- La cámara sigue la posición de forma fluida, mantiene el marcador fijo y gira
  según una dirección estabilizada.
- Los tiles cercanos se precargan y la caché se recupera también durante un
  arranque sin conexión.
- El mapa admite estilos claros y oscuros, aunque el modo elegido por el usuario
  prevalece sobre la detección automática.
- Los fallos de MapLibre, red o GPS no deben bloquear cabecera, relojes,
  telemetría ni controles.
- El botón MMI usa los aros Audi sin cambiar su acción ni su área pulsable.
- Las etiquetas no alcanzadas de los relojes se muestran en blanco al 90 %.

## Restricciones

- No modificar cálculos de telemetría, navegación, marcha ni alertas.
- No convertir la caché en una descarga masiva u offline de rutas.
- Mantener la geometría diseñada para `2400x896 @ 320 dpi`.

## Resultado esperado

La composición final debe mantener un cockpit funcional mientras el mapa carga
de forma asíncrona. El plan de implementación y verificación está en
[PLAN.md](PLAN.md); la especificación completa permanece en [MAP.md](MAP.md).
