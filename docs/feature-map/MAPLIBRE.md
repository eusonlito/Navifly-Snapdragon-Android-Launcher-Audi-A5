# MapLibre Native en A5 Cockpit

## Decisión aplicada

Desde el 29 de julio de 2026 el launcher utiliza MapLibre Native Android
13.4.1 con los estilos vectoriales Positron, Liberty y Bright de OpenFreeMap.
La versión se ha coordinado con AGP 9.3.1, Gradle 9.7.0, Kotlin integrado
2.3.10 y Compose BOM 2026.06.01.

El renderer se adjunta después de los primeros frames del cockpit, configura la
caché ambiental antes de aplicar el estilo y publica timeouts y errores sin
bloquear la interfaz principal. GPS, marcador, estados de red, rumbo filtrado y
zoom persistente siguen siendo responsabilidad del launcher.

La primera validación en el emulador Navifly 2400x896 cargó correctamente
teselas MVT, glyphs y el estilo Positron. La validación definitiva del driver
OpenGL, arranque como aplicación HOME y estabilidad prolongada corresponde al
dispositivo real.

No estás limitado a OsmAnd. **Para hacer una aplicación Android que muestre un mapa y siga la posición del dispositivo, la opción abierta más parecida al sistema visual de Waze es MapLibre Native para Android.**

La arquitectura sería:

```text
Teselas vectoriales + style.json
             ↓
       MapLibre Native
             ↓
   renderizado del mapa por GPU
             +
 ubicación, rumbo y precisión
             ↓
 marcador + cámara de seguimiento
```

No necesitas servidor de rutas, grafo vial ni motor de navegación.

## Opción recomendada: MapLibre Native

MapLibre:

* renderiza teselas vectoriales;
* permite cambiar completamente el estilo del mapa;
* puede mostrar un marcador de ubicación integrado;
* puede hacer que la cámara siga al usuario;
* permite rotar el mapa según GPS o brújula;
* admite inclinación, zoom, animaciones y mapas offline;
* es software libre y no obliga a utilizar mapas de un proveedor concreto. ([MapLibre][1])

El mapa visible necesita dos recursos:

1. **Teselas vectoriales**, normalmente archivos MVT.
2. **Un `style.json`**, que especifica colores, grosores, fuentes, carreteras, edificios y etiquetas.

Puedes obtenerlos de un proveedor de mapas basado en OpenStreetMap o alojarlos tú mismo con OpenMapTiles. OpenMapTiles está diseñado precisamente para alimentar MapLibre con mapas vectoriales online u offline. ([OpenMapTiles][2])

## Cómo conseguir el comportamiento visual de Waze

Necesitas configurar cuatro elementos.

### 1. Marcador de posición

MapLibre incluye un `LocationComponent`, que dibuja:

* la posición;
* el círculo de precisión;
* una flecha de orientación;
* opcionalmente una animación pulsante.

El icono puede ser reemplazado por tu propia flecha o indicador. ([MapLibre][3])

### 2. Cámara de seguimiento

Estos son los modos más relevantes:

* `TRACKING`: sigue la posición, sin rotar necesariamente.
* `TRACKING_COMPASS`: sigue la posición y gira con la brújula.
* `TRACKING_GPS`: sigue la posición y gira según el rumbo proporcionado por las actualizaciones GPS.
* `TRACKING_GPS_NORTH`: sigue al usuario, pero mantiene el norte arriba. ([MapLibre][4])

Para caminar, `TRACKING_COMPASS` suele producir una orientación más inmediata. Para bicicleta o automóvil, `TRACKING_GPS` suele ser más estable una vez que el dispositivo está en movimiento.

### 3. Perspectiva

Una apariencia similar a Waze se obtiene normalmente con:

```text
zoom:        16–18
inclinación: 35–50 grados
rumbo:       orientación del dispositivo o movimiento
marcador:    ligeramente por debajo del centro
```

MapLibre proporciona métodos específicos para cambiar el zoom, la inclinación y el espacio alrededor del usuario mientras la cámara está en modo de seguimiento. ([MapLibre][5])

### 4. Actualizaciones de ubicación

Para movimiento continuo necesitas actualizaciones periódicas, no solamente `lastLocation`.

Android ofrece el Fused Location Provider, que combina GPS, Wi-Fi y otras fuentes, y puede entregar latitud, longitud, rumbo, velocidad y precisión. La frecuencia y precisión influyen directamente en el consumo de batería. ([Android Developers][6])

MapLibre también puede utilizar su motor de ubicación integrado, por lo que para una primera versión ni siquiera necesitas conectar manualmente `FusedLocationProviderClient`. ([MapLibre][3])

## Implementación mínima

### Dependencia

```kotlin
dependencies {
    implementation("org.maplibre.gl:android-sdk:<version-actual>")
}
```

La documentación oficial recomienda obtener la versión vigente y cargar el SDK desde Maven Central. ([MapLibre][1])

### Permisos

```xml
<uses-permission
    android:name="android.permission.ACCESS_COARSE_LOCATION" />

<uses-permission
    android:name="android.permission.ACCESS_FINE_LOCATION" />
```

No necesitas permiso de ubicación en segundo plano si solo sigues al usuario mientras la pantalla de la aplicación está abierta. Los permisos deben solicitarse también en tiempo de ejecución. ([MapLibre][7])

