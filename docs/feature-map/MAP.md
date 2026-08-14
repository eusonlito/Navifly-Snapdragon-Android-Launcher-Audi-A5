# Especificación del mapa de conducción

## 1. Objetivo y alcance

El mapa debe comportarse como la vista de seguimiento de una aplicación de
mapas moderna: movimiento continuo, posición estable, orientación según la
marcha, zoom fluido, ausencia de huecos durante las transiciones y degradación
predecible cuando se pierde GPS o red.

Esta fase define la experiencia y la arquitectura antes de implementarla. No
incluye cálculo de rutas, instrucciones giro a giro, búsqueda de destinos,
tráfico ni integración con una ruta activa de Waze. Esas funciones requerirían
servicios y fuentes de datos adicionales.

## Estado de implementación

El renderer Compose construido inicialmente para esta especificación se retiró
el 26 de julio de 2026 después de la prueba real: mostraba costuras entre
bitmaps, podía desplazar la cámara al rotar y declaraba el mapa cargado con una
cobertura incompleta.

La implementación activa utiliza MapLibre Native 13.4.1 dentro de
`AndroidView`, con estilos y teselas vectoriales de OpenFreeMap. El marcador y
el resto del cockpit continúan siendo Compose. El renderer se crea 250 ms
después de los primeros frames, configura la caché antes del estilo y dispone
de timeout explícito para `getMapAsync`; un fallo deja operativo el launcher.
La actualización forma parte de la migración coordinada del toolchain Android
y requiere validación adicional en el Navifly real.

El núcleo de seguimiento activo incluye:

* anclaje fijo `(50 %, 80 %)`;
* animación conjunta de posición y rumbo mediante la cámara de MapLibre;
* rotación `heading-up`, con rumbo GPS suavizado y congelado a baja velocidad;
* zoom fraccionario persistido por estilo;
* renderizado GPU de teselas vectoriales sin costuras entre bitmaps;
* caché ambiental de MapLibre con el límite seleccionado en ajustes;
* preferencia por GPS y uso de ubicación de red sólo cuando el GPS lleva al
  menos 15 segundos sin entregar una muestra aceptada;
* validación de precisión y rechazo de saltos GPS imposibles;
* contenedor tolerante a fallos: un error al crear el mapa deja operativo el
  launcher y se publica como `ERROR MAPA`.
* exploración manual con arrastre: pausa únicamente el seguimiento de cámara,
  oculta el marcador fijo para no representar una posición falsa y presenta
  un botón de recentrado; GPS, POI, telemetría y teselas siguen activos.

Quedan como evoluciones posteriores la fusión con sensores de orientación a
velocidad muy baja y las métricas avanzadas de FPS/latencia. La posición,
sentido de giro y rendimiento deben validarse específicamente en el Navifly.

## 2. Composición visual

### 2.1 Punto de anclaje

* El marcador del vehículo permanece fijo.
* Su centro horizontal está en el 50 % del área visible del mapa.
* Su centro vertical está en el 80 % del área visible: deja un margen inferior
  equivalente al 20 % de la altura.
* El mapa se desplaza y rota por debajo del marcador. El marcador nunca salta,
  deriva ni se desplaza para representar el avance.
* El anclaje deja aproximadamente el 80 % del mapa por delante del vehículo,
  priorizando la carretera que se aproxima.
* Los fondos de las esferas siguen en primer plano y delimitan los laterales.
  El mapa no puede dibujar sobre cabecera, cifras, testigos ni barra inferior.

### 2.2 Marcador y estados

* Cian: red y posición GPS válidas.
* Amarillo: no existe una posición GPS reciente; se conserva la última conocida.
* Rojo: no hay conexión de red. El rojo tiene prioridad si también falta GPS.
* El rumbo visual del marcador apunta siempre hacia la parte superior. Es el
  mapa el que gira, no el icono.
* El icono no usa halo, círculo ni sombra. Un contorno claro integrado mantiene
  el contraste sin ocultar nombres de carreteras relevantes.

### 2.3 Atribución

La atribución del proveedor y de OpenStreetMap debe permanecer visible, legible
y fuera de las zonas tapadas por las esferas.

