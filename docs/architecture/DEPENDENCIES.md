# Estado de dependencias

Auditoría actualizada el 17 de noviembre de 2026. Sólo se seleccionan versiones
estables publicadas en los repositorios oficiales.

## Versiones activas

| Componente | Versión | Estado |
|---|---:|---|
| Android Gradle Plugin | 9.5.1 | Actualizado |
| Gradle Wrapper | 9.9.1 | Actualizado; SHA-256 oficial fijado |
| Kotlin integrado / Compose Compiler | 2.4.21 | Versiones coordinadas mediante el plugin Compose |
| Compose BOM | 2026.09.00 | UI, Runtime y Foundation 1.12.1 |
| AndroidX Core | 1.19.1 | Actualizado |
| AndroidX Activity | 1.13.0 | Actualizado |
| AndroidX Lifecycle | 2.11.0 | Actualizado |
| Core Splashscreen | 1.2.0 | Actualizado |
| MapLibre Native | 13.7.1 | Última versión de la línea 13.x; el informe sólo señala `2026.11.2-globe`, una variante con proyección de globo y esquema de versión distinto, que no se adopta. Pendiente de validación en el Navifly |
| OkHttp | 5.5.1 | Actualizado |
| JUnit | 4.13.2 | Última versión estable de JUnit 4 |
| org.json (pruebas) | 20260814 | Actualizado; no se empaqueta en el APK |
| Plugin de informe de actualizaciones | 0.67.0 | Actualizado |
| grpcio (emulador) | 1.85.1 | Actualizado; no se empaqueta en el APK |

MapLibre utiliza los estilos vectoriales Positron, Liberty y Bright de
OpenFreeMap. osmdroid se ha retirado del APK; el directorio de su antigua caché
(`osmdroid`) sólo se elimina mediante la acción de mantenimiento.

## Compatibilidad Android

El proyecto usa Kotlin integrado en AGP 9.5, sin aplicar
`org.jetbrains.kotlin.android`. El plugin Compose (2.4.21) mantiene alineados el
compilador y sus plugins. La máquina de desarrollo usa JDK 25 y GitHub Actions
JDK 21; ambos compilan con Java 17 como nivel de bytecode para Android, lo que no
añade una JVM al APK ni exige ninguna versión de Java en el coche.

Se compila y se declara `targetSdk 37` para mantener el contrato Android actual;
`minSdk 34` refleja que esta aplicación está diseñada exclusivamente para el
Navifly de referencia con Android 14.

La compatibilidad oficial entre versiones de AGP, Gradle y Kotlin se consulta en
las notas de versión de [AGP](https://developer.android.com/build/releases/about-agp)
y la [guía de Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html);
la combinación seleccionada se valida localmente con las pruebas y la compilación.

La combinación actual compila con `testDebugUnitTest` (326 pruebas sin fallos),
`lintRelease`, `minifyReleaseWithR8` y `assembleDebug`. El informe de
actualizaciones del 17 de noviembre de 2026 no registra dependencias obsoletas
salvo la variante `2026.11.2-globe` de MapLibre ni fallos de resolución.

No se usan versiones dinámicas ni snapshots. La compilación y la prueba en el
emulador validan el conjunto del software; MapLibre debe validarse además
en el dispositivo real por sus drivers OpenGL y su comportamiento como `HOME`.

## Verificación reproducible de actualizaciones

Desde `a5-launcher`, el script siguiente consulta los repositorios y no modifica
el build:

```bash
./scripts/check-dependencies.sh
```

Genera dos archivos ignorados por Git bajo `build/reports/dependencies/`:

- `updates.json`: versiones publicadas más recientes de plugins y dependencias
  declaradas, mediante `io.github.ben-manes.versions.settings` 0.67.0.
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

Lint forma parte de `scripts/compile.sh` y debe finalizar sin errores (el último informe
local registra 0 errores y 16 advertencias). Sólo se
excluyen dos reglas deliberadas: compatibilidad ChromeOS/x86, que no corresponde
al APK específico del coche, y la recomendación Timber originada por una
dependencia transitiva de MapLibre; el diagnóstico del launcher usa Logcat.