### Vista del mapa

```xml
<org.maplibre.android.maps.MapView
    android:id="@+id/mapView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Activar seguimiento

Código simplificado:

```kotlin
@SuppressLint("MissingPermission")
private fun enableUserTracking(
    map: MapLibreMap,
    style: Style
) {
    val visualOptions =
        LocationComponentOptions.builder(this)
            .pulseEnabled(true)
            .trackingGesturesManagement(true)
            .build()

    val locationRequest =
        LocationEngineRequest.Builder(1_000L)
            .setFastestInterval(500L)
            .setPriority(
                LocationEngineRequest.PRIORITY_HIGH_ACCURACY
            )
            .build()

    val activationOptions =
        LocationComponentActivationOptions
            .builder(this, style)
            .locationComponentOptions(visualOptions)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(locationRequest)
            .build()

    map.locationComponent.apply {
        activateLocationComponent(activationOptions)
        isLocationComponentEnabled = true

        // Flecha orientada según el movimiento GPS.
        renderMode = RenderMode.GPS

        // El mapa sigue la posición y gira con el movimiento.
        cameraMode = CameraMode.TRACKING_GPS

        // Apariencia de navegación.
        zoomWhileTracking(17.0)
        tiltWhileTracking(45.0)
    }
}
```

Después se activa cuando termina de cargarse el estilo:

```kotlin
MapLibre.getInstance(this)

mapView.getMapAsync { map ->
    map.setStyle(MAP_STYLE_URL) { style ->
        enableUserTracking(map, style)
    }
}
```

El ejemplo oficial utiliza esta misma estructura: cargar el estilo, activar `LocationComponent`, habilitarlo y seleccionar el modo de cámara. ([MapLibre][7])

Para una aplicación de caminata cambiaría estas dos líneas:

```kotlin
renderMode = RenderMode.COMPASS
cameraMode = CameraMode.TRACKING_COMPASS
```

## De dónde sacar el mapa

MapLibre es el renderizador; no proporciona por sí mismo todo el mapa mundial. Tienes tres posibilidades:

### Proveedor externo

La opción más sencilla:

```text
Android → MapLibre → proveedor de style.json y teselas
```

El proveedor mantiene los mapas y te entrega una URL de estilo. Normalmente requiere una clave y puede cobrar según el tráfico.

### OpenMapTiles propio

Más control:

```text
OpenStreetMap
    ↓
OpenMapTiles
    ↓
servidor de teselas
    ↓
MapLibre Android
```

Puedes modificar el estilo, eliminar puntos de interés, destacar caminos o utilizar tus propios datos. OpenMapTiles también permite empaquetar mapas regionales para uso offline. ([OpenMapTiles][2])

### Mapas completamente offline

Para offline tienes dos alternativas principales:

* **MapLibre + paquetes de teselas vectoriales**, con apariencia moderna y flexible.
* **Mapsforge**, que utiliza archivos vectoriales `.map` locales y está orientado específicamente a Android y Java. ([GitHub][8])

Mapsforge es práctico para aplicaciones offline, pero MapLibre permite conseguir más fácilmente una estética y animación parecidas a Waze.

## Lo que no deberías hacer

No conviene usar directamente `tile.openstreetmap.org` como backend de producción ni descargar sus teselas para uso offline. Los datos de OpenStreetMap son reutilizables, pero los servidores públicos de teselas tienen capacidad limitada, no ofrecen SLA y prohíben la descarga masiva para offline. Debes usar un proveedor, teselas vectoriales permitidas o infraestructura propia, manteniendo la atribución correspondiente. ([operations.osmfoundation.org][9])

## Elección concreta

Para tu caso:

```text
MapLibre Native Android
+ teselas vectoriales de OpenStreetMap
+ LocationComponent
+ TRACKING_COMPASS al caminar
+ zoom 17
+ inclinación 40–45°
+ marcador personalizado
```

Eso proporciona esencialmente la parte visual de Waze: mapa vectorial fluido, posición actual, dirección, rotación y seguimiento, sin ninguna funcionalidad de cálculo de rutas.

[1]: https://maplibre.org/maplibre-native/android/examples/getting-started/?utm_source=chatgpt.com "Quickstart - MapLibre Android Examples"
[2]: https://openmaptiles.org/docs/mobile/mobile/?utm_source=chatgpt.com "Create a mobile app – OpenMapTiles"
[3]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.location/-location-component/index.html?utm_source=chatgpt.com "LocationComponent"
[4]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.location/-location-component/set-camera-mode.html?utm_source=chatgpt.com "setCameraMode"
[5]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.location/-location-component/zoom-while-tracking.html?utm_source=chatgpt.com "zoomWhileTracking"
[6]: https://developer.android.com/develop/sensors-and-location/location/request-updates?utm_source=chatgpt.com "Request location updates | Sensors and location | Android Developers"
[7]: https://maplibre.org/maplibre-native/android/examples/location-component/?utm_source=chatgpt.com "LocationComponent - MapLibre Android Examples"
[8]: https://github.com/mapsforge/mapsforge?utm_source=chatgpt.com "GitHub - mapsforge/mapsforge: Vector map library and writer - running on Android and Desktop. · GitHub"
[9]: https://operations.osmfoundation.org/policies/tiles/?utm_source=chatgpt.com "Tile Usage Policy"
