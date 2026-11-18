# Mapa integrado

> La especificación de comportamiento está en [MAP.md](MAP.md). Este documento
> describe la implementación vigente (MapLibre Native).

## Conclusión

El mapa en vivo entre las esferas de velocidad y RPM utiliza:

* **Renderizador:** MapLibre Native 13.7.1 alojado en un `AndroidView`, con
  renderizado GPU de geometría vectorial mediante `TextureView`.
* **Cartografía:** datos de OpenStreetMap mediante OpenFreeMap/OpenMapTiles.
* **Estilo:** estilo claro seleccionable entre Positron, Liberty y Bright; el
  estilo Dark se aplica según el modo de color (Automático, Claro u Oscuro) con
  una paleta propia (`MapTheming`). Los temas de home no cambian el estilo.
* **Posición:** proveedor de ubicación de Android; no depende de los eventos CAN.
* **Navegación:** el mapa es exclusivamente informativo; no calcula rutas ni se
  conecta con la sesión de Waze.

La implementación no depende de WebGL ni WebView. MapLibre gestiona fuentes
MVT, glyphs, sprites, memoria, caché ambiental y cámara; Compose mantiene el
marcador, los avisos y el recorte visual del cockpit. Se conserva visible la
atribución requerida por OpenStreetMap y OpenMapTiles.

El `MapView` usa deliberadamente `textureMode=true`. En el Navifly, el
`SurfaceView` independiente se componía a una resolución inferior cuando el
mapa pasó a ocupar los 2400 px de ancho y Android ampliaba después esa
superficie, deformando textos y geometría. `TextureView` integra el mapa en la
misma composición que los relojes superpuestos y conserva el tamaño real del
layout. El diagnóstico registra el tipo de renderer, `pixelRatio` y densidad
para detectar futuras regresiones específicas del dispositivo.

La composición se validó a 2400x896 en el emulador. El mapa conserva geometría y texto
nítidos a todo el ancho, y SurfaceFlinger no expone una superficie MapLibre
independiente susceptible de ser reescalada por el compositor del fabricante.

El mapa no forma parte de la ruta crítica de inicio: el cockpit se presenta
primero y el `MapView` se adjunta tras dos frames y una espera de 250 ms. La
ausencia de cobertura, una caché fría o una inicialización lenta de MapLibre no
impiden mostrar relojes, barras, testigos ni telemetría.

## Alcance de la primera versión

La vista central puede ofrecer:

* posición actual;
* mapa orientado según el rumbo del vehículo;
* zoom automático en función de la velocidad;
* tema oscuro coherente con el cuadro;
* caché de los mosaicos ya visitados.

No debe prometer:

* indicaciones de giro o ruta activa de Waze, porque Waze no expone esa sesión a
  otras aplicaciones;
* navegación paso a paso sin integrar además un motor de rutas;
* funcionamiento completamente offline usando directamente los servidores
  públicos de `tile.openstreetmap.org`.

## Encaje visual

El mapa ocupa el área central entre la cabecera y el pie y se sitúa por debajo
de los instrumentos. Cada tema de home indica qué bordes tapan sus paneles
(`MapViewportSpec`: márgenes obscurecidos y radio de esquina) y la cámara centra
el vehículo en el área restante, al 50 % horizontal y 80 % vertical de esa
zona. En el tema Clásico, `CockpitMapIntegrationOverlay` pinta un exterior
negro que sigue la circunferencia de cada esfera y genera los dos límites
curvos sin alterar el mapa; Horizonte y Facetas usan paneles sobre el mapa, Telemetría, Galaxia y Aventura lo enmarcan y
Submarino y Luna lo recortan con la silueta de sus instrumentos. Los paneles de estadísticas de viaje
sustituyen al mapa en todos los temas.

La interacción es mínima para evitar distracciones:

* sin controles de zoom ni brújula propios;
* zoom mediante pellizco, rotación con dos dedos y arrastre, con un botón para
  volver a la posición actual (véase MAP.md, modo exploración);
* marcador fijo al 50 % horizontal y 80 % vertical del área visible;
* textos grandes y pocos elementos cartográficos.

## Implementación

* Se solicitan `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` en tiempo de
  ejecución.
* El mapa está aislado de las esferas y de la telemetría CAN dentro de la propia
  jerarquía Compose.
