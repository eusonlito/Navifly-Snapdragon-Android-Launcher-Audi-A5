# Capturas reproducibles del launcher

Las capturas de presentación no deben depender del instante del replay real ni
introducir valores prefijados en la aplicación. El script
`scripts/capture-emulator.sh` crea una copia temporal del APK debug, sustituye
sólo el asset de replay dentro de esa copia, la vuelve a firmar e instala esa
variante exclusivamente en el emulador.

No modifica código, preferencias de producción, el JSONL original ni el APK
release. La variante release continúa sin incluir ningún replay.

## Escena canónica

| Parámetro | Valor |
|---|---|
| Resolución | `2400x896 @ 320 dpi` |
| Coordenadas | `43.348538, -8.407951` |
| Mapa | Siempre `LIGHT`, estilo `POSITRON` |
| Velocidad | `80 km/h` |
| RPM | `1840` |
| Marcha esperada | `5` |
| Consumo calculado esperado | `6,3` |
| Combustible | `56` |
| Odómetro | `220.024` |
| Temperatura exterior | `24,5 °C` |

La velocidad nunca es cero, las RPM están por encima del umbral de cálculo y la
marcha no es `N`. Consumo, autonomía y odómetro se obtienen por los mismos
caminos que utiliza la aplicación; el script sólo inyecta la telemetría de la
escena temporal.

## Uso

### Flujo de validación visual interactiva

La sesión de desarrollo dispone de display local. Para cambios visuales se
debe lanzar el emulador en modo gráfico con `./scripts/emulator.sh --replay RUTA_JSONL` y mantenerlo
abierto para que el usuario revise directamente el resultado. La tarea no se
considera cerrada y no se genera el APK de producción hasta recibir su
validación visual explícita.

El script automatizado descrito a continuación sigue siendo útil para capturas
reproducibles y comprobaciones mecánicas, pero no sustituye esta revisión en el
emulador visible.

Primero debe existir el APK debug correspondiente al código que se desea
capturar:

```bash
./gradlew assembleDebug
```

Después:

```bash
./scripts/capture-emulator.sh \
  --output screenshots/mi-captura-2400x896.png
```

El script arranca un emulador headless si no existe uno, espera la carga del
mapa, realiza la captura, comprueba sus dimensiones y detiene únicamente el
emulador que él mismo haya iniciado.

Por defecto también desactiva el modo avión, habilita Wi-Fi y espera a que
Android confirme una red `VALIDATED`. Si no la consigue, falla en lugar de
generar accidentalmente una captura con el aviso «Sin conexión». El modo sin red
se solicita expresamente con `--offline` y queda reservado para comprobar la
recuperación de caché y los estados degradados.

## Escenas alternativas

```bash
./scripts/capture-emulator.sh \
  --speed 36 \
  --rpm 1390 \
  --fuel 42 \
  --odometer 220120 \
  --settle 15 \
  --output screenshots/escena-36kmh.png
```

Las combinaciones de velocidad y RPM deben corresponder a una relación válida
del estimador para evitar una marcha transitoria o `N`. El consumo mostrado se
calcula en ejecución a partir de ambas lecturas; no es un texto prefijado.

Opciones disponibles:

```bash
./scripts/capture-emulator.sh --help
```

## Garantías

- El APK base no se modifica.
- El asset JSONL original no se modifica.
- El APK temporal se elimina al terminar.
- El mapa se fuerza a claro sólo mediante preferencias de la instalación del
  emulador.
- La ubicación se suministra mediante el GPS emulado.
- La escena normal exige conectividad validada; `--offline` es una excepción
  explícita.
- La captura se rechaza si no mide exactamente `2400x896`.
- No existe ningún valor de demostración en la aplicación instalada en el coche.
