# Recursos visuales de terceros

## Material Design Icons

Los testigos `seatbelt`, `car-brake-parking` y `car-light-dimmed` proceden de
[Material Design Icons](https://github.com/Templarian/MaterialDesign), mantenido
por Pictogrammers. Se distribuyen bajo licencia Apache 2.0.

Los trazados SVG originales se convirtieron directamente a recursos Vector
Drawable de Android. No se alteró su geometría; Compose aplica únicamente el
tamaño y el color correspondientes al estado del vehículo.

## Recursos propios del proyecto

Los recursos visuales específicos del launcher, el marcador de posición, el
robot del Asistente IA y la animación incluida en `boot/default` fueron creados
por el propietario del proyecto. Se publican como parte del proyecto, sin que
el uso descriptivo de marcas de terceros implique afiliación o patrocinio.

## Testigo de puertas abiertas

El testigo `ic_witness_door.xml` fue creado expresamente para el proyecto.
Representa una vista cenital sólida con las puertas abiertas, con
menos líneas interiores que el pictograma anterior para conservar la lectura a
38 dp. El trazado original se integra sin deformación dentro de un `viewport`
cuadrado centrado. Las cuatro puertas laterales se extraen como capas vectoriales
con el mismo `viewport`; Compose puede mantener la carrocería en alerta y
repintar cada puerta cerrada con el color inactivo según sus bits independientes
de telemetría.

## Marcador de posición del mapa

El marcador fue creado expresamente para el proyecto. Su ligera inclinación y
profundidad conservan bien la lectura sobre el mapa a 38 dp.

El SVG original se rasteriza a 768 px y se recorta al contorno alfa antes de
generar los tres recursos `drawable-nodpi`: azul normal, amarillo sin GPS y rojo
sin red. Las variantes se derivan siempre de esa rasterización original y sólo
cambian el matiz de los píxeles coloreados; el borde blanco, las sombras y el
volumen se conservan sin deformación, margen transparente sobrante ni tintado
global en tiempo de ejecución.
