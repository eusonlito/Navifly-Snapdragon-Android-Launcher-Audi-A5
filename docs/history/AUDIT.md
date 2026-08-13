# Auditoría técnica de A5 Cockpit

> **Fotografía histórica, no estado vigente.** Los hallazgos corresponden al
> código del 28 de julio de 2026, anterior a la remediación, MapLibre 13.2.0 y
> la actualización completa del toolchain. Consulte `REMEDIATION.md`,
> `../architecture/DEPENDENCIES.md` y `../architecture/ARCHITECTURE.md` para
> conocer el estado actual.

**Fecha:** 28 de julio de 2026
**Alcance:** `a5-launcher` (aplicación, configuración Gradle, manifiesto, pruebas,
scripts y documentación técnica).
**Tipo:** auditoría estática y de compilación. No se ha modificado código de la
aplicación.

## 1. Resumen ejecutivo original

La aplicación es funcional y su arquitectura básica es comprensible: una
actividad `HOME`, un `ViewModel`, un servicio que adapta la telemetría del
fabricante y una interfaz Compose con un `MapView` embebido. El mapa usa una
biblioteca consolidada, la variante `release` excluye el replay, R8 está
activado y los fallos al crear el mapa no sustituyen deliberadamente todo el
dashboard.

No obstante, el estado actual **todavía no debe considerarse una versión final
cohesionada**. La auditoría encuentra cuatro problemas prioritarios:

1. El camino de arranque Direct Boot accede a preferencias protegidas antes de
   comprobar que el usuario esté desbloqueado. Puede explicar el bucle de
   reinicios o la pantalla negra en un arranque en frío.
2. El consumo mostrado no procede del coche ni es un consumo medio real: es
   una heurística basada en RPM y velocidad, presentada sin indicar su
   incertidumbre. La autonomía calculada hereda ese error.
3. El ciclo de vida del mapa, GPS y red no sigue el estado visible de la
   actividad. Continúan activos cuando el launcher queda en segundo plano.
4. El proceso de producción genera un APK, pero no ejecuta Android Lint. Lint
   detecta cuatro errores de compatibilidad con el `minSdk 26`.

También existe deuda acumulada: dos ficheros principales superan las 1.000
líneas, permanecen tres componentes visuales y un motor de mapas ya
reemplazados, y las pruebas actuales duplican fórmulas en vez de ejercitar el
código de producción.

### Dictamen

| Área | Resultado |
|---|---|
| Compilación release optimizada | Correcta |
| Pruebas unitarias actuales | 9 correctas, cobertura insuficiente |
| Android Lint | No conforme: 4 errores y 17 avisos |
| Arranque normal tras desbloqueo | Diseño razonable |
| Arranque en frío / Direct Boot | No conforme |
| Telemetría básica (velocidad, RPM, puertas) | Coherente con el protocolo documentado |
| Consumo, autonomía y marcha | Parcialmente estimados; no equivalen a datos reales confirmados |
| Mapa en primer plano | Implementación razonable y tolerante a pérdida de red |
| Mapa en segundo plano | No conforme con ciclo de vida |
| Preparación para distribución | No conforme por firma, versionado, Lint y pruebas |

## 2. Método y evidencias

Se revisaron todos los fuentes Kotlin, recursos, manifiesto, Gradle, scripts de
compilación, pruebas y documentos Markdown. Se ejecutaron:

```text
./gradlew clean testDebugUnitTest lintDebug assembleRelease
./gradlew --no-daemon testReleaseUnitTest assembleRelease
```

Resultados:

* `testDebugUnitTest`: correcto.
* `testReleaseUnitTest`: correcto.
* `assembleRelease`: correcto; APK minificado y recursos reducidos.
* `lintDebug`: fallido con 4 errores y 17 avisos.
* No existen pruebas instrumentadas, de Compose, de ciclo de vida, de arranque
  Direct Boot ni contra un doble del servicio SzChoiceWay.
* `a5-launcher` no contiene repositorio Git propio. No ha sido posible auditar
  regresiones por commit ni separar históricamente decisiones definitivas de
  parches.

## 3. Hallazgos

La prioridad combina impacto, probabilidad y dificultad de detectar el fallo
durante una prueba manual normal.

### A-01 — Crítico — Acceso inválido durante Direct Boot

**Evidencia:** `MainActivity.onCreate()` llama a `logLifecycle()` antes de
consultar `UserManager.isUserUnlocked`. `logLifecycle()` abre
`launcher_settings` y crea/activa `MapDebugLogger`, que a su vez abre
`map_debug_log` y puede usar `MediaStore`.

