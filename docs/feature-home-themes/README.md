# Temas de la home

La pantalla principal admite ocho temas. Todas muestran los mismos
datos, conservan el mapa y comparten comportamiento: estadísticas, avisos de
puntos de interés, Asistente IA y animación de arranque.

| Tema | Captura | Disposición |
|---|---|---|
| **Clásico** | [`dashboard.png`](../screenshots/es/dashboard.png) | Esferas circulares de velocidad y RPM pegadas a los bordes, sobre el mapa, con arco que se aviva hacia el valor y zona roja que se enciende; es el valor por defecto. |
| **Horizonte** | [`theme-horizon.png`](../screenshots/es/theme-horizon.png) | Mapa a pantalla completa con paneles de cristal en dos columnas alineadas abajo. Velocidad y RPM en barras horizontales a la izquierda, con relleno en degradado y punta clara; combustible horizontal y testigos a la derecha. |
| **Facetas** | [`theme-facets.png`](../screenshots/es/theme-facets.png) | Velocidad y RPM en tarjetas en forma de L (la de RPM invertida): banda vertical coral-magenta a toda altura y la lectura en el pie, sobre un fondo de facetas en tonos del mismo azul. Los ocho bloques van en una fila inferior. |
| **Telemetría** | [`theme-telemetry.png`](../screenshots/es/theme-telemetry.png) | Mapa enmarcado a la izquierda y fichas a la derecha: velocidad y RPM absolutas con barras segmentadas, marcha entre corchetes amarillos, cuadrícula abierta de 3×2 valores con pictograma junto a cada etiqueta, combustible y testigos. |
| **Submarino** | [`theme-submarine.png`](../screenshots/es/theme-submarine.png) | Ilustrado submarino: velocidad en una portilla de latón con base submarina, RPM en una portilla alargada con la marcha arriba, algas sobre roca, pez y burbujas. Barras superior e inferior transparentes, sólo con su contorno; combustible con barra segmentada. |
| **Luna** | [`theme-moon.png`](../screenshots/es/theme-moon.png) | Ilustrado lunar con bordes de neón: astronauta saludando en la esfera de velocidad, platillo con alienígena y marcha en insignia en la de RPM, cohete, robot y suelo lunar. Barra superior en tres píldoras y combustible con barra segmentada. |
| **Galaxia** | [`theme-galaxy.png`](../screenshots/es/theme-galaxy.png) | Paneles de cristal espaciales: velocidad (con cohete) y RPM con marcha (sobre una nebulosa) en barras a la izquierda, mapa en el centro, combustible sobre la Tierra y testigos sobre suelo lunar a la derecha, y los seis valores en una barra inferior. |
| **Aventura** | [`theme-adventure.png`](../screenshots/es/theme-adventure.png) | Dibujos infantiles con la estructura de Telemetría: comandos en un carril a la izquierda, mapa grande y fichas de colores para velocidad, RPM, marcha (estrella), los seis valores con iconos de colores, combustible y testigos. |

## Uso

- El tema se elige en **Ajustes → Temas**, una cuadrícula de tarjetas con una
  miniatura de cada tema; la activa queda resaltada. El cambio es inmediato y
  no recrea el mapa.
- Cada tema tiene variante de día y de noche y sigue el modo Automático, Claro
  u Oscuro del mapa.
- El estilo del mapa es siempre el que elige el usuario; ningún tema lo
  personaliza.
- Los bloques de datos se reordenan con una pulsación larga. **Cada tema guarda
  su propio orden**, porque la composición cambia entre temas. El orden de los
  botones de la barra de comandos es común a todos.
- En Horizonte, combustible y testigos tienen panel propio y no entran en la
  reordenación. En Telemetría y Aventura, los seis valores se
  reordenan en las dos direcciones y las fichas de combustible y testigos se
  intercambian entre sí; en Galaxia lo hacen sus dos paneles de la derecha,
  fuera de la barra inferior.

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
- Las tarjetas en L de Facetas se dimensionan a partir de su contenido más
  ancho (tres cifras de velocidad; marcha y una cifra con decimal en RPM).
  Sus facetas sólo aclaran u oscurecen el fondo de la tarjeta y ninguna
  arista pasa por detrás de la lectura del pie; las barras superior e
  inferior se aclaran ligeramente hacia el mapa.
- Los paneles de Horizonte son de cristal: un borde fino de luz, un brillo
  tenue en su parte superior y, en la fila de valores, separadores que se
  desvanecen en los extremos y unidades más pequeñas que la cifra.
- En Telemetría, los seis valores no tienen caja: los separan líneas finas.
  Cada etiqueta va centrada sobre su cifra y lleva colgado a su izquierda un
  pictograma de línea (`BlockIcons.kt`) en el amarillo de acento, del doble
  de alto que sus mayúsculas; el pictograma no cuenta para centrar ni resta
  ancho a la cifra. El fondo no es negro plano: una trama de fibra de carbono
  (sarga 2/2) que se oscurece hacia abajo, y las fichas son placas de grafito
  algo más claras con un contorno tenue.
- Sólo se usan fuentes del sistema; Telemetría y los temas ilustrados usan
  Roboto Black para las cifras.
- Los paneles de estadísticas de viaje, parcial y total sustituyen al mapa:
  ocupan toda su zona con el fondo, el acento y las cifras del tema, y
  reparten los valores en tres columnas (recorrido, velocidad y combustible).
  Cada tema indica qué parte de esa zona tapan sus cajas y el panel deja un
  margen fijo de 32 dp a los lados y 20 dp arriba y abajo.

## Temas ilustrados

