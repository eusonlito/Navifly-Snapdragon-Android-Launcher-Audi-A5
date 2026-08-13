# Plan histórico de desarrollo: Audi A5 Android Launcher (2400x900)

> **Documento histórico.** Conserva la evolución inicial del proyecto y no
> describe necesariamente la implementación vigente. Para el estado actual
> deben consultarse `../architecture/ARCHITECTURE.md`,
> `../feature-telemetry/TELEMETRY.md`, `../feature-map/MAP.md` y `AUDIT.md`.
> El estimador numérico de marcha y los cálculos de consumo/autonomía se
> conservan como decisiones funcionales documentadas. Ya no existen el motor
> raster propio ni los componentes visuales antiguos.

## Especificación del Sistema Destino

*   **Dispositivo:** Audi A5 Headunit Android
*   **MCUVer:** `023090gGS-ADg-EHU-MP.b-260113`
*   **System Ver:** `QCOM 685-GT7HPro-GL-user-20260330.162110`
*   **AndroidVer (OS):** `14` (API level 34, user build `864712070703017-CY2400X900`)
*   **APPVer:** `20260330GT_GL`
*   **Resolución física:** `2400x900` píxeles

## Versiones utilizadas al iniciar el proyecto (histórico)

> Estas versiones ya no están activas. El conjunto vigente está registrado en
> `../architecture/DEPENDENCIES.md`.

Para garantizar la estabilidad y rendimiento en el sistema Android 14 (API 34) de la unidad de destino, se utilizará el siguiente stack tecnológico alineado con las versiones instaladas en el espacio de trabajo:

*   **Gradle Build System:** Gradle `8.2` o superior.
*   **Android Gradle Plugin (AGP):** `8.2.2` (comprobado en la raíz).
*   **Kotlin Compiler:** `1.9.22` (comprobado en la raíz).
*   **Java Virtual Machine Target:** Java 17 (`JvmTarget.JVM_17`).
*   **Compose Compiler Extension:** `1.5.8` o `1.5.10` (compatible de forma nativa con Kotlin `1.9.22`).
*   **Jetpack Compose BOM (Bill of Materials):** `2024.02.00` o superior (UI/Graphics `1.6.2`, Material 3 `1.2.0`).
*   **Coroutines:** `1.7.3` para el procesamiento en background asíncrono.

---

## 1. Objective
Crear desde cero una nueva aplicación Android 14 que funcione como Launcher principal (Home Screen) completo, elegante y funcional para la unidad Android (resolución ultra ancha de 2400x900) del Audi A5. Esta nueva aplicación integrará la lógica de telemetría obtenida por AIDL IPC (validada previamente) para mostrar un cuadro de instrumentos digital en tiempo real junto con funciones esenciales de infoentretenimiento.

## 2. Background & Motivation
Tras haber resuelto exitosamente la ingeniería inversa del protocolo de comunicación de Szchoiceway en nuestra app de pruebas y conseguir capturar datos de alta frecuencia (velocidad, RPM, puertas y bloque térmico) a través del servicio IPC Binder (`EventService`), hemos validado la viabilidad técnica. En lugar de adaptar la aplicación de pruebas/logger, la mejor aproximación arquitectónica es crear un proyecto completamente nuevo y limpio en el subdirectorio `a5-launcher`, diseñado desde el primer día para ser un Launcher de alto rendimiento que no bloquee el hilo de renderizado principal.

## 3. Scope & Impact
El proyecto abarcará la creación de un nuevo proyecto Android (con Jetpack Compose) y la portabilidad del código núcleo de telemetría (AIDL, Service).
- **Impacto del Sistema:** La nueva aplicación se configurará en su `AndroidManifest.xml` con `CATEGORY_HOME` y `CATEGORY_DEFAULT` para poder ser seleccionada como el lanzador principal del sistema (Home Screen).
- **Arquitectura Limpia:** Separación clara entre la capa de datos (Servicio IPC/Observadores) y la capa de presentación (Compose UI) mediante un flujo reactivo (`StateFlow`).

---

## 4. Proposed Solution & Architecture

### A. Capa de Datos (TelemetryService)
- Se implementará un `Service` en segundo plano autogestionado que se conectará al servicio IPC `com.szchoiceway.eventcenter.EventService`.
- Se utilizará la transacción Binder **`120`** para registrar el callback `ICallbackfn` mediante `setDashBoardCallback`.
- El procesamiento del array de bytes ocurrirá **estrictamente en `Dispatchers.Default`** para no bloquear el Main Thread de la UI.
- **State Management (Flujos Granulares):** El servicio expondrá flujos independientes (`speedFlow`, `rpmFlow`, `doorsFlow`, `coolantTempFlow`, `rangeFlow`, `outsideTempFlow`) en lugar de un objeto de estado unificado. Esto garantizará que las actualizaciones constantes del motor (a alta frecuencia de hasta 5ms) no provoquen la recomposición innecesaria del resto del dashboard (ej: las puertas o el reloj), manteniendo los 60 FPS estables.