**Impacto:** al ser `MainActivity` `directBootAware`, puede ejecutarse antes de
que el almacenamiento cifrado por credenciales esté disponible. Android puede
rechazar el acceso a esas preferencias. Esto encaja con el comportamiento
observado: varios intentos de inicio, pantalla negra y arranque correcto después
de detener y abrir la aplicación con el sistema ya desbloqueado.

**Inconsistencia documental:** `../architecture/ARCHITECTURE.md` afirma que antes del desbloqueo
no se accede a preferencias ni almacenamiento protegido, pero el código hace lo
contrario.

**Criterio de cierre futuro:** ninguna ruta anterior a `isUserUnlocked` debe
crear el `ViewModel`, consultar preferencias normales, inicializar el logger,
usar `MediaStore`, GPS, telemetría u osmdroid. Debe probarse con reinicio real,
no sólo relanzando la actividad.

### A-02 — Crítico — El consumo medio no es un dato de consumo

**Evidencia:** `LauncherViewModel.startTripConsumptionAveraging()` genera cada
segundo una muestra con:

```text
4,8 + (rpm / 1800 × 1,5) + penalización por velocidad superior a 110
```

Después calcula la media aritmética temporal de esas muestras.

**Impacto:** la fórmula no usa caudal, combustible inyectado, distancia
recorrida ni un contador del coche. No representa consumo instantáneo ni media
de viaje en L/100 km. Además, la sesión se reinicia con el proceso/ViewModel,
no con el viaje o ciclo de encendido. El valor se presenta en la interfaz como
`CONSUMO`, con la misma autoridad visual que los datos CAN reales.

**Consecuencia:** cuando no llega autonomía nativa, `CompactVitals` calcula
`litros / consumo × 100`; por tanto, esa autonomía también puede ser
materialmente incorrecta aunque la fórmula aritmética sea válida.

**Criterio de cierre futuro:** obtener una magnitud de consumo confirmada del
coche o declarar el dato como no disponible. Una estimación sólo sería
aceptable con un modelo calibrado y una indicación explícita de que no es un
valor del vehículo.

### A-03 — Alta — Marcha estimada presentada como marcha seleccionada

**Estado actual (31-07-2026): mitigado, pendiente de validación completa en
coche.** El umbral genérico y sin estado descrito originalmente fue sustituido
por un perfil del A5 2.0 TDI manual de seis marchas. El estimador exige dos
muestras coincidentes, descarta régimen bajo y relaciones alejadas, conserva
la última marcha ante transitorios breves y respeta inmediatamente `R`/`N`
cuando ChoiceWay los proporciona. Cada mensaje 90 se entrega como una muestra
atómica para que velocidad y RPM no cuenten dos veces ni se combinen entre
tramas. Las relaciones 1–3 están contrastadas con el
log real del 24-07-2026; 4–6 siguen pendientes de una captura estable específica.

**Evidencia:** cuando `getGearType()` no ofrece un valor reconocido, el
`ViewModel` deduce 1–6 mediante `rpm / velocidad`; parado devuelve `N`. Los
valores iniciales cambian de `P` a `N` cuando se emiten los estados iniciales.
Para varios modos reportados por el fabricante también se estima la relación,
en vez de mostrar el modo recibido.

**Impacto:** embrague pisado, deslizamiento, rueda libre, relaciones distintas
o datos desincronizados producen una marcha incorrecta. En un cuadro de
conducción se presenta como dato cierto.

**Criterio de cierre futuro:** contrastar las seis relaciones con capturas
estables del coche real o localizar una fuente nativa de marcha engranada.

### A-04 — Alta — Mapa, GPS y red permanecen activos en segundo plano

**Evidencia:** `MapView.onResume()` se llama al crear la vista. `onPause()` y
`onDetach()` sólo se llaman cuando la composición se elimina definitivamente.
La composición no se elimina con `Activity.onStop()`. Por el mismo motivo,
siguen registrados los listeners GPS y de conectividad, continúa el tick de un
segundo y el bucle de estado de tiles.

**Impacto:** uso de CPU, GPS, red y batería mientras el usuario está en Waze,
ajustes, MMI u otra aplicación. Los logs observados con
`visible=false` y heartbeats sucesivos son coherentes con el código.

**Criterio de cierre futuro:** vincular recursos y `MapView` a
`STARTED/RESUMED`, pausar al perder visibilidad y reanudar sin reconstrucciones
innecesarias.

### A-05 — Alta — Lint bloquea la conformidad con `minSdk 26`

