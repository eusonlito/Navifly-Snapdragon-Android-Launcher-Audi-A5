# Mapa integrado

> El comportamiento objetivo de la siguiente versión está definido en
> `MAP.md`. Este documento conserva la investigación y el historial técnico de
> los prototipos existentes.

## Conclusión

El mapa en vivo entre las esferas de velocidad y RPM utiliza:

* **Renderizador:** MapLibre Native 13.4.1 alojado en un `AndroidView`, con
  renderizado GPU de geometría vectorial mediante `TextureView`.
* **Cartografía:** datos de OpenStreetMap mediante OpenFreeMap/OpenMapTiles.
* **Estilo:** seleccionable entre Positron, Liberty y Bright;
  las tres opciones son claras y no cambian con el tema oscuro del launcher.
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

La composición resultante se validó a 2400x896 en
`map-textureview-full-width-2400x896.png`. El mapa conserva geometría y texto
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

El mapa implementado ocupa todo el ancho y alto del área central entre la
cabecera y el pie. Es una superficie rectangular situada por debajo de los dos relojes.
Cada esfera incorpora detrás un fondo circular un 6 % mayor, concéntrico con el
reloj y con el mismo degradado radial que su interior. No existe desplazamiento
horizontal del círculo: su centro coincide exactamente con el centro de la
velocidad o las RPM. La ampliación protege las cifras y genera los dos límites
curvos convexos sin introducir discos negros ajenos al acabado del reloj.

La interacción debe ser mínima para evitar distracciones:

* sin controles de zoom ni brújula propios;
* zoom mediante pellizco;
* marcador fijo al 50 % horizontal y 80 % vertical;
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
* Un gesto de pellizco permite zoom fraccionario continuo entre 12 y 18. El
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
* Un quinto icono de la cabecera abre los ajustes propios del launcher. Desde
  ellos se puede activar el diagnóstico del mapa y consultar estado de carga,
  red, GPS, coordenadas, proveedor, límite de caché y último error. La pantalla
  usa tres paneles uniformes sin scroll ni encabezados internos; el diagnóstico
  es una opción convencional y todos los tamaños de caché aparecen en una sola
  línea. El nombre del fichero de debug sólo aparece cuando ya existe.
* Al activar el diagnóstico se crea internamente
  `map-debug-AAAAmmdd-HHMMSS.log`. Registra cambios de red,
  GPS, fase de inicialización, descarga del estilo y errores sin guardar eventos
  CAN ni una línea por cada frame.
* El APK incluye `libmaplibre.so` para las ABI soportadas, pero no incorpora
  JavaScript ni WebView. El Navifly utiliza la variante ARM64.

## Estado de la validación

### Composición a ancho completo del 29 de julio de 2026

La superficie vectorial se validó en el emulador Navifly 2400x896 ocupando todo
el área central, por debajo de las dos esferas opacas. El mapa cargó estilo,
glyphs y teselas sin costuras; el proceso permaneció estable y no se registraron
errores fatales de Android o MapLibre. La captura
`captura local full-width-vector-map-audi-rings-2400x896.png` conserva la evidencia
visual. El APK ARM64 optimizado superó tests, Lint release y R8.

### Migración vectorial del 29 de julio de 2026

MapLibre se reintrodujo con un ciclo de vida explícito, inicialización
diferida, límite de caché aplicado antes del estilo y timeout de ocho segundos
para `getMapAsync`. OpenFreeMap Positron cargó correctamente en el emulador
2400x896: estilo, glyphs y teselas MVT respondieron por HTTPS, el mapa se
renderizó sin costuras y el proceso permaneció estable. El padding de cámara se
aplica una sola vez por tamaño para que recomposiciones Compose no cancelen
precargas.

Esta validación no sustituye la prueba en el Navifly. Deben verificarse allí el
driver OpenGL, el arranque como `HOME`, varias suspensiones/reanudaciones y un
viaje prolongado antes de considerar cerrada la migración.

### Reinicio del 27 de julio de 2026

La instrumentación inicial introdujo una incompatibilidad Kotlin/Java en
`DiagnosticMapTileProvider`: declaró como no nulo el `Drawable` de
`mapTileRequestCompleted`, aunque osmdroid puede invocar ese callback con
`null` mientras el descargador encadena sus proveedores. La comprobación de
Kotlin lanzaba una `NullPointerException` en el hilo `downloader`; al ser la
aplicación `HOME`, Android volvía a iniciarla y producía el bucle observado.