## 3. Cámara y modos de seguimiento

### 3.1 Modo seguimiento

Es el modo normal:

* anclaje fijo en `(50 %, 80 %)`;
* orientación `heading-up`;
* cámara actualizada con cada posición aceptada;
* transiciones continuas y basadas en tiempo, no en el número de eventos GPS;
* retorno gradual, nunca mediante salto, después de recuperar una señal.

### 3.2 Modo exploración

Para ofrecer un comportamiento comparable al de otros mapas:

* un arrastre con un dedo desacopla temporalmente la cámara del vehículo;
* el marcador fijo se oculta durante la exploración para no sugerir que el
  centro del mapa sigue siendo la posición real;
* aparece un botón grande para volver a centrar;
* el seguimiento sólo se recupera mediante una acción explícita sobre ese
  botón, nunca por un temporizador mientras el usuario consulta otra zona;
* el pellizco continúa modificando el zoom y conserva el nivel seleccionado.

### 3.3 Orientación

* Con el vehículo en movimiento se usa preferentemente el `bearing` del GPS.
* A baja velocidad, donde el rumbo GPS deja de ser fiable, se combina con el
  sensor de orientación/rotación de Android si el dispositivo proporciona datos
  válidos.
* Si no existe ninguna fuente fiable se congela el último rumbo válido; no se
  vuelve bruscamente al norte.
* Los ángulos se interpolan por el camino más corto: la transición entre 359° y
  1° debe recorrer 2°, no 358°.
* Deben rechazarse cambios incompatibles con la velocidad y precisión
  declaradas por el sensor.
* Se recomienda permitir en ajustes `Dirección arriba` y `Norte arriba`, usando
  `Dirección arriba` por defecto.

## 4. Posición y suavizado

La posición mostrada no debe copiar directamente cada muestra:

1. validar antigüedad, precisión horizontal y velocidad de la muestra;
2. descartar coordenadas imposibles o saltos que exigirían una aceleración
   incompatible con el vehículo;
3. filtrar la posición mediante un filtro alfa-beta o Kalman ligero;
4. predecir unos pocos cientos de milisegundos hacia delante para compensar la
   cadencia y latencia del GPS;
5. interpolar entre el estado visual actual y el nuevo estado usando el tiempo
   real de la muestra;
6. corregir gradualmente el error cuando vuelve el GPS.

No se debe inventar desplazamiento indefinidamente. Al superar el horizonte
corto de predicción, la cámara se detiene en la última posición y el marcador
pasa a amarillo.

## 5. Zoom

### 5.1 Zoom continuo

* El gesto de pellizco modifica un zoom fraccionario y continuo.
* El rango inicial recomendado es `12.0–18.0`.
* El nivel no debe saltar únicamente entre enteros.
* Durante el gesto se escala inmediatamente el contenido ya renderizado.
* Al finalizar, se solicitan los tiles del nivel óptimo y se sustituyen mediante
  una transición corta cuando están listos.
* Nunca se muestra el fondo vacío mientras llegan tiles de mayor resolución.
* Si faltan tiles del nivel solicitado se mantiene ampliado el nivel anterior,
  aunque se vea temporalmente menos nítido.
* Los tiles antiguos sólo se liberan después de disponer de cobertura suficiente
  del nuevo viewport.

### 5.2 Persistencia

* El zoom elegido se guarda después de un breve `debounce`, no en cada frame.
* Se restaura al iniciar una sesión nueva.
* El valor se guarda por estilo si las escalas útiles de los proveedores
  difieren.
* `Restablecer zoom` devuelve al valor inicial recomendado.
* El ajuste automático por velocidad debe ser opcional. Si está activo, aplica
  un desplazamiento suave respecto al zoom guardado; nunca sobrescribe la
  preferencia manual.

## 6. Renderizado vectorial

MapLibre Native 13.4.1 gestiona la pirámide de teselas MVT, texturas, cámara,
cancelación de solicitudes y composición GPU. El launcher no descarga ni
ensambla bitmaps por su cuenta. Esto evita costuras, desplazamientos entre
teselas y estados de cobertura incorrectos observados en el prototipo raster.

### 6.1 Prioridades de carga