**Evidencia:** tres accesos a
`MediaStore.Downloads.EXTERNAL_CONTENT_URI` requieren API 29, y
`android:windowLightNavigationBar` requiere API 27. No hay protección por nivel
de API ni recursos versionados.

**Impacto:** el manifiesto promete API 26, pero el logger puede fallar en API
26–28 y el tema no es compatible con el mínimo declarado. El dispositivo real
actual puede no verse afectado, pero el contrato de la aplicación es falso.

**Criterio de cierre futuro:** soportar realmente API 26 o subir el mínimo al
nivel efectivo del hardware objetivo. `lintDebug` y `lintRelease` deben quedar
sin errores.

### A-06 — Alta — Las pruebas no ejercitan el código activo

**Evidencia:**

* `TelemetryDecoderTest` vuelve a escribir dentro del test las mismas fórmulas
  y el mismo mapeo de marcha. No invoca un decodificador de producción. Si el
  servicio cambia, el test puede seguir pasando.
* `RasterMapEngineTest` prueba `RasterMapEngine`, motor que ya no usa la
  interfaz. No prueba osmdroid, su ciclo de vida, caché, estado de tiles,
  orientación ni filtrado GPS.
* No hay pruebas para `MainActivity`, Direct Boot, `LauncherViewModel`,
  navegación, ajustes o fallos/reconexión del Binder.

**Impacto:** las 9 pruebas correctas dan una señal de seguridad mayor que la
protección real contra regresiones.

### A-07 — Alta — Valores iniciales aparentan ser datos reales

**Evidencia:** temperatura exterior empieza en `22.5`, clima en `22.0`,
ventilador en `1`, marcha en `P` y cinturón en estado de alerta hasta recibir
datos. La cabecera muestra la temperatura exterior.

**Impacto:** sin proveedor o antes del primer evento, el usuario ve valores
plausibles que no proceden del coche. En cambio, autonomía usa correctamente
cero como “desconocida”. Falta aplicar la misma semántica al resto.

### A-08 — Alta — Procesamiento de telemetría sin orden ni recuperación robusta

**Evidencia:** cada callback Binder crea una coroutine independiente en un
`CoroutineScope(Dispatchers.Default)` sin `SupervisorJob`. Los eventos pueden
procesarse fuera de orden y una excepción no controlada puede cancelar el
scope completo. `onServiceDisconnected()` no implementa re-registro explícito,
`onBindingDied()` ni reintento del callback. Un fallo de
`setDashBoardCallback()` sólo se registra.

**Impacto:** un fallo transitorio del servicio del fabricante puede congelar
silenciosamente la telemetría hasta recrear el servicio. Campos relacionados
pueden llegar a la interfaz en estados intermedios.

### A-09 — Media — El mapa está diferido, pero no completamente aislado

**Evidencia:** el dashboard compone dos frames y espera 250 ms antes de crear
`MapView`; las descargas de tiles son asíncronas y los errores de construcción
se capturan. Sin embargo, creación/configuración de `MapView`, proveedor y
caché sucede después en el hilo principal.

**Valoración:** cumple el objetivo de mostrar primero los instrumentos y no
espera a la red, pero una inicialización lenta o bloqueada de osmdroid aún puede
causar un tirón posterior en la interfaz. No existe un timeout ni un estado de
salud independiente para el trabajo de inicialización.

### A-10 — Media — La caché configurable promete más de lo que Android garantiza

**Evidencia:** se permiten hasta 5 GB bajo `context.cacheDir`. Esa ubicación es
evictable por Android y el código informa espacio con `File.usableSpace`, no
con la cuota/espacio asignable de `StorageManager`. La precarga se limita al
overshoot de osmdroid; no hay un gestor explícito de vecindad, presupuesto o
prioridad.

**Impacto:** seleccionar 5 GB no reserva 5 GB ni garantiza conservarlos. Puede
inducir una expectativa incorrecta, y la caché puede desaparecer bajo presión
de almacenamiento.

### A-11 — Media — Logger sin política de tamaño ni ciclo de vida

**Evidencia:** cada activación crea un archivo, pero no existe rotación por
tamaño, límite total ni limpieza automática. En debug se registra cada tile,
GPS, heartbeat y diagnóstico HTTP. El singleton mantiene un scope IO durante
toda la vida del proceso.

**Impacto:** si debug queda activado durante viajes largos, los logs pueden
crecer de forma indefinida. El botón manual de borrado reduce el riesgo, pero
no lo controla.

### A-12 — Media — Estructura con restos de implementaciones sustituidas

