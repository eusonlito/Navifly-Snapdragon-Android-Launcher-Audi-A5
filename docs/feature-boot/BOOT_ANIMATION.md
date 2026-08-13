# Logo y animación de inicio

## Contrato del dispositivo

El Navifly Snapdragon 685 importa el logo y la animación por separado. Ambos
deben representar el mismo primer fotograma para que el cambio entre las dos
fases del arranque no sea visible.

| Propiedad | Valor |
|---|---|
| Área visible | `2400x896` |
| Identificador de FatSet | `2400_900` |
| Orientación | horizontal |
| Logo | `bootlogo.zip` |
| Animación | `bootanimation.zip` |
| Tamaño máximo de la animación | 100 MiB |

La fuente conserva siempre su proporción. Se escala hasta que una dimensión
alcanza el límite del lienzo y el espacio restante se completa con un color
uniforme. Nunca se estira ni se recorta.

## Generación

Instalar las herramientas necesarias en Debian o Ubuntu:

```bash
sudo apt install ffmpeg imagemagick zip unzip
```

Crear un directorio bajo `boot/` y guardar dentro el vídeo con el nombre exacto
`bootanimation.mp4`:

```text
boot/mi-animacion/
└── bootanimation.mp4
```

Ejecutar desde la raíz del repositorio:

```bash
./boot/scripts/generate.sh mi-animacion
```

El argumento sólo puede ser el nombre de una carpeta directa de `boot/`. El
script rechaza rutas absolutas, `..` y enlaces simbólicos que puedan salir de
ese directorio.

La salida queda junto al vídeo:

```text
boot/mi-animacion/
├── bootanimation.mp4
├── bootanimation.zip
├── bootlogo.zip
├── bootanimation.gif
└── bootlogo.png
```

Los dos últimos ficheros son previsualizaciones. Sólo los ZIP se importan en el
dispositivo.

## Tratamiento visual

- El lienzo final mide exactamente `2400x896`.
- El vídeo se escala proporcionalmente mediante *contain* y queda centrado.
- El fondo se calcula una sola vez a partir de los bordes de cinco fotogramas
  repartidos por el vídeo. Usar un único color evita parpadeos entre frames.
- La animación conserva la cadencia nominal redondeada al entero más cercano,
  con un máximo de 30 FPS.
- La previsualización GIF mide `1200x448`, usa como máximo 12 FPS y se repite.
- Se elimina el audio.
- Los frames del paquete son PNG completos y opacos. Se genera una única paleta
  para toda la secuencia con el fin de reducir tamaño sin variar el color entre
  fotogramas.

Los cuatro resultados se construyen y validan en un directorio temporal. Un
fallo no sustituye las salidas válidas que ya existan.

## `bootanimation.zip`

La estructura es:

```text
bootanimation.zip
├── desc.txt
└── part0/
    ├── frame-00000.png
    ├── frame-00001.png
    └── ...
```

`desc.txt` contiene la cadencia obtenida del vídeo y termina con una línea
vacía:

```text
2400 896 24
p 0 0 part0

```

La secuencia se repite hasta que Android termina el arranque. Todas las entradas
del ZIP usan el método `store`; no existe una carpeta contenedora adicional.

## `bootlogo.zip`

FatSet utiliza el identificador lógico `2400_900`, aunque el bitmap conserva la
resolución visible de 2400×896:

```text
bootlogo.zip
└── 2400_900/
    └── logo_customer1.bmp
```

El bitmap es Windows BMP3, RGB de 24 bits, sin compresión ni transparencia. Se
genera desde `frame-00000.png`; por tanto, coincide con el comienzo de la
animación. El ZIP también utiliza `store`.

## Verificación automática

El test crea un vídeo 4:3 sintético y comprueba la composición con bandas, los
formatos, las dimensiones, la estructura ZIP, el método `store` y la sustitución
atómica ante un vídeo inválido:

```bash
./boot/scripts/test-generate.sh
```

También se pueden inspeccionar los paquetes manualmente:

```bash
unzip -t boot/mi-animacion/bootanimation.zip
unzip -t boot/mi-animacion/bootlogo.zip
zipinfo -l boot/mi-animacion/bootanimation.zip | head
unzip -p boot/mi-animacion/bootlogo.zip \
  2400_900/logo_customer1.bmp > /tmp/logo_customer1.bmp
file /tmp/logo_customer1.bmp
```

## Instalación y recuperación

1. Copiar los dos ZIP a la ubicación que permita seleccionar el importador del
   dispositivo.
2. Importar primero `bootanimation.zip` y reiniciar para comprobarlo.
3. Importar después `bootlogo.zip` y seleccionar el nuevo logo en FatSet.
4. Mantener disponible la opción de recuperación del dispositivo.

Una animación inválida suele producir una fase de pantalla negra, pero el logo
actúa antes de Android y conlleva más riesgo. No apagar el equipo durante una
importación.

## Contenido versionado

El repositorio conserva únicamente:

- `boot/default/`, ejemplo reproducible y listo para importar.
- `boot/scripts/`, generador y prueba.

Las demás carpetas de `boot/` son trabajo local y no deben publicarse.
