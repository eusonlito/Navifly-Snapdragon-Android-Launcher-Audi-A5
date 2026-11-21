# Arquitectura del Sistema - Audi A5 Launcher

Este documento detalla las directrices arquitectónicas, los patrones de diseño y el flujo de datos del Launcher (A5 Cockpit) para la pantalla ultra ancha del Audi A5. El paquete raíz de la aplicación es `com.lito.a5launcher`.

---

## 1. Patrón Arquitectónico (MVVM Reactivo)

La aplicación implementa una arquitectura **MVVM (Model-View-ViewModel)** reactiva impulsada por **Kotlin Coroutines** y **Jetpack Compose**. Esto garantiza un desacoplamiento limpio entre la obtención física de datos (MCU/CAN) y el renderizado gráfico.

```
       [ Bus CAN / Vehículo ]
                 │
                 ▼ (Servicio IPC Binder)
       ┌────────────────────────┐
       │   TelemetryService     │ (Capa de Datos - Background)
       └────────────────────────┘
                 │
                 ▼ (StateFlow / Binder)
       ┌────────────────────────┐
       │   LauncherViewModel    │ (Capa de Negocio / Estado)
       └────────────────────────┘
                 │
                 ▼ (Compose State)
       ┌────────────────────────┐
       │     DashboardScreen    │ (Capa de Presentación - UI)
       │  + tema de la home     │ (ocho temas)
       └────────────────────────┘
```

---

## 2. Descripción de Componentes Clave

### A. Capa de Datos: `TelemetryService` (Android Service)
Es un servicio foreground autogestionado que actúa como puente de hardware y
propietario de la sesión de conducción. Permanece activo cuando otra aplicación
ocupa la pantalla y su notificación es de baja prioridad. Sus responsabilidades son:
1. Enlazarse mediante **Binder IPC** a `com.szchoiceway.eventcenter`.
2. Registrar un callback `ICallbackfn` remoto.
3. Observar los ajustes asíncronos de Android (`Settings.Global`).
4. Traducir las tramas y exponerlas mediante `StateFlow`.
5. Iniciar el viaje con la primera velocidad positiva y mantener tiempo,
   distancia y consumo medio aunque el launcher no esté visible.
6. Persistir esos acumuladores por `BOOT_COUNT` para tolerar recreaciones del
   proceso sin convertirlos en históricos globales.

### B. Capa de Negocio: `LauncherViewModel`
El ViewModel adapta los datos del servicio a la interfaz:
1. Se conecta al `TelemetryService` mediante un enlace local de servicio (`LocalBinder`) y expone sus flujos (velocidad, RPM, marcha, puertas, combustible, autonomía, estadísticas de viaje, parcial y total, historial de viajes).
2. Recibe el `StateFlow` de telemetría y lo transforma o limpia si es necesario (ej: conversión de millas a kilómetros, formateo de strings).
3. Gestiona acciones del sistema como:
   * Obtener y filtrar el listado de aplicaciones instaladas (`App Drawer`).
   * Lanzar aplicaciones externas (Waze, MMI original, Ajustes).
4. Mantiene el estado de día/hora mediante tareas programadas seguras con coroutines (`Flow`-based timers).

### C. Capa de Presentación: `Compose UI`
Construida al 100% de manera declarativa:
- No almacena estado mutable directo de telemetría; solo observa el estado expuesto por el ViewModel.
- Divide la interfaz en componentes atómicos independientes y modulares (ej: `ProgressRingIndicator` para las esferas del tema Clásico, `LinearGauge` para las barras de los demás temas, `HomeBlocks` para los bloques y testigos del pie).
- Las disposiciones de la home (`ClassicHomeLayout`, `HorizonHomeLayout`, `FacetsHomeLayout`, `TelemetryHomeLayout`, `IllustratedHomeLayout` para Submarino y Luna, `GalaxyHomeLayout` y `AdventureHomeLayout`, paquete `com.lito.a5launcher.ui.home`) comparten un único `HomeState` y las mismas acciones; el tema se elige en Ajustes → Temas (véase [Temas de la home](../feature-home-themes/README.md)).
- Diseñada con optimizaciones para evitar recomposiciones innecesarias de Compose y mantener una tasa de refresco constante de **60 fps** incluso ante ráfagas masivas de datos CAN.

### D. Otros módulos del paquete

| Paquete o fichero | Responsabilidad |
|---|---|
| `com.lito.a5launcher.functional` | Registros funcionales de eventos (diario, códec, archivo, ajustes) |
| `com.lito.a5launcher.assistant` | Asistente de voz IA en tiempo real (proveedores Gemini Live y OpenAI Realtime, audio, acciones de navegación, resolución de destinos con Google Places) |
| `com.lito.a5launcher.location` | `LocationRepository`: ubicación y validación del rumbo |
| `JourneyHistoryStore`, `JourneyHistoryManager`, `PartialStatistics`, `RefuelTracking`, `DistanceSinceRefuelStore` | Historial de viajes y parciales, estadísticas y distancia desde el último repostaje |
| `GitHubLauncherUpdates`, `LauncherUpdateInstaller` | Comprobación de versiones en GitHub Releases e instalación de la actualización |
| `RecentAppsAccessibilityService` | Servicio de accesibilidad que abre la vista nativa de Recientes (`GLOBAL_ACTION_RECENTS`) |
| `com.lito.a5launcher.ui.components` | Mapa (`CockpitMap`, caché de estilos, puntos de interés y avisos de aproximación por categoría), ajustes, paneles de estadísticas y de registros |