La cobertura y las prioridades internas pertenecen a MapLibre. Desde el
launcher sólo se exige:

1. configurar la caché antes de cargar el estilo;
2. no recrear el estilo ni la vista por recomposiciones Compose;
3. no reaplicar padding o cámara si su valor efectivo no ha cambiado;
4. pausar y reanudar el `MapView` con el ciclo de vida;
5. mantener el mapa fuera de la ruta crítica de arranque.

## 7. Precarga alrededor de la posición

Esta función forma parte del conjunto de trabajo normal del mapa. No descarga
una ruta, una ciudad ni un área para uso offline: conserva los tiles visibles y
solicita un margen pequeño alrededor de la posición actual para que el siguiente
movimiento de cámara no descubra huecos.

La cobertura próxima la calcula MapLibre a partir del viewport y de la cámara.
No existe un descargador de rutas ni un barrido propio de áreas. El launcher
evita cambios redundantes de cámara para que las precargas internas no se
cancelen continuamente y detiene el trabajo cuando el mapa deja de estar
visible.

## 8. Puntos de Interés

La capa informativa se define en [POI.md](../feature-poi/POI.md). Combina las
fuentes GeoJSON y los iconos PNG que el usuario importa desde Ajustes del
Launcher. Los datos se cargan de forma asíncrona desde el almacenamiento privado
y no condicionan la disponibilidad del mapa base, el seguimiento de cámara ni
la precarga de tiles.

### Apariencia clara y oscura

El launcher ofrece tres políticas persistentes en Ajustes:

* **Automático:** sigue las luces del coche mediante
  `KSW_DATA_SMALL_LIGHT_ON`. Si el coche ya arranca con las luces encendidas,
  activa el estilo oscuro inmediatamente. Durante la marcha exige que las luces
  permanezcan encendidas durante 60 segundos para evitar cambios breves en
  túneles; al apagarlas vuelve al estilo claro sin demora. Si el proveedor del
  fabricante no está disponible, usa como respaldo el modo nocturno comunicado
  por Android.
* **Claro:** conserva el estilo claro seleccionado (Positron, Liberty o Bright)
  aunque Android active el modo nocturno.
* **Oscuro:** parte del estilo Dark de OpenFreeMap y aplica una paleta nocturna
  propia de alto contraste. El fondo permanece antracita, mientras carreteras,
  edificios, agua y etiquetas mantienen niveles de luminosidad diferenciados
  para resultar legibles en la pantalla del vehículo sin deslumbrar.

El cambio de apariencia recrea exclusivamente la sesión del renderer y conserva
el zoom asociado al estilo claro seleccionado. No bloquea el resto del cockpit.

La implementación observa el ciclo de vida de la actividad: al pasar a segundo
plano pausa `MapView`, cancela el heartbeat y retira listeners de GPS y red. Al
volver a primer plano reanuda la misma sesión y conserva zoom y posición.

La caché operativa no debe confundirse con una función offline ni utilizarse
para realizar descargas masivas o barridos automatizados. Las condiciones y la
atribución de OpenFreeMap, OpenMapTiles y OpenStreetMap siguen siendo
aplicables. Proveedor, URL, atribución, identificación y política HTTP deben
permanecer desacoplados del comportamiento de la cámara.

## 9. Caché

### 9.1 Política

* Límite configurable: 512 MB, 1 GB, 2 GB o 5 GB.
* La suma de todos los estilos cuenta contra un único límite.
* El almacenamiento, la revalidación HTTP y la eliminación pertenecen a
  MapLibre; el launcher configura el límite de su caché ambiental.
* El JSON de cada estilo se conserva separadamente en `filesDir/map-styles` y
  se carga desde ahí al arrancar sin red. Sin esta pieza, los tiles vectoriales
  cacheados no pueden reconstruirse después de reiniciar el proceso.
* Botón de borrado con resultado visible.
* Al reducir el límite se ejecuta una poda inmediata en segundo plano.
* Debe reservarse espacio libre para Android. El límite efectivo será el menor
  entre la preferencia y el espacio disponible menos una reserva de seguridad.
  Elegir 5 GB no garantiza que Android pueda asignarlos.