* El marcador Compose permanece fijo; la cámara de MapLibre desplaza y rota el
  mapa bajo él según la posición y el rumbo.
* La cámara se actualiza en cada frame desde una posición visual independiente
  del último fix GPS. La visualización mantiene un búfer de 250 ms para
  interpolar entre muestras y, cuando alcanza la más reciente, proyecta el
  movimiento un máximo de 1.000 ms usando velocidad y rumbo.
* El cálculo visual sigue el refresco de pantalla, pero MapLibre recibe como
  máximo 30 actualizaciones de cámara por segundo y sólo cuando el movimiento
  acumulado alcanza 0,5 píxeles al zoom actual o cambia el rumbo al menos 0,2°.
  Los incrementos inferiores se acumulan en el suavizador, no se descartan.
* Durante un gesto de zoom la cámara queda exclusivamente bajo control del
  usuario. El seguimiento se reanuda al finalizar y conserva el nuevo zoom.
* La predicción nunca continúa indefinidamente: se detiene al alcanzar ese
  horizonte, por debajo de 0,8 m/s o ante una discontinuidad. Un salto superior
  a 90 m/s o más de 10 segundos entre muestras reinicia el suavizador en vez de
  animar una trayectoria falsa.
* Las muestras GPS tienen prioridad. La ubicación de red sólo se acepta si no
  existe una muestra GPS reciente, evitando alternancias entre precisiones.
* El rumbo sólo cambia con GPS y movimiento suficiente; se interpola el camino
  angular más corto y se amortigua el 35 % de cada corrección.
* Un gesto de pellizco permite zoom fraccionario continuo entre 0 y 18. El
  valor se persiste por estilo y se restaura entre sesiones.
* MapLibre mantiene tiles vectoriales en memoria durante cambios de cámara y zoom, sin
  cancelar la cola por cada muestra GPS.
* `MAPA CARGADO` depende del estado real de los tiles visibles. Mientras falte
  alguno se mantiene `CARGANDO MAPA`; crear la vista no implica que esté lista.
* MapLibre calcula la cobertura visible y precarga automáticamente los recursos
  próximos necesarios para las transiciones de cámara.
* Los recursos vistos se almacenan en la caché ambiental. El límite conjunto de
  todos los estilos es configurable entre 512 MB, 1 GB, 2 GB y 5 GB;
  2 GB es el valor inicial. Al alcanzar el límite se eliminan primero los
  mosaicos más antiguos. Sólo existe caché anticipada alrededor de la posición
  actual; no hay descarga de rutas, áreas ni mapas offline.
* Cada `style.json` validado se guarda además en almacenamiento persistente de
  la aplicación. En un arranque sin red se aplica esa copia local antes de que
  MapLibre solicite los tiles, fuentes y sprites disponibles en su caché
  ambiental. Esto permite reconstruir el mapa visto sin depender de que el
  proceso anterior siga vivo.
* Cada posición válida se persiste localmente. Si se pierde el GPS, se conserva
  la última posición y aparece `SIN SEÑAL GPS`.
* Si Android no valida una conexión a Internet aparece `SIN CONEXIÓN`; los
  mosaicos presentes en caché siguen disponibles.
* Los avisos pueden aparecer simultáneamente y se actualizan sin reiniciar el
  mapa.
* Los ajustes del launcher incluyen la sección Mapa, con cuatro tarjetas:
  apariencia (color, estilo y límite de caché), estado (mapa, red, GPS,
  coordenadas, proveedor y último error), caché y logs (con el interruptor de
  diagnóstico) y puntos de interés (véase [POI.md](../feature-poi/POI.md)).
* Al activar el diagnóstico se crea internamente, en `filesDir/map-debug-logs`,
  `map-debug-AAAAmmdd-HHMMSS-mmm.log`. Registra cambios de red,
  GPS, fase de inicialización, descarga del estilo y errores sin guardar eventos
  CAN ni una línea por cada frame.
* Para probar el mapa en el emulador, `scripts/replay-emulator-gps.py`
  reproduce las posiciones GPS de un log real respetando su temporización
  (reloj monotónico del log; si falta, las marcas de tiempo antiguas). El replay
  de la propia app está en [TELEMETRY.md](../feature-telemetry/TELEMETRY.md).