**Evidencia:** `DashboardScreen.kt` tiene 1.118 líneas y `CockpitMap.kt` 1.002.
Siguen compilándose `RasterMapEngine`, `VitalsComponent`, `SidebarComponent` y
`CarDoorsDiagram`, sin referencias desde la interfaz activa.

**Impacto:** aumenta el coste de entender qué implementación es la vigente,
facilita corregir accidentalmente código muerto y mantiene tests asociados a
una arquitectura abandonada. Es la evidencia más clara de iteraciones
acumuladas, aunque no constituye por sí sola un fallo en ejecución.

### A-13 — Media — Internacionalización incompleta

**Evidencia:** fecha y hora usan `Locale.getDefault()`, pero los textos del
dashboard, mapa, ajustes y aplicaciones están escritos directamente en español.
El separador de miles se fuerza con `Locale("es", "ES")`. Sólo `app_name` está
en recursos.

**Impacto:** la interfaz no sigue completamente el idioma y formato
configurados, contrariamente al criterio acordado. Los cambios de idioma en
ejecución tampoco actualizan los formateadores hasta recrear el `ViewModel`.

### A-14 — Media — El pipeline de producción omite controles esenciales

**Evidencia:** el antiguo script de compilación ejecutaba tests release y `assembleRelease`, pero no
Lint. La variante release:

* usa la clave de debug;
* mantiene `versionCode = 1` y `versionName = 1.0.0`;
* no crea un artefacto con hash ni conserva `mapping.txt` junto al APK;
* no verifica instalación, arranque ni Direct Boot.

**Impacto:** es adecuado para pruebas privadas instalables, no para una
distribución controlada o actualizaciones fiables.

### A-15 — Media — Permisos y copia de seguridad más amplios de lo necesario

**Evidencia:** se declara `RECEIVE_BOOT_COMPLETED`, pero no hay receptor de
arranque. `QUERY_ALL_PACKAGES` es amplio para una pantalla que consulta
actividades `LAUNCHER`. `allowBackup` está activado y puede incluir ajustes y
última posición guardada.

**Impacto:** superficie de permisos y privacidad innecesaria. Para distribución
pública, `QUERY_ALL_PACKAGES` requiere justificación específica.

### A-16 — Baja — Trabajo evitable en segundo plano y en el hilo principal

**Evidencia:** Compose usa `collectAsState()` en lugar de recogida consciente
del ciclo de vida. El reloj actualiza cada segundo aunque sólo muestra minutos.
La consulta al `SysVarProvider` para navegación se realiza desde una coroutine
en `Dispatchers.Main`.

**Impacto:** no explica por sí solo los fallos graves, pero suma actividad
innecesaria y una consulta IPC potencialmente bloqueante en la interacción.

## 4. Revisión funcional

| Funcionalidad | Implementación | Valoración |
|---|---|---|
| Launcher predeterminado | Intent `HOME` y solicitud oficial de `ROLE_HOME` | Correcta; el firmware puede seguir imponiendo Quickstep al arrancar |
| Splash y superficie inicial | Splash API + placeholder negro | Correcta tras desbloqueo; defectuosa antes por A-01 |
| Velocidad | Mensaje 90, bytes 11–12 | Coherente con logs y documentación |
| RPM | Mensaje 90, bytes 13–14 | Coherente con logs y documentación |
| Combustible | Mensaje 90, bytes 15–16 | Coherente con logs; debe conservarse validación de unidad |
| Temperatura exterior | Mensaje 90, bytes 17–18 | Decoder plausible; estado inicial ficticio |
| Odómetro | Mensaje 90, bytes 20–22 | Validado contra capturas cercanas a 220.000 km |
| Autonomía nativa | Mensaje 90, bytes 5–6 si es mayor que cero | Provisional; logs citados no confirman valores reales |
| Autonomía calculada | combustible / consumo × 100 | Aritmética correcta, entrada de consumo no fiable |
| Consumo medio | heurística RPM/velocidad | No cumple semántica de consumo real |
| Puertas | mensaje 93, máscaras de bits | Coherente con capturas |
| Cinturón y freno | `SysVarProvider`, msg 91 sólo en replay | Razonable; defaults pueden producir falsos estados |
| Marcha | `getGearType()` + estimador | Parcial; estimación presentada como dato real |
| Navegación | configuración SzChoiceWay, Waze como fallback | Funcional; consulta en hilo principal |
| MMI | broadcast explícito al EventCenter | Coherente con Panel |
| Ajustes del coche | actividad SzChoiceWay + fallback Android | Coherente y tolerante a ausencia |
| Cajón de aplicaciones | consulta de actividades `LAUNCHER` | Funcional; permiso global posiblemente excesivo |
| Mapa claro | tiles CARTO/osmdroid, color filter desactivado | Correcto en primer plano según prueba real |
| Rotación del mapa | bearing GPS filtrado y animación | Diseño razonable; sin pruebas automatizadas activas |
| Marcador fijo | overlay Compose al 30 % bajo el centro | Correcto por estructura |
| Zoom multitáctil | controles multitáctiles y persistencia por estilo | Correcto |
| Pérdida de red/GPS | última posición + color/badges | Correcto en diseño; listeners no se pausan |
| Caché | caché HTTP de osmdroid en `cacheDir` | Funcional, no equivale a almacenamiento garantizado |
| Debug del mapa | nuevo archivo por activación y borrado manual | Funcional en API 29+; sin límites de crecimiento |
| Replay | sólo variante debug | Correctamente excluido de release |

