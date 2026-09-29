# Estado de dependencias

Auditoría actualizada el 29 de septiembre de 2026. Sólo se seleccionan versiones
estables publicadas en los repositorios oficiales.

## Versiones activas

| Componente | Versión | Estado |
|---|---:|---|
| Android Gradle Plugin | 9.4.1 | Actualizado |
| Gradle Wrapper | 9.8.0 | Actualizado; SHA-256 oficial fijado |
| Kotlin integrado / Compose Compiler | 2.4.20 | Versiones coordinadas mediante el plugin Compose |
| Compose BOM | 2026.09.00 | UI, Runtime y Foundation 1.12.1 |
| AndroidX Core | 1.19.1 | Actualizado |
| AndroidX Activity | 1.13.0 | Actualizado |
| AndroidX Lifecycle | 2.11.0 | Actualizado |
| Core Splashscreen | 1.2.0 | Actualizado |
| MapLibre Native | 13.6.1 | Actualizado; pendiente de validación en el Navifly |
| OkHttp | 5.5.0 | Actualizado |
| JUnit | 4.13.2 | Última versión estable de JUnit 4 |
| org.json (pruebas) | 20260814 | Actualizado; no se empaqueta en el APK |
| Plugin de informe de actualizaciones | 0.64.0 | Actualizado |
| grpcio (emulador) | 1.84.0 | Actualizado; no se empaqueta en el APK |

MapLibre utiliza los estilos vectoriales Positron, Liberty y Bright de
OpenFreeMap. osmdroid 6.1.20 se ha retirado del APK; su antigua caché sólo se
elimina mediante la acción de mantenimiento.

## Compatibilidad Android

El proyecto usa Kotlin integrado en AGP 9.4, sin aplicar
`org.jetbrains.kotlin.android`. El plugin Compose selecciona Kotlin 2.4.20
para mantener alineados el compilador y sus plugins. La máquina de desarrollo
usa JDK 25 y conserva Java 17 como nivel de bytecode para Android; esto no añade
una JVM al APK ni exige ninguna versión de Java en el coche.

Se compila y se declara `targetSdk 37` para mantener el contrato Android actual;
`minSdk 34` refleja que esta aplicación está diseñada exclusivamente para el
Navifly de referencia con Android 14.

AGP 9.4 requiere Gradle 9.6 o posterior y admite API 37. La matriz de soporte
completo de Kotlin 2.4.20 llega a Gradle 9.7.0 y AGP 9.3.1; las versiones
seleccionadas son posteriores a esa matriz y requieren verificación local.
Fuentes: [AGP](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html).

La combinación seleccionada ha superado `testDebugUnitTest` (271 pruebas),
`lintRelease` (0 errores, 11 advertencias), `minifyReleaseWithR8` y
`assembleDebug`. El informe de actualizaciones no registra dependencias
obsoletas ni fallos de resolución. En el emulador se ha comprobado el mapa,
los paneles de viaje, parcial y total, la navegación por históricos y la
reapertura en el registro actual. grpcio 1.84.0 ha enviado GPS al emulador.

No se usan versiones dinámicas ni snapshots. La compilación y la prueba en el
emulador validan el conjunto del software; MapLibre 13.6.1 debe validarse además
en el dispositivo real por sus drivers OpenGL y su comportamiento como `HOME`.

## Verificación reproducible de actualizaciones

Desde `a5-launcher`, el script siguiente consulta los repositorios y no modifica
el build:

```bash
./scripts/check-dependencies.sh
```

Genera dos archivos ignorados por Git bajo `build/reports/dependencies/`:

- `updates.json`: versiones publicadas más recientes de plugins y dependencias
  declaradas, mediante `io.github.ben-manes.versions.settings` 0.64.0.
- `release-runtime-classpath.txt`: grafo efectivo que Gradle ha resuelto para
  `releaseRuntimeClasspath`, incluidas las dependencias transitivas.

El informe excluye alfas, betas y candidatos de publicación cuando la versión
activa es estable. Para forzar una consulta de metadatos recién publicados,
ejecutar `./gradlew dependencyUpdates --refresh-dependencies`; Gradle conserva
normalmente ese catálogo durante 24 horas.

El informe identifica los candidatos estables que se actualizan en este
repositorio. La validación local incluye resolución de Gradle, pruebas,
Lint, R8 y ejecución del APK debug en el emulador. Si hay cambios visuales
pendientes, se puede ejecutar `:app:minifyReleaseWithR8` sin generar el APK de
producción. Tras la validación visual requerida en
[CAPTURES.md](../design/CAPTURES.md), `./scripts/compile.sh` valida además el
APK de producción. Si falla, se corrige la combinación o se
descarta el cambio. Las dependencias con impacto gráfico, especialmente
MapLibre, se prueban además en el Navifly.

## Arquitectura nativa del APK

El APK de producción es específico para el Navifly Snapdragon 685 y sólo
empaqueta `arm64-v8a`. Así se evita incluir tres copias innecesarias de
`libmaplibre.so` para ARM de 32 bits, x86 y x86_64. La variante debug conserva
todas las ABI publicadas por MapLibre para continuar funcionando en el
emulador x86_64.

Las librerías nativas de MapLibre y AndroidX Graphics se distribuyen ya
procesadas. La configuración de empaquetado evita intentar aplicarles `strip`
por segunda vez, sin conservar símbolos adicionales ni aumentar el APK.

Lint forma parte de `scripts/compile.sh` y debe finalizar con `No issues found`. Sólo se
excluyen dos reglas deliberadas: compatibilidad ChromeOS/x86, que no corresponde
al APK específico del coche, y la recomendación Timber originada por una
dependencia transitiva de MapLibre; el diagnóstico del launcher usa Logcat.