* `cacheDir` puede ser eliminado por Android. Si se requiere disponibilidad
  offline garantizada habrá que usar almacenamiento persistente y una fuente
  que autorice mapas offline.

## 10. Red y recuperación de errores

* Timeout separado de conexión y lectura.
* Reintentos limitados con espera exponencial y jitter.
* No reintentar inmediatamente errores HTTP permanentes.
* Mantener tiles de caché aunque estén caducados cuando no existe red,
  marcándolos internamente como obsoletos.
* No borrar un frame válido por el fallo de un tile.
* Cambiar de red móvil a Wi-Fi sin reiniciar el mapa.
* Registrar proveedor, URL anonimizada, código HTTP, latencia, bytes, aciertos de
  memoria/disco/red y motivo de cancelación cuando el debug está activo.
* No registrar un evento por frame ni un historial continuo de coordenadas salvo
  que el usuario lo solicite expresamente.

## 11. Ciclo de vida y seguridad

* Pausar animaciones y peticiones no esenciales cuando la aplicación deja de
  estar visible.
* Liberar bitmaps fuera del viewport sin bloquear el hilo principal.
* Recuperar el estado de cámara tras recreación de actividad.
* El fallo del mapa nunca debe reiniciar el launcher `HOME`.
* Evitar `SurfaceView`, WebView o renderizadores nativos no validados que puedan
  cubrir la interfaz o derribar el proceso.
* Si el motor falla, mostrar el último frame o el fondo del cockpit y mantener
  operativas velocidad, RPM, testigos y botones.

## 12. Ajustes propuestos

* Estilo claro: Positron, Voyager o sin etiquetas.
* Orientación: dirección arriba / norte arriba.
* Zoom automático por velocidad: activado/desactivado.
* Restablecer zoom.
* Límite de caché: 512 MB–5 GB.
* Borrar caché.
* Debug del mapa.
* Borrar logs.

La pantalla debe conservar la cabecera fija, paneles desplazables, estado
seleccionado inequívoco y áreas táctiles adecuadas para el dispositivo.

## 13. Métricas y diagnóstico

El diagnóstico debe exponer:

* posición recibida, posición filtrada y precisión;
* fuente y antigüedad del rumbo;
* zoom solicitado, zoom visual y nivel de tiles activo;
* tiles visibles: listos, en caché, descargando y fallidos;
* cola y descargas simultáneas;
* aciertos de memoria, disco y red;
* tamaño real y límite de caché;
* proveedor y estado de la caché anticipada;
* latencia media y último error;
* FPS y tiempo máximo de frame.

Cada activación crea un fichero nuevo y muestra inmediatamente su nombre.

## 14. Criterios de aceptación

* El marcador permanece exactamente en `(50 %, 80 %)` durante seguimiento.
* No existe salto visible con muestras GPS normales de 1 Hz.
* La cámara rota por el camino angular más corto y no oscila con el coche
  detenido.
* El pellizco responde en el mismo frame y conserva el mapa anterior hasta tener
  tiles nítidos.
* No aparece fondo vacío durante cambio de zoom, giro o pérdida de red.
* El zoom se recupera después de cerrar y abrir la aplicación.
* La caché anticipada no retrasa tiles visibles ni excede sus presupuestos.
* El mapa continúa siendo útil con tiles cacheados al perder la conexión.
* Cabecera, esferas y barra inferior nunca quedan cubiertas.
* Reducir o borrar la caché no bloquea la interfaz.
* Un error del proveedor no afecta a la telemetría ni reinicia el launcher.
* La atribución es visible en todos los estilos.

## 15. Decisiones pendientes antes de implementar

1. Confirmar las condiciones generales de uso y atribución del proveedor
   seleccionado.
2. Confirmar si la aplicación es estrictamente personal/no comercial y si
   cumple las condiciones del proveedor.
3. Decidir si el zoom automático por velocidad permanece activo.
4. Verificar qué sensores de orientación expone realmente el Navifly y su
   calibración respecto al eje del vehículo.
5. Medir espacio libre real y fijar la reserva mínima de almacenamiento.
6. Definir objetivos de rendimiento realistas: ideal 60 FPS, mínimo aceptable
   30 FPS sin pausas perceptibles.