## 5. Aspectos bien resueltos

* Separación conceptual entre actividad, estado, telemetría e interfaz.
* El mapa no espera a disponer de red para mostrar el resto del cockpit.
* `MapView` se crea después de los primeros frames y la creación está envuelta
  para que un error deje visible la interfaz.
* Uso de `StateFlow` para exponer estado inmutable desde servicio y ViewModel.
* Consultas a aplicaciones instaladas fuera del hilo principal.
* Intents del fabricante explícitos por paquete/componente.
* Replay y captura real no se empaquetan en release.
* R8, optimización y reducción de recursos funcionan.
* Caché, estilo, zoom y última ubicación sobreviven entre sesiones.
* La documentación recoge gran parte de las decisiones y limitaciones; el
  problema principal es que algunas secciones ya no coinciden con el código.

## 6. Orden recomendado de saneamiento

Esta auditoría no aplica cambios. Para una fase posterior, el orden profesional
sería:

1. Corregir y probar Direct Boot (A-01) antes de cualquier mejora visual.
2. Decidir la política de datos no confirmados: consumo, autonomía, marcha y
   estados iniciales (A-02, A-03, A-07).
3. Hacer lifecycle-aware mapa, GPS, red y recogida de estado (A-04, A-16).
4. Extraer decodificadores y reglas a unidades probables; sustituir tests
   duplicados y del mapa antiguo (A-06).
5. Resolver Lint y hacer que forme parte obligatoria del script de producción (A-05,
   A-14).
6. Eliminar únicamente código demostrado como muerto y dividir los dos
   componentes gigantes por responsabilidad (A-12).
7. Endurecer reconexión/orden de telemetría y límites de logs/caché (A-08,
   A-10, A-11).
8. Completar recursos localizados, permisos, firma y versionado (A-13, A-14,
   A-15).

## 7. Condiciones para considerar la versión cohesionada

La versión puede considerarse candidata final cuando:

* completa al menos diez arranques en frío reales sin reinicios ni pantalla
  negra, con y sin red;
* todos los valores visibles tienen una fuente confirmada o un estado
  inequívoco de “no disponible”;
* abandonar el launcher pausa mapa, GPS y trabajo periódico;
* `test`, `lint` y `assembleRelease` son obligatorios y terminan correctamente;
* las pruebas llaman al código real y cubren decodificación, Direct Boot,
  desconexión/reconexión y cálculos visibles;
* no queda un motor de mapas alternativo compilado ni documentación que describa
  una implementación distinta;
* el APK usa versionado incremental y una firma de distribución controlada;
* una prueba de viaje confirma continuidad de telemetría, memoria estable,
  caché acotada y recuperación de red/GPS.

## 8. Conclusión

El proyecto no es una colección improvisada de parches: existe una dirección
arquitectónica reconocible y varias decisiones correctas. Sin embargo, sí
conserva capas de iteraciones anteriores y algunos comportamientos provisionales
han pasado a presentarse como funcionalidad definitiva.

La prioridad no debe ser una refactorización estética general. Debe ser cerrar
primero los contratos observables: arranque seguro, datos veraces, ciclo de vida
y pipeline verificable. Después, una limpieza limitada y respaldada por pruebas
puede convertir la implementación actual en una base cohesionada sin cambiar
cosas por cambiar.

## 9. Estado de remediación

El 28 de julio de 2026 se inició la remediación de esta auditoría. Este
documento conserva los hallazgos originales como fotografía del estado
auditado. Los cambios aplicados se registran en `REMEDIATION.md`; un hallazgo no
debe considerarse cerrado únicamente por aparecer allí, sino después de las
pruebas indicadas en su criterio de cierre.
