# A5 Launcher

A5 Launcher es un sustituto de la pantalla de inicio de Android diseñado para
una unidad ultra panorámica de 2400×896 instalada en un Audi A5. Reúne en una
interfaz orientada a la conducción un cuadro de estilo OEM, telemetría del
vehículo, mapa MapLibre, datos de viaje y un asistente IA por voz opcional.

> Es un proyecto personal e independiente. No está afiliado ni respaldado por
> Audi, Volkswagen Group, Waze, Google, OpenAI, ChoiceWay, Navifly ni por
> ningún otro fabricante de vehículos o software.

[Read in English](README.md)

## Funcionalidades principales

- Velocidad, revoluciones, marcha estimada y testigos del vehículo.
- Mapa vectorial MapLibre con caché local y puntos de interés GeoJSON importables.
- Tiempo, distancia, consumo estimado y autonomía del viaje actual.
- Acciones y bloques inferiores reordenables.
- Asistente de voz opcional mediante OpenAI o Gemini, configurado en el coche.
- Lanzador de aplicaciones y accesos a los ajustes del sistema y del launcher.
- APK release ARM64 optimizado para el dispositivo de referencia.

El proyecto está adaptado al dispositivo documentado y a la interfaz de eventos
propietaria de ChoiceWay. No es una aplicación Android Auto universal; otros
vehículos, cajas CAN o firmwares pueden necesitar cambios.

## Capturas

![Dashboard de A5 Launcher](docs/screenshots/dashboard.png)

| Aplicaciones | Ajustes de Mapa y POI |
|---|---|
| ![Aplicaciones](docs/screenshots/applications.png) | ![Ajustes de Mapa y POI](docs/screenshots/settings-map.png) |

| Ajustes del Asistente IA | Ajustes del Sistema |
|---|---|
| ![Ajustes del Asistente IA](docs/screenshots/settings-ai.png) | ![Ajustes del Sistema](docs/screenshots/settings-system.png) |

## Requisitos

- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools compatible con Android Gradle Plugin 9.3.1
- Sistema de compilación de 64 bits

El wrapper de Gradle está incluido y no hace falta instalar Gradle por separado.

## Compilación

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
./scripts/compile.sh
```

`scripts/compile.sh` ejecuta las pruebas, Lint de release y la compilación optimizada.
Guarda el APK instalable, su SHA-256 y el mapping de R8 —si existe— en `out/`.
Actualmente el release usa la clave debug estándar de Android para pruebas e
instalación local; no debe considerarse una firma de distribución definitiva.

Para usar el emulador con un replay CAN/GPS local:

```bash
./scripts/emulator.sh --replay /ruta/al/can_bus_log.jsonl
```

Los ficheros que deban seleccionarse mediante el selector de documentos de
Android se pueden copiar al emulador con:

```bash
./scripts/emulator.sh --replay /ruta/al/can_bus_log.jsonl --files /ruta/a/los/ficheros
```

Quedarán disponibles en `Downloads/A5-Cockpit` sin empaquetarlos en la
aplicación ni añadirlos a Git. Mientras el script siga ejecutándose, las
creaciones, sustituciones y eliminaciones posteriores se sincronizarán
automáticamente mediante ADB. Ambas opciones se pueden combinar.

Los replays, APK, capturas, claves y volcados del dispositivo están excluidos de
Git de forma intencionada.

## Configuración y secretos

El proyecto no incluye claves API. Las claves de IA y Places se introducen en
los ajustes de la aplicación y se guardan localmente. Nunca subas `.env`, una
firma, volcados del dispositivo o archivos de diagnóstico. Consulta
[SECURITY.md](SECURITY.md) antes de distribuir un APK: una clave de larga
duración incluida en una aplicación cliente nunca puede protegerse por completo
frente al propietario del dispositivo.

## Documentación y colaboración

El índice está en [docs/INDEX.md](docs/INDEX.md). Para colaborar, consulta
[CONTRIBUTING.md](CONTRIBUTING.md), especialmente las reglas sobre traducciones,
recursos de terceros y datos de prueba.

## Licencia

El código original se publica bajo [licencia MIT](LICENSE). Las dependencias,
datos cartográficos, marcas y recursos visuales de terceros conservan sus
propios términos; consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
