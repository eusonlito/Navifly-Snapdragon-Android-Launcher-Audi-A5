# Flujo de análisis con consumo mínimo

## Regla principal

El identificador de todo artefacto es su SHA-256. Si el hash ya existe y tiene
marcador `COMPLETE`, no se vuelve a extraer ni decompilar. Antes de abrir código,
se consulta la ficha `APP-<paquete>.md` y `QUEUE.md`.

## Herramienta

Desde `a5-launcher`:

```bash
python3 tools/firmware-analysis/firmware_analysis.py ingest /ruta/captura.zip
python3 tools/firmware-analysis/firmware_analysis.py index
python3 tools/firmware-analysis/firmware_analysis.py status
python3 tools/firmware-analysis/firmware_analysis.py decompile com.szchoiceway.eventcenter
python3 tools/firmware-analysis/firmware_analysis.py search com.szchoiceway.eventcenter PATTERN
python3 tools/firmware-analysis/firmware_analysis.py verify-docs
```

La caché local está en `.firmware-cache/` y no entra en Git. La ingesta valida
CRC, deduplica objetos y sólo publica el snapshot al terminar. `index` conserva
las secciones manuales de cada ficha y actualiza únicamente su bloque generado.

## Orden de lectura

1. `README.md`, `ARCHITECTURE.md`, `PROTOCOL.md` y `QUEUE.md`.
2. Ficha de la aplicación concreta.
3. Búsqueda acotada mediante la herramienta; nunca volcar árboles completos al
   contexto.
4. Leer sólo ventanas pequeñas alrededor de coincidencias.
5. Registrar inmediatamente evidencia, interpretación y confianza en la ficha.

## Nueva captura

Ingerirla, comparar hashes y analizar únicamente APK nuevos o modificados. Si un
hash no cambia, sus conclusiones siguen vigentes. Los informes del dispositivo
se comparan por archivo, no se reabren APK sin una pregunta nueva concreta.

## Capturas de LogCapture

Los ZIP de `AndroidLogcat` son acumulativos porque cada ejecución vuelca el
anillo completo de `logcat`. Para no procesar varias veces los mismos megabytes:

1. catalogar una vez nombre, tamaño y SHA-256 en `SYSTEM-LOGS.md`;
2. elegir el ZIP más reciente que todavía contenga todas las marcas horarias de
   las sesiones objeto de estudio;
3. extraer únicamente su fichero `.log`; los ANR históricos repetidos no se
   analizan para telemetría;
4. filtrar primero por ventana temporal y por `SerialPortData`, `EventService`
   o el identificador concreto buscado;
5. resumir comandos, variantes y rangos numéricos; nunca incorporar el log
   completo al contexto;
6. abrir otro ZIP sólo si falta una ventana o el anillo ya la sobrescribió.

Los ficheros `session.txt` aportan el instante exacto de inicio. Cada ventana
dura aproximadamente 15 segundos y se correlaciona con el ZIP que lleva esa
misma hora en el nombre.