Los puntos de interés se importan desde GeoJSON propio del usuario (véase
`../feature-poi/README.md`); la aplicación no incluye catálogos de terceros.

---

## 3. Optimización del Rendimiento (Tasa de Muestreo Alta)

Dado que la MCU envía datos CAN a alta velocidad (hasta un evento cada 5-10 milisegundos):
1. **Desacoplamiento de Hilos:** El procesamiento de bytes y las transacciones de Binder IPC ocurren estrictamente en el pool de hilos de background (`Dispatchers.Default`), nunca en el hilo principal de la UI (`Main Thread`).
2. **Filtrado de Cambios (`distinctUntilChanged`):** El flujo `StateFlow` filtra valores idénticos consecutivos. Si la velocidad o las RPM no han variado físicamente respecto al milisegundo anterior, la UI no se vuelve a dibujar.
3. **Optimización de Compose:** Los componentes de dibujo personalizado (`Canvas`) para los anillos de progreso reciben parámetros inmutables (`@Stable` o `@Immutable`) para asegurar que solo se repinte el arco de progreso cuando el valor numérico cambie sustancialmente.

---

## 4. Integración del Sistema Operativo como Launcher

Para que la aplicación actúe como la pantalla de inicio principal, se declaran los siguientes filtros de intención en el `AndroidManifest.xml` de la actividad principal (`MainActivity`):

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

`MainActivity` declara además el filtro `MAIN`/`LAUNCHER` para aparecer en el cajón de aplicaciones del sistema, y usa `launchMode="singleInstance"`.

Esto hace que Android la registre como un lanzador legítimo. Al presionar el botón "Home" físico o capacitivo del vehículo, el sistema operativo retornará inmediatamente a esta interfaz de forma segura.

`MainActivity` está marcada además como `android:directBootAware="true"`. Durante
el intervalo entre el arranque del kernel y el desbloqueo del usuario sólo
muestra una superficie negra y no accede a preferencias, GPS, MapLibre,
telemetría ni almacenamiento protegido por credenciales. Al recibir
`ACTION_USER_UNLOCKED` sustituye esa superficie por el dashboard completo.

El firmware del Navifly no siempre entrega de forma fiable ese broadcast al
primer `HOME`. Para evitar que el placeholder quede negro indefinidamente, la
actividad comprueba también `UserManager.isUserUnlocked` cada 500 ms mientras
espera, y repite la comprobación en `onStart`, `onResume` y cada `onNewIntent`.
El mapa sólo se construye después del desbloqueo: su red o su caché no pueden
bloquear esta transición.

Incluso después del desbloqueo, el `MapView` no se crea durante la primera
composición. El cockpit presenta primero dos frames completos con toda la
telemetría y, 250 ms después, adjunta el renderer de MapLibre. Las descargas ya
eran asíncronas, pero esta separación evita que la construcción de la vista y
la apertura de su caché SQL compitan con el primer frame de la actividad
`HOME`, especialmente durante un arranque en frío.

Este comportamiento permite que Android resuelva A5 Cockpit como aplicación
`HOME` desde la primera fase del arranque, en lugar de recurrir temporalmente a
Quickstep porque nuestra actividad todavía no fuese elegible. No se intenta
forzar la apertura desde `BOOT_COMPLETED`, ya que Android 14 restringe los
arranques de actividades desde segundo plano.

La aplicación continúa declarando su actividad como `HOME`, pero los ajustes
internos no muestran ni modifican el launcher predeterminado. Esa elección
permanece bajo el control de los ajustes del sistema.

---

## 5. Acciones del sistema verificadas contra Panel

Las acciones superiores reproducen el comportamiento de la librería Szchoiceway
incluida en Panel:

* **Navegación:** consulta `NAV_PACKAGENAME` y `NAV_ACTIVITYNAME` en
  `SysVarProvider`. Si no están disponibles usa `com.waze` como fallback.
* **MMI/Coche:** envía el broadcast
  `com.szchoiceway.eventcenter.EventUtils.ACTION_SWITCH_ORIGINACAR` dirigido a
  `com.szchoiceway.eventcenter`.
* **Ajustes:** abre
  `com.szchoiceway.settings/com.szchoiceway.settings.MainActivity`; si esa
  actividad no existe utiliza `Settings.ACTION_SETTINGS`.

Estas rutas sustituyen intents genéricos anteriores que no coincidían con la
implementación de fábrica.

---

## 6. Compilación de producción

Desde el directorio `a5-launcher`, `./scripts/compile.sh` ejecuta las pruebas unitarias
debug sobre el código productivo y Android Lint de la variante release, activa
R8 y la eliminación de recursos no
utilizados, desactiva el replay de telemetría y genera un APK instalable en:

```text
out/A5Cockpit.apk
```

En `out` se conservan además su SHA-256 y el mapping de R8. La entrega o copia a
un dispositivo se realiza fuera del script para que el repositorio no dependa de
un proveedor de almacenamiento concreto.

Sin credenciales de publicación, la compilación local se firma con la clave de
depuración de la máquina, lo que basta para pruebas directas en el coche. Las
versiones publicadas se firman en el equipo local con una clave estable
(variables `A5_RELEASE_*`), de modo que las copias instaladas se pueden
actualizar. La publicación en GitHub Releases, el esquema de versiones
(`scripts/release-version.py`) y el paso de autorización se describen en
`AGENTS.md`; no se documentan valores secretos en el repositorio.

El release sólo incluye código nativo `arm64-v8a`, la arquitectura del Navifly
Snapdragon 685. Debug mantiene las ABI adicionales necesarias para ejecutar la
aplicación en el emulador.
