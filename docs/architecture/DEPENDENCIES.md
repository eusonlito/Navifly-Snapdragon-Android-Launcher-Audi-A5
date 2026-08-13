# Estado de dependencias

Auditoría actualizada el 12 de agosto de 2026.

## Versiones activas

| Componente | Versión | Estado |
|---|---:|---|
| Android Gradle Plugin | 9.3.1 | Actualizado |
| Gradle Wrapper | 9.7.0 | Actualizado; SHA-256 oficial fijado |
| Kotlin integrado / Compose Compiler | 2.4.10 | Versión integrada con AGP 9.3 |
| Compose BOM | 2026.06.01 | Actualizado |
| AndroidX Core | 1.19.0 | Actualizado |
| AndroidX Activity | 1.13.0 | Actualizado |
| AndroidX Lifecycle | 2.11.0 | Actualizado |
| Core Splashscreen | 1.2.0 | Actualizado |
| MapLibre Native | 13.4.1 | Actualizado |

MapLibre utiliza los estilos vectoriales Positron, Liberty y Bright de
OpenFreeMap. osmdroid 6.1.20 se ha retirado del APK; su antigua caché sólo se
elimina mediante la acción de mantenimiento.

## Compatibilidad Android

El proyecto usa Kotlin integrado en AGP 9.3, sin aplicar
`org.jetbrains.kotlin.android`. Java 17 es el nivel de bytecode y compilación
requerido por AGP; no añade una JVM al APK ni exige Java 17 en el coche.

Se compila y se declara `targetSdk 37` para mantener el contrato Android actual;
`minSdk 34` refleja que esta aplicación está diseñada exclusivamente para el
Navifly de referencia con Android 14.

No se usan versiones dinámicas ni snapshots. La compilación y la prueba en el
emulador validan el conjunto del software; MapLibre 13.4.1 debe validarse además
en el dispositivo real por sus drivers OpenGL y su comportamiento como `HOME`.

## Verificación reproducible de actualizaciones

Desde `a5-launcher`, el script siguiente consulta los repositorios y no modifica
el build:

```bash
./scripts/check-dependencies.sh
```

Genera dos archivos ignorados por Git bajo `build/reports/dependencies/`:

- `updates.json`: versiones publicadas más recientes de plugins y dependencias
  declaradas, mediante `io.github.ben-manes.versions.settings` 0.61.0.
- `release-runtime-classpath.txt`: grafo efectivo que Gradle ha resuelto para
  `releaseRuntimeClasspath`, incluidas las dependencias transitivas.

El informe excluye alfas, betas y candidatos de publicación cuando la versión
activa es estable. Para forzar una consulta de metadatos recién publicados,
ejecutar `./gradlew dependencyUpdates --refresh-dependencies`; Gradle conserva
normalmente ese catálogo durante 24 horas.

El informe identifica los candidatos estables que se actualizan en este
repositorio. Cada actualización se acepta únicamente si `./scripts/compile.sh`
termina correctamente; ese flujo valida la resolución de Gradle, pruebas,
Lint, R8 y el APK de producción. Si falla, se corrige la combinación o se
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
