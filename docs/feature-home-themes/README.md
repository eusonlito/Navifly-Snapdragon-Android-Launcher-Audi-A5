# Temas de la home

La pantalla principal admite cuatro disposiciones. Todas muestran los mismos
datos, conservan el mapa y comparten comportamiento: estadísticas, avisos de
puntos de interés, Asistente IA y animación de arranque.

| Tema | Captura | Disposición |
|---|---|---|
| **Clásico** | [`dashboard.png`](../screenshots/es/dashboard.png) | Esferas circulares de velocidad y RPM sobre el mapa; es el valor por defecto. |
| **Horizonte** | [`theme-horizon.png`](../screenshots/es/theme-horizon.png) | Mapa a pantalla completa con paneles de cristal. Velocidad y RPM en barras horizontales, combustible en columna vertical. |
| **Facetas** | [`theme-facets.png`](../screenshots/es/theme-facets.png) | Barras verticales rectas con relleno coral-magenta a los lados y los ocho bloques en una fila inferior. |
| **Telemetría** | [`theme-telemetry.png`](../screenshots/es/theme-telemetry.png) | Mapa enmarcado a la izquierda y fichas a la derecha: luces de cambio, marcha, cuadrícula de 3×2 valores, combustible y testigos. |

## Uso

- El tema se elige en **Ajustes → Sistema → Tema**. El cambio es inmediato y
  no recrea el mapa.
- Cada tema tiene variante de día y de noche y sigue el modo Automático, Claro
  u Oscuro del mapa.
- El estilo del mapa es siempre el que elige el usuario; ningún tema lo
  personaliza.
- Los bloques de datos se reordenan con una pulsación larga. **Cada tema guarda
  su propio orden**, porque la composición cambia entre temas. El orden de los
  botones de la barra de comandos es común a todos.
- En Horizonte, combustible y testigos tienen panel propio y no entran en la
  reordenación. En Telemetría, los seis valores se reordenan en las dos
  direcciones y las fichas de combustible y testigos se intercambian entre sí.

## Reglas visuales comunes

- Escala de velocidad de 0 a 240 km/h y zona roja de RPM desde 4.500 en todos
  los temas.
- El indicador de combustible usa los colores del Clásico según el nivel: cian
  normal, ámbar con poco combustible y rojo en reserva.
- Los testigos son el mismo grupo en todos los temas: puertas, cinturón, freno
  de mano y luces.
- En los temas nuevos, cada caja usa un único margen interior en los cuatro
  lados. Los textos se miden por la altura de cifras y mayúsculas
  (`capHeightBounds`), no por el alto de la fuente, para que el margen visual
  sea el mismo arriba, abajo y a los lados.
- Sólo se usan fuentes del sistema; Telemetría usa Roboto Black para las cifras.

## Código

Ficheros del paquete `com.lito.a5launcher.ui.home`, salvo el último, que está en
`com.lito.a5launcher.ui.components`:

| Fichero | Responsabilidad |
|---|---|
| `HomeTheme.kt` | Enumeración, tokens de cada tema, clave de preferencias y orden por tema |
| `HomeState.kt` | Estado observable compartido por todos los temas |
| `HomeReorder.kt` | Reordenación lineal y en cuadrícula con pulsación larga |
| `HomeCommands.kt`, `HomeBlocks.kt`, `HomePanels.kt` | Botones, bloques, testigos, paneles y textos comunes |
| `LinearGauge.kt` | Barras horizontales, bandas verticales y luces de cambio |
| `*HomeLayout.kt` | Una disposición por tema |
| `MapTheming.kt` | Paleta oscura del mapa y área visible para centrar el vehículo |

Preferencias en `launcher_settings`: `home_theme`, `footer_block_order`
(Clásico), `footer_block_order_<tema>` (resto) y `top_command_order`.

## Documentos

- [Plan y decisiones](PLAN.md)
- [Mockups aprobados y prompts](mockups/)