### B. Capa de Negocio (LauncherViewModel)
- El ViewModel se conectará al `TelemetryService` mediante un binder local y observará los flujos granulares.
- Aplicará las transformaciones físicas correspondientes:
  - **Velocidad:** `int speed = ((bArr[11] & 0xFF) * 256) + (bArr[12] & 0xFF)` (Máx 300 km/h)
  - **RPM:** `int rpm = ((bArr[13] & 0xFF) * 256) + (bArr[14] & 0xFF)` (Máx 8000 RPM)
  - **Puertas (Bitmask byte 5):** `bArr[5]` (Bit 5 piloto, Bit 4 copiloto, Bit 7 trasera izquierda, Bit 6 trasera derecha, Bit 3 maletero).
  - **Bloque térmico:** Temperatura interpretada del primer byte del mensaje 95 mediante `double temp = ((bArr[0] & 0xFF) / 2.0) + 15.5`. Las tramas reales sólo contienen dos bytes y no aportan modo ni ventilador.
  - **Autonomía (Remaining Range):** `int range = ((bArr[5] & 0xFF) * 256) + (bArr[6] & 0xFF)`
  - **Temperatura Exterior:** Decodificada de manera segura mediante cásting a `Short` signed en Kotlin de los bytes `17` y `18`:
    ```kotlin
    val tempRaw = (((bArr[17].toInt() and 0xFF) shl 8) or (bArr[18].toInt() and 0xFF)).toShort().toInt()
    val outsideTemp = tempRaw * 0.1
    ```
  - **Estimador Inteligente de Marchas (Manual Gearbox):** Dado que la MCU no transfiere de manera activa la marcha en el callback, el ViewModel calcula en tiempo real la relación óptima entre revoluciones del motor y velocidad para renderizar de `1` a `6`, `N` (punto muerto) o `R` (marcha atrás).
  - **Consumo de Combustible Medio:** Un simulador dinámico que estima el consumo en litros/100km evaluando la carga instantánea del motor (`rpm / 1800`) y la resistencia aerodinámica (`speed > 110`).
  - **Odómetro Real:** Se decodifica como entero de 24 bits desde los bytes 20-22
    del mensaje 90. No se usa una base ficticia ni acumulación local.
- Proveerá los flujos a la vista usando estados `@Stable` y `@Immutable` de Compose.

### C. Capa de Presentación (Compose UI - 2400x900)
- **Estructura Base:** Un contenedor horizontal (`Row` a pantalla completa) dividido en `Sidebar (15%)` y `Dashboard (85%)`. El fondo será de color *Deep Obsidian* (`0xFF0F0F12`).
- **Sidebar (Panel Izquierdo):** Ancho fijo de `360.dp`. Barra de navegación vertical fija con accesos rápidos grandes de `80.dp`:
  - **Navegación:** Abre `com.waze` o aplicación de navegación configurada.
  - **Panel de Apps (App Drawer):** Despliega un menú en formato grid de 6 columnas superpuesto.
  - **MMI Original:** Lanza el intent/acción de Szchoiceway para cambiar a la señal de vídeo analógica original del coche.
  - **Ajustes:** Lanza la app de ajustes de Android.
- **Dashboard Central:**
  - **Reloj, Fecha y Temperatura Exterior:** Ubicado en la zona superior central de manera sofisticada. La fecha y hora siguen estrictamente el formato personalizado solicitado por el usuario: `'HH:mm EEEE d-MM-yyyy'` (Ej. `22:30 Martes 20-06-2026`). Se muestra además la lectura del sensor de temperatura exterior.
  - **Esferas Analógicas (ProgressRingIndicator):** Componentes gráficos personalizados dibujados mediante `Canvas`.
    - *Velocidad (Izquierda):* Arco de 270 grados. Relleno progresivo en degradado S-Line Red (`0xFFE30A17`). Límite 300 km/h.
    - *Revoluciones (Derecha):* Relleno progresivo. Al superar las 6500 RPM, entra en zona roja parpadeante. Integra un visualizador de **Marcha Manual** (`centerGear`) que enseña el cambio actual (ej. `3`) con tipografía S-Line Red en negrita dentro del mismo dial.
  - **Coche y Puertas (CarDoorsDiagram):** Ilustración cenital vectorial de un Audi A5 Coupe en el centro de la pantalla. Superpone zonas rojas iluminadas y sectores de alerta radiantes de forma de haz de luz degradada basándose en la decodificación del byte `bArr[5]`.
  - **Estado (`VitalsComponent`):** Banda inferior con consumo medio estimado,
    combustible, autonomía estimada y odómetro real. El clima se omite hasta
    disponer de datos validados.

---