El callback acepta y registra ahora explícitamente el valor nulo. La propiedad
del `MapView` también se mantiene en una sesión estable no observable por
Compose y sólo se libera cuando esa sesión abandona realmente la composición.
El logger es único por proceso y los registros identifican PID, actividad,
composición y sesión de mapa.

La corrección se reprodujo y validó en el emulador: mismo PID durante más de
35 segundos, cero excepciones del descargador, tiles CARTO visibles y estado
`MAPA CARGADO`.

Las capturas reales `captura local 2026-07-25 17.26.07.png`,
`captura local 2026-07-25 20.08.38.png` y `captura local 2026-07-25 22.42.09.png`
mostraron únicamente la superficie beige inicial de MapLibre y el marcador
Compose. El registro `registro local map-debug-20260725-211037.log.txt` confirma red y
GPS activos y dos esperas agotadas en `getMapAsync()`: el bloqueo sucede antes
de descargar o aplicar el estilo.

La inicialización activa se ha endurecido de la siguiente forma:

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

La prueba del 26 de julio con MapLibre 13.0.2 y el arranque del renderizador
diferido hasta `View.post` provocó un reinicio nativo continuo del proceso en el
Navifly. El registro repite una inicialización completa cada 0,8–1 segundo y se
corta siempre en `ESPERANDO RENDERIZADOR`, sin excepción Java. Se ha restaurado
11.11.0 y el ciclo de vida estable para impedir que una incompatibilidad del
renderizador bloquee el launcher principal. Esa versión nueva no debe
reintroducirse sin aislarla primero del proceso `HOME`.

La prueba posterior con MapLibre GL JS tampoco llegó a abrir el HTML local en
el WebView del firmware: el registro permaneció en `CARGANDO HTML`, Android
contabilizó cero tráfico y la superficie del WebView tapó la cabecera Compose.
Por ello también se descartó esa integración.

El prototipo posterior descargaba directamente PNG `@2x` y aplicaba una matriz
Compose a cada tile. La prueba real mostró costuras, traslaciones incorrectas al
girar y estados de carga falsos, por lo que fue retirado. La solución activa
en aquel momento delegaba cámara y pirámide raster en osmdroid y utilizaba PNG
de 256 px de CARTO.
El diagnóstico informa `MAPA ACTIVO` cuando el `MapView` está creado y
`ERROR MAPA` si el contenedor no puede construirlo; no afirma que todos los
tiles de red estén descargados. Al no existir `SurfaceView`, `GLSurfaceView` ni
WebView, el mapa permanece dentro de los límites de su vista Android.

Cada transición del ajuste `Debug del mapa` de desactivado a activado crea un
fichero nuevo con milisegundos en el nombre. Nunca reutiliza el URI de una
captura anterior. La ruta del nuevo fichero se propaga inmediatamente al panel
de ajustes, sin depender de que después cambie el GPS, la red o el mapa.

Los ajustes permiten cambiar el estilo con efecto inmediato, borrar los
mosaicos almacenados, descargar todos los logs en un único ZIP eligiendo antes
el destino con el selector de documentos de Android y borrar todos los logs
`map-debug-*.log` creados por la
aplicación. También permiten modificar el límite de caché hasta 5 GB; al
reducirlo, la poda se ejecuta inmediatamente con la siguiente composición del
mapa. Los propios botones muestran la ocupación actual de la caché vectorial y
de los logs con unidades legibles, y se actualizan al abrir los ajustes y tras
cada descarga o limpieza. Los ficheros permanecen en el almacenamiento interno
hasta que se descargan o se limpian. Los logs antiguos que pudieran existir en
`Descargas/A5Cockpit` quedan fuera de esta gestión y pueden descartarse. Ambas
limpiezas informan del número de elementos eliminados.

La aplicación y el APK compilan correctamente sin código nativo específico de
ARM64 o x86_64. El encaje visual final y el rendimiento quedan pendientes de
validar en el Navifly real.

Los scripts de compilación conservan sus artefactos en el directorio `out` de cada proyecto.

## Opciones descartadas inicialmente

* **Google Maps SDK:** integración Compose madura y compatible con el dispositivo,
  pero exige proyecto Google Cloud, clave API y facturación habilitada. No aporta
  una ventaja necesaria para una vista auxiliar.
* **Servidor raster público de OpenStreetMap:** los datos OSM son libres, pero sus
  servidores públicos no permiten descarga masiva ni mapas offline y no ofrecen
  garantía de servicio.
* **Mapa offline completo desde el primer prototipo:** MapLibre soporta regiones
  offline, pero hay que usar una fuente que autorice la descarga o preparar
  mosaicos propios. Se evaluará después de validar rendimiento y legibilidad.

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