Submarino, Luna, Galaxia y Aventura usan ilustraciones. Los marcos y
dibujos son imágenes WebP con transparencia en
`res/drawable-nodpi/illustration_<tema>_*.webp`, generadas con IA a partir de
las maquetas de cada tema; las escalas, cifras, fondos de esfera, estrellas,
burbujas se dibujan en código. Ninguna ilustración tapa cifras ni
etiquetas, y **ninguna se dibuja sobre el mapa**: van en el fondo, en los
instrumentos o en los paneles.

### Portillas: Submarino y Luna

Comparten una única disposición (`IllustratedHomeLayout`) y sólo cambian sus
colores y dibujos:

- Barra superior enmarcada con reloj, fecha, comandos y temperatura, y barra
  inferior con los ocho bloques reordenables y el combustible con barra
  segmentada. El nombre del tema no aparece en la cabecera. En Submarino las
  dos barras son transparentes: el agua y las burbujas se ven detrás, más
  tenues para no restar legibilidad a las cifras.
- Ningún adorno del fondo asoma cortado por el mapa o las portillas: las
  burbujas que quedarían parcialmente tapadas no se dibujan, y las algas
  descansan sobre su roca encima de la barra inferior.
- Velocidad y RPM pegadas a los bordes de la pantalla, a un margen del 2 % del
  alto, y tan altas como la franja central.
- El mapa ocupa todo el espacio entre ambos instrumentos: es un rectángulo
  redondeado al que se le recorta la silueta de cada instrumento más un hueco,
  así que también aprovecha las esquinas. Las estadísticas sustituyen sólo a
  esa ventana.
- Velocidad en esfera de 240° con aguja (Submarino) o arco iluminado y
  etiquetas encendidas (Luna). RPM en arco con zona roja desde 4.500.
- Las escalas se centran en el hueco transparente de cada portilla. Sus
  medidas, como fracción del ancho y alto de la imagen, están en
  `IllustratedInstruments.kt` (`PORTHOLE_OPENING_RADIUS`,
  `TallPortholeOpening`); si se sustituye una imagen hay que volver a medirlas.

### Galaxia

`GalaxyHomeLayout`: tres píldoras arriba (reloj y fecha, comandos,
temperatura), paneles de cristal con borde fino de cian a violeta, velocidad y
RPM en barras horizontales a la izquierda (0-80-160-240 km/h; RPM de 0 a 6 con
zona roja), el mapa en el centro, combustible y testigos en dos paneles a la
derecha y los seis valores en una barra inferior baja (12,5 % del alto). Las
columnas laterales ocupan el 20 % del ancho para dejar el mapa tan grande como
permiten las cifras.

### Aventura

`AdventureHomeLayout` sigue la estructura de Telemetría con fichas de colores
con relieve. Cada valor conserva su color aunque se reordene. Los iconos de
colores van junto a la etiqueta, no junto a la cifra, para que la cifra use
todo el ancho de la ficha; los valores largos se reducen.

### Imágenes

Las imágenes se recortan a su contenido y se guardan a unas dos veces su tamaño
máximo en pantalla (640 px las portillas, 300-520 px personajes y adornos,
96-120 px los iconos):

```bash
magick origen.png -trim +repage -resize 640x640! -quality 88 \
  -define webp:alpha-quality=100 \
  app/src/main/res/drawable-nodpi/illustration_sub_porthole.webp
```

No se usan capas intermedias (`saveLayer`, modos de fusión) para fundir
imágenes: el renderizador del emulador se cae con ellas. Si hace falta una
composición, se prepara antes como una imagen.

## Miniaturas del selector

`res/drawable-nodpi/theme_preview_<tema>.webp` son las capturas en inglés de
`docs/screenshots/` (Clásico usa `dashboard.png`) reducidas a 560 px de ancho.
Cuando cambie la disposición de un tema, se regeneran con:

```bash
magick docs/screenshots/theme-moon.png -resize 560x -quality 82 \
  app/src/main/res/drawable-nodpi/theme_preview_moon.webp
```

## Código

Ficheros del paquete `com.lito.a5launcher.ui.home`, salvo `ThemeSettingsPanel.kt`
y `MapTheming.kt`, que están en `com.lito.a5launcher.ui.components`:

| Fichero | Responsabilidad |
|---|---|
| `HomeTheme.kt` | Enumeración, tokens de cada tema, clave de preferencias y orden por tema |
| `HomeState.kt` | Estado observable compartido por todos los temas |
| `HomeReorder.kt` | Reordenación lineal y en cuadrícula con pulsación larga |
| `HomeCommands.kt`, `HomeBlocks.kt`, `HomePanels.kt` | Botones, bloques, testigos, paneles y textos comunes |
| `LinearGauge.kt` | Barras horizontales, bandas verticales y luces de cambio |
| `*HomeLayout.kt` | Una disposición por tema; `IllustratedHomeLayout.kt` sirve a Submarino y Luna |
| `IllustratedTheme.kt` | Paletas y tokens de los temas ilustrados |
| `IllustratedInstruments.kt`, `IllustratedGauge.kt` | Esferas, portillas, huecos medidos de cada marco y escala circular |
| `IllustratedImages.kt` | Dibujo de las imágenes ilustradas conservando sus proporciones |
| `IllustratedArt.kt` | Estrellas y burbujas dibujadas en código |
| `ThemeSettingsPanel.kt` | Panel Ajustes → Temas y miniaturas de cada tema |
| `MapTheming.kt` | Paleta oscura del mapa y área visible para centrar el vehículo |

Preferencias en `launcher_settings`: `home_theme`, `footer_block_order`
(Clásico), `footer_block_order_<tema>` (resto) y `top_command_order`.