* El APK incluye `libmaplibre.so` para las ABI soportadas, pero no incorpora
  JavaScript ni WebView. El Navifly utiliza la variante ARM64.

## Estado de la validación

La superficie vectorial se validó el 29 de julio de 2026 en el emulador Navifly
2400x896: estilo, glyphs y teselas MVT cargaron sin costuras, el proceso se
mantuvo estable y no hubo errores fatales de Android o MapLibre. La
validación del driver OpenGL, del arranque como `HOME`, de varias
suspensiones/reanudaciones y de un viaje prolongado en el Navifly real no está
documentada en el repositorio.

Los ajustes permiten cambiar el estilo con efecto inmediato, borrar los
mosaicos almacenados, descargar todos los logs en un único ZIP (el destino se
elige antes con el selector de documentos de Android) y borrar los logs
`map-debug-*.log`. También modifican el límite de caché hasta 5 GB; al
reducirlo, la poda se ejecuta con la siguiente composición del mapa. Los
botones muestran la ocupación actual de la caché vectorial y de los logs, y
ambas limpiezas informan del número de elementos eliminados. Los logs se
conservan en el almacenamiento interno hasta que se descargan o se limpian.

### Inicialización endurecida de MapLibre

1. `MapView.onCreate()` se ejecuta antes de solicitar el mapa y el resto de su
   ciclo de vida se sincroniza desde el observador de Android.
2. Si `getMapAsync()` no responde en 8 segundos se muestra y registra
   `ERROR INICIALIZACIÓN`.
3. Con red validada, el estilo se descarga mediante HTTPS controlado por el
   launcher, con timeout, código HTTP y validación mínima de contenido.
4. El JSON válido se sustituye atómicamente en `filesDir/map-styles`; una
   descarga incompleta o inválida nunca reemplaza la última copia correcta.
5. Sin red, o si falla una actualización, se entrega inmediatamente a MapLibre
   el último JSON persistido mediante `Style.Builder.fromJson`.
6. Sólo cuando aún no existe ninguna copia local se conserva la URL remota como
   último recurso, permitiendo que MapLibre se recupere al volver la conexión.
7. Los tiles, TileJSON, fuentes y sprites continúan perteneciendo a la caché
   ambiental de MapLibre; no se descarga una región offline.

El diagnóstico informa el estado del mapa (cargando, cargado, error o error de
inicialización) a partir del estado real de MapLibre.

## Diagnóstico en fichero

Al activar el debug se genera un fichero nuevo que registra, sin alterar el
comportamiento del mapa:

* transporte y capacidades reales de la red activa;
* resolución DNS y una descarga HTTP de control del estilo OpenFreeMap;
* código, tipo, bytes, firma, TLS y tiempos de la respuesta;
* ruta, ocupación, espacio libre y prueba de lectura/escritura de la caché;
* creación, primer layout, `resume` y liberación del `MapView`;
* creación, carga del estilo, renderizado completo y errores de MapLibre;
* viewport, zoom, orientación y centro en cada actualización aceptada;
* muestras de ubicación aceptadas y rechazadas, junto con el motivo.

Las escrituras se serializan para que las descargas concurrentes no mezclen ni
pisen líneas.

## Criterios de validación en el coche

* 60 fps estables en las animaciones principales o, como mínimo, ausencia de
  tirones perceptibles en velocidad y RPM.
* primer mapa visible en menos de 3 segundos con conexión;
* posición y rumbo coherentes durante un viaje;
* consumo de datos y caché medidos;
* legibilidad desde la posición del conductor;
* retorno inmediato al fondo normal cuando no hay red, GPS o permiso.

## Continuidad de cámara durante giros

La cámara se actualiza como máximo a 30 fps a partir de las muestras GPS. Entre
muestras puede proyectar el movimiento durante un segundo como máximo, pero la
llegada de una nueva posición nunca debe rebobinar la cámara hasta el tramo de
interpolación retrasado.

El suavizador conserva el último frame realmente mostrado y, cuando una nueva
muestra modifica la trayectoria, absorbe durante 750 ms la diferencia entre la
predicción anterior y el nuevo recorrido. Posición, velocidad y rumbo se
corrigen con la misma curva suave; el rumbo usa siempre el recorrido angular
más corto. Esto evita el salto lateral que una predicción recta producía al
entrar en una curva, manteniendo acotada la predicción cuando deja de llegar
señal GPS.