## 5. Verification
- Se validará la compilación sin errores utilizando el JDK local.
- Se comprobará mediante pruebas unitarias en `TelemetryDecoderTest` el parser de los bytes de CAN.
- Se testeará la correcta visualización a 2400x900 simulando la rotación de pantalla en el emulador de desarrollo o dispositivo de pruebas.

## 6. Estado de implementación visual

### Iteración 01 — cuadro panorámico responsive

* Eliminada de la pantalla principal la sidebar fija de 360 dp por ser
  incompatible con los 420 dpi usados por el emulador 2400x900.
* Añadida una cabecera compacta con las cuatro acciones del launcher.
* Reorganizadas las esferas: RPM a la izquierda y velocidad a la derecha, siguiendo
  la referencia visual suministrada.
* Añadido escenario central con perspectiva, anillos y vehículo cenital.
* Consolidada la información secundaria en una banda inferior permanente.
* Conservado el App Drawer como overlay de seis columnas.
* Compilación `debug` verificada y captura guardada en
  `screenshots/iteration-01.png`.

### Trabajo visual pendiente

* Sustituir los placeholders por los recursos definitivos enumerados en
  `../design/IMAGES.md`.
* Afinar tipografía, graduaciones y halos tras incorporar los fondos de esfera.
* Validar estados simulados de RPM, velocidad, puertas y testigos.
* Comparar cada iteración mediante capturas completas de 2400x900.

### Iteración 02 — jerarquía e indicadores

* Fecha y hora agrupadas.
* Acciones superiores reducidas a iconos; MMI representado por un coche.
* Velocidad trasladada a la esfera izquierda y RPM a la derecha.
* Eliminados los rótulos redundantes de ambas esferas.
* Refrigerante retirado de la banda principal.
* Puertas, marcha, cinturón y freno de mano recuperados como indicadores
  permanentes.
* Documentada en `../features/telemetry/TELEMETRY.md` la diferencia entre telemetría real, valores
  iniciales y estimaciones.
* Compilación e instalación verificadas; captura guardada en
  `screenshots/iteration-02.png`.

### Iteración 03 — panel central de testigos

* Cabecera dividida en tres columnas exactamente iguales para garantizar el
  centrado óptico y geométrico de las acciones.
* Eliminado el vehículo cenital del centro del cuadro.
* Añadido un panel central con iconos de puertas, cinturón y freno de mano.
* Los testigos permanecen atenuados en estado correcto y pasan a naranja en
  alerta.
* Marcha situada bajo los testigos y eliminada de la banda inferior para evitar
  duplicados.
* Captura validada en `screenshots/iteration-03.png`.

### Iteración 04 — columna de testigos y marcha integrada

* Reducido el ancho relativo de la zona central.
* Puertas, cinturón y freno de mano reorganizados en una sola columna vertical.
* Marcha retirada del centro e integrada dentro de la esfera de RPM.
* Marcha ausente de la banda inferior para mantener una única fuente visual.
* Compilación, instalación y composición verificadas en
  `screenshots/iteration-04.png`.

### Iteración 05 — ajuste del tacómetro

* Separada la marcha del valor de RPM mediante espacio y sin etiqueta.
* Eliminado `r/min` de la unidad; el tacómetro muestra únicamente `x1000`.

### Replay de telemetría real

* Añadido replay automático del JSONL de `debug` cuando EventService no existe.
* Conservada una única ruta de decodificación para callbacks reales y grabados.
* Reproducción en bucle con timestamps originales y cancelación al conectar la
  MCU real.
* Asset y funcionalidad excluidos de `release`.
* Corregida la lectura visual de RPM a formato decimal `x1000`.
* Identificados bytes 20-22 como odómetro real de este firmware, no autonomía.

### Ajustes posteriores al replay

* Alineados verticalmente los valores de velocidad y RPM.
* Compactado el espaciado del valor decimal de RPM.
* Consumo convertido en media acumulada del viaje con muestreo cada segundo.
* Simplificados los estados textuales de puertas y cinturón.

### Integración contrastada con aplicaciones de fábrica

* Revisadas `openlauncher`, `openlauncher-TW` y Panel.
* Confirmado que los launchers abiertos recurren a GPS y no aportan nuevos datos
  CAN.
* Documentadas las fuentes reales de odómetro y autonomía, y la limitación del
  consumo.
* Navegación conectada a la aplicación configurada en `SysVarProvider`.
* MMI y Ajustes actualizados con las acciones/componentes usados por Panel.
* Testigos centrales reducidos a iconos, valores principales con peso homogéneo y
  botón Volver trasladado a la izquierda.

### Depuración de datos sin fuente fiable

* Eliminado el bloque de clima de la interfaz.
* Autonomía conservada provisionalmente hasta validarla en el vehículo.
* Badge de marcha atenuado con blanco semitransparente y fondo neutro.
