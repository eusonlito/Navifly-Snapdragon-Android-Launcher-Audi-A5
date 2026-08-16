# A5 Launcher

A5 Launcher es un sustituto de la pantalla de inicio de Android creado para una
unidad NaviFly concreta instalada en un Audi A5. La aplicación instalada se
muestra como **A5 Cockpit** y reúne en una sola interfaz el cuadro de
instrumentos, la telemetría del vehículo, el mapa, los datos del viaje, las
aplicaciones y un Asistente IA opcional.

> Es un proyecto personal e independiente. No está afiliado ni respaldado por
> Audi, Volkswagen Group, Waze, Google, OpenAI, ChoiceWay, NaviFly ni por ningún
> otro fabricante de vehículos o software.

[Read in English](README.md)

## Compatibilidad: Lee Esto Antes de Instalar

Esta aplicación **no es un launcher Android universal**. Está diseñada,
calibrada y validada exclusivamente para esta combinación:

- [NaviFly Snapdragon 685 Newest Android System 8+256G 2K para Audi A4/A5](https://www.alibaba.com/product-detail/NaviFly-Snapdragon-685-Newest-Android-System_11000030157012.html).
- Android 14 y arquitectura ARM64.
- Pantalla ultra panorámica con área visible exacta de `2400×896` a 320 dpi.
- Aplicaciones propietarias ChoiceWay/NaviFly, especialmente `EventCenter`,
  `Settings` y `FatSet`.
- Comunicación CAN y cambio al MMI original mediante las interfaces internas de
  ese firmware.
- Configuración de desarrollo: Audi A5 2.0 TDI 150 CV manual de 2015, MMI 3G
  Basic y protocolo CAN `3G` del dispositivo.

La página del vendedor anuncia compatibilidad física con Audi A4 2008–2014 y
Audi A5 2008–2016, pero eso **no garantiza que A5 Cockpit funcione en todas esas
combinaciones**. Un firmware, caja CAN, protocolo, resolución o relación de
aspecto diferente puede impedir la telemetría, descolocar la interfaz o dejar
sin funcionamiento las acciones del MMI.

No instales esta versión esperando compatibilidad con:

- otras radios Android, incluso aunque también utilicen Snapdragon 685;
- Android Auto o CarPlay como si fuera una aplicación convencional;
- otra resolución, densidad o relación de aspecto;
- otros modelos de coche, motores, transmisiones, cajas CAN o firmware;
- una unidad que no incluya las aplicaciones propietarias de ChoiceWay.

## Funcionalidades Principales

- Velocidad, revoluciones, marcha estimada y testigos del vehículo.
- Tiempo, viaje, consumo estimado, distancia desde el último repostaje,
  autonomía, combustible y odómetro.
- Mapa vectorial MapLibre con modo claro/oscuro, caché local, seguimiento,
  rotación y controles táctiles.
- Puntos de interés GeoJSON importables, con categorías, iconos y pulsos
  configurables.
- Marcadores POI de radares suministrados por ficheros locales del usuario.
- Barra superior y bloques de viaje reordenables manteniendo pulsado.
- Lanzador de aplicaciones, cambio de aplicación, acceso a ajustes y MMI Audi.
- Asistente IA opcional mediante OpenAI o Gemini, con Google Places para
  búsquedas cercanas y navegación mediante Waze.
- Actualización del propio APK desde el selector de documentos de Android.
- Herramientas de diagnóstico y exportación de logs del mapa y del Asistente IA.

El consumo, la autonomía y la marcha son estimaciones construidas con los datos
disponibles del firmware. No sustituyen los indicadores originales del coche.

## Capturas

![Dashboard de A5 Launcher](docs/screenshots/dashboard.png)

| Aplicaciones | Ajustes de Mapa y POI |
|---|---|
| ![Aplicaciones](docs/screenshots/applications.png) | ![Ajustes de Mapa y POI](docs/screenshots/settings-map.png) |

| Ajustes del Asistente IA | Ajustes del Sistema |
|---|---|
| ![Ajustes del Asistente IA](docs/screenshots/settings-ai.png) | ![Ajustes del Sistema](docs/screenshots/settings-system.png) |

## Instalación Para Usuarios

### 1. Conseguir el APK

Descarga `A5Cockpit.apk` desde la publicación de GitHub correspondiente, si esa
versión incluye un APK. Si sólo aparece el código fuente, el APK debe compilarse
siguiendo la sección [Compilación](#compilación).

Instala únicamente un APK obtenido de este repositorio o de una persona en la
que confíes. Android sólo permite actualizar una instalación existente con un
APK que tenga el mismo identificador de aplicación y la misma firma.

### 2. Llevar el APK al NaviFly

Se puede utilizar cualquiera de estos métodos:

- descargar el APK directamente desde el navegador del dispositivo;
- copiarlo a `Downloads` mediante una memoria USB;
- enviarlo a una carpeta compartida de Dropbox y descargarlo con la aplicación
  oficial de Dropbox;
- copiarlo por ADB desde un ordenador.

Dropbox es sólo una recomendación práctica para compartir actualizaciones; no
es una dependencia de A5 Cockpit. Si Dropbox no aparece en el selector de
documentos, descarga primero el fichero a `Downloads`.

### 3. Permitir la Instalación Inicial

1. Abre el APK desde Dropbox, el navegador o el gestor de archivos.
2. Si Android bloquea la instalación, pulsa **Ajustes** en el aviso.
3. Activa **Permitir Desde Esta Fuente** para la aplicación que ha abierto el
   APK.
4. Vuelve atrás, confirma **Instalar** y abre A5 Cockpit.

No hace falta activar orígenes desconocidos globalmente ni deshabilitar ninguna
protección del sistema.

### 4. Elegir A5 Cockpit Como Launcher

1. Pulsa el botón físico o táctil **Home** del NaviFly.
2. Selecciona **A5 Cockpit** en la lista de aplicaciones de inicio.
3. Elige **Siempre**, no **Sólo Una Vez**.

Si Android no muestra la pregunta, abre los ajustes del dispositivo y busca una
ruta equivalente a **Aplicaciones > Aplicaciones Predeterminadas > Aplicación de
Inicio**. El nombre exacto puede cambiar entre versiones del firmware.

Después, pulsa Home varias veces y reinicia el NaviFly para comprobar que vuelve
siempre a A5 Cockpit. No desinstales ni deshabilites Quickstep, el launcher
original o las aplicaciones de sistema: son una vía de recuperación.

## Permisos y Ajustes Necesarios

Android solicita cada permiso cuando se utiliza por primera vez. No hace falta
conceder permisos de almacenamiento generales: los APK, POI y logs utilizan el
selector seguro de documentos de Android.

| Permiso o ajuste | Cuándo es necesario | Cómo activarlo |
|---|---|---|
| **Ubicación precisa** | Seguimiento y rotación del mapa, estado GPS y búsquedas cercanas | Acepta el aviso al abrir el dashboard y elige ubicación precisa mientras se usa la aplicación. |
| **Micrófono** | Sólo para el Asistente IA | Activa un proveedor y pulsa el icono del Asistente IA; Android mostrará el permiso. |
| **Servicio de accesibilidad “Aplicaciones recientes de A5 Cockpit”** | Sólo para abrir el selector nativo de aplicaciones recientes | Pulsa una vez el botón de cambio de aplicación; se abrirán los ajustes de accesibilidad. Activa únicamente ese servicio. |
| **Instalar Aplicaciones Desconocidas para A5 Cockpit** | Sólo para actualizar desde los ajustes del launcher | Se solicita automáticamente durante la primera actualización. |
| **Aplicación de inicio predeterminada** | Para que Home y el arranque utilicen A5 Cockpit | Selecciona A5 Cockpit como aplicación de inicio y confirma **Siempre**. |

El acceso a Internet y el servicio de telemetría están declarados por la
aplicación y no muestran un diálogo de permiso. La telemetría depende de
`com.szchoiceway.eventcenter`; no existe un permiso manual que pueda sustituir
esa aplicación o hacer compatible otro firmware.

Si el firmware incluye un gestor de batería y detiene la telemetría cuando Waze
está en primer plano, configura A5 Cockpit como **Sin Restricciones**. Hazlo sólo
si observas ese problema: no es necesario en la configuración de referencia.

## Primera Configuración

1. Abre **Ajustes del Launcher > Mapa** y selecciona color, estilo y tamaño
   máximo de caché.
2. Comprueba que Mapa, Red y GPS muestran un estado válido. El dashboard sigue
   funcionando aunque el mapa no tenga cobertura.
3. Abre **Points of Interest** para importar:
   - uno o varios catálogos `.geojson`;
   - `categories.json`, con el icono, pulso y color de cada categoría;
   - iconos PNG de 64×64 píxeles, que es el tamaño visual recomendado.
4. En **Asistente IA**, selecciona Desactivado, OpenAI o Google Gemini. Las
   claves de OpenAI, Gemini y Google Places se guardan y validan por separado.
   El icono del Asistente IA no aparece en el dashboard mientras esté
   desactivado.
5. Mantén pulsado un icono superior o un bloque inferior para reordenarlo.

Las claves API son opcionales y no están incluidas en el repositorio.

## Actualización Mediante el Propio Launcher

La forma más cómoda es mantener una carpeta compartida de Dropbox que contenga
el último `A5Cockpit.apk`:

1. Copia el nuevo APK a Dropbox desde el ordenador y espera a que se sincronice.
2. En el coche, abre **Ajustes del Launcher > Sistema > Actualización**.
3. Pulsa **Actualizar**.
4. En el selector de Android, abre Dropbox y selecciona el APK. También puedes
   elegir un APK previamente descargado en `Downloads`.
5. La primera vez, Android pedirá permitir que A5 Cockpit instale aplicaciones.
   Activa **Permitir Desde Esta Fuente**, vuelve y continúa.
6. Confirma la instalación en la pantalla del instalador de Android.

Se puede cancelar sin elegir un fichero usando **Atrás**. La aplicación comprueba
que el APK pertenece a A5 Cockpit; Android comprueba además su firma. Una
actualización normal conserva ajustes, claves, caché y datos locales.

Si Android muestra **Aplicación No Instalada** o un conflicto de paquete, la
causa más habitual es que el APK esté firmado con una clave diferente. No
desinstales la versión funcional sin haber guardado una vía de recuperación:
desinstalar también elimina los datos locales de la aplicación.

## Logo y Animación de Arranque

El dispositivo utiliza dos paquetes independientes:

- `bootanimation.zip`: animación mostrada mientras arranca Android;
- `bootlogo.zip`: imagen estática mostrada antes de la animación.

El ejemplo listo para importar está en [`boot/default`](boot/default). Ambos
paquetes están preparados para `2400×896` y comparten el mismo primer fotograma.

### Importación Recomendada

1. Conserva una copia de los paquetes originales y confirma que puedes volver a
   abrir la herramienta de recuperación/FatSet.
2. Copia los ZIP al almacenamiento que pueda leer el importador del firmware.
   Dropbox puede servir para transferirlos, pero algunos importadores sólo
   muestran almacenamiento local o USB.
3. Abre la aplicación de administración **FatSet** del NaviFly.
4. Importa primero `bootanimation.zip` y reinicia para comprobarlo.
5. Sólo si Android arranca correctamente, importa `bootlogo.zip`, selecciona el
   nuevo logo y vuelve a reiniciar.
6. No apagues el equipo ni cortes la alimentación durante la importación.

Una animación inválida normalmente deja esa fase en negro, pero un logo inválido
actúa antes y entraña más riesgo. Este repositorio **no contiene ni modifica el
bootloader real, el MCU ni las particiones de arranque**. No flashees un fichero
de este proyecto como bootloader. El nombre correcto del segundo paquete es
`bootlogo.zip`.

Para crear una animación propia a partir de un vídeo, consulta la guía completa
de [Logo y Animación de Inicio](docs/feature-boot/BOOT_ANIMATION.md). El generador
mantiene las proporciones, crea ambos ZIP y genera un GIF y un PNG de
previsualización.

## Solución de Problemas

### No Aparece Telemetría

- Comprueba que se trata del modelo y firmware indicados en Compatibilidad.
- Verifica que `EventCenter` sigue instalado y que el protocolo CAN seleccionado
  en los ajustes de fábrica es el correcto para el vehículo.
- Reinicia el dispositivo. Conceder permisos Android adicionales no sustituye
  la comunicación propietaria de ChoiceWay.

### El Mapa No Sigue la Posición

- Concede ubicación precisa y activa la ubicación/GPS del dispositivo.
- Pulsa el botón de centrar después de mover o ampliar el mapa.
- Sin Internet se mostrarán los datos ya presentes en la caché; una zona nunca
  cargada necesita conexión.

### El Botón de Aplicaciones Recientes No Funciona

Activa el servicio **Aplicaciones recientes de A5 Cockpit** en los ajustes de
accesibilidad. No es necesario activar ningún otro servicio.

### El Asistente IA No Aparece

Abre **Ajustes del Launcher > Asistente IA**, selecciona un proveedor, guarda una
clave válida y concede el micrófono. Google Places sólo es necesario para
búsquedas como “la gasolinera más cercana”.

### El Dispositivo Arranca Con Otro Launcher

Pulsa Home, vuelve a elegir A5 Cockpit y confirma **Siempre**. Comprueba también
la aplicación de inicio en los ajustes del sistema. No deshabilites Quickstep ni
aplicaciones del firmware para forzar el arranque.

## Compilación

Requisitos para desarrolladores:

- JDK 21;
- Android SDK Platform 37;
- Android SDK Build Tools compatible con Android Gradle Plugin 9.3.1;
- sistema de compilación de 64 bits.

El wrapper de Gradle está incluido:

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
./scripts/compile.sh
```

`scripts/compile.sh` ejecuta pruebas, Lint y la compilación release optimizada.
Guarda el APK, su SHA-256 y el mapping de R8 —si existe— en `out/`.

La configuración pública firma actualmente el release con la clave debug
estándar para instalación local. Quien distribuya actualizaciones debe utilizar
una clave privada estable, guardarla fuera del repositorio y firmar siempre con
la misma identidad.

### Emulador y Replay

```bash
python3 -m pip install -r requirements-emulator.txt
./scripts/emulator.sh --replay /ruta/al/can_bus_log.jsonl
```

Para sincronizar ficheros seleccionables por Android:

```bash
./scripts/emulator.sh \
  --replay /ruta/al/can_bus_log.jsonl \
  --files /ruta/a/los/ficheros
```

Quedarán en `Downloads/A5-Cockpit` y los cambios posteriores se sincronizarán
mediante ADB mientras el script continúe ejecutándose.

## Seguridad, Documentación y Colaboración

El proyecto no incluye claves API. Nunca publiques `.env`, firmas, volcados del
dispositivo, APK privados ni archivos de diagnóstico. Consulta [SECURITY.md](SECURITY.md)
antes de distribuir un APK.

El índice técnico está en [docs/INDEX.md](docs/INDEX.md). Para colaborar,
consulta [CONTRIBUTING.md](CONTRIBUTING.md), especialmente las reglas sobre
traducciones, recursos y datos de prueba.

## Licencia

El código original se publica bajo [licencia MIT](LICENSE). Las dependencias,
datos cartográficos, marcas y recursos visuales de terceros conservan sus
propios términos; consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
