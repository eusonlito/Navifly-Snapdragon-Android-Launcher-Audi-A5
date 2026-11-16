# Plan — Temas de la home

Estado: **implementado** (noviembre de 2026).

## Objetivo

Permitir elegir entre varias disposiciones de la home sin duplicar estado ni
lógica. Recupera la idea rechazada «Añadir muchos temas visuales completos» de
[`IDEAS.md`](../IDEAS.md) por decisión del propietario.

## Arquitectura

1. `DashboardScreen` conserva todo el estado y la lógica. Expone a los temas un
   `HomeState` con `State<T>` para que cada cambio de telemetría sólo recomponga
   el texto o el indicador que lo lee.
2. `HomeActions` agrupa los callbacks; `HomeSlots` entrega el mapa y sus capas.
3. El `MapView` es único y se mueve entre temas con `movableContentOf`; cambiar
   de tema no lo recrea.
4. Cada tema recibe sus colores y tipografía por `LocalHomeTokens`.
5. `MapViewportSpec` indica qué parte del mapa tapan los paneles para centrar
   el vehículo en el área visible.

## Mockups

| Fichero | Tema |
|---|---|
| [`00-actual.png`](mockups/00-actual.png) | Clásico (estado previo) |
| [`01-horizonte.png`](mockups/01-horizonte.png) | Horizonte |
| [`02-facetas.png`](mockups/02-facetas.png) | Facetas; las barras se implementan rectas, no en ángulo |
| [`03-telemetria.png`](mockups/03-telemetria.png) | Telemetría; sin minigráfica de consumo |

Los ficheros `_common.md` y `_p1.md`–`_p3.md` son los prompts usados para
generarlos.

## Decisiones del propietario

- Día y noche siguen el modo Automático/Claro/Oscuro existente.
- Escala de velocidad de 240 km/h en todos los temas, también en Clásico.
- Zona roja de RPM desde 4.500 en todos los temas.
- Selector en Ajustes → Sistema, junto al idioma, sin miniaturas.
- Sin estilo de mapa propio por tema: se respeta siempre el del usuario.
- Orden de bloques independiente por tema; orden de comandos común.
- Colores de nivel de combustible iguales a los del Clásico en todos los temas.
- Testigos con el mismo grupo y proporciones en todos los temas.
- Publicación en una única Release.

## Validación

- Pruebas JUnit en `app/src/test/.../ui/home/`: análisis de preferencias,
  tokens, orden por tema, reordenación, escalas, luces de cambio, colores de
  combustible y lecturas de bloques.
- Clásico comparado píxel a píxel con la captura previa: sólo cambian los
  valores en vivo.
- Revisión visual en el emulador de cada tema en día y noche, cambio de tema en
  caliente conservando el mapa y animación de arranque.
