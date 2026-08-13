# Capturas privilegiadas de LogCapture

Este documento evita reanalizar los ZIP acumulativos de `AndroidLogcat`. Los
resultados siguientes se obtuvieron leyendo como fuente principal únicamente
`AndroidLogcat-2026-08-10-22.10.52.zip`; su anillo contiene las once ventanas
entre las 22:06:11 y las 22:11:07. Los demás ZIP se conservan como respaldo.

## Captura controlada de crucero

El conductor confirma que activó el control de crucero durante
`AndroidLogcat-2026-08-10-20.59.12.zip`, SHA-256
`11034e5bed9631c079b7d852ac4ac8649bfa91f09f3b468cfb18fd7f5cbf5a40`.
La ventana exacta es 20:59:12–20:59:27 y contiene:

- 111 recepciones `00/A1/19`;
- 26 recepciones `00/A1/14`, 11 de `00/A1/15` y una de `00/A1/16`,
  correspondientes a PDC y marcha atrás;
- una recepción `00/11/01`;
- ningún otro comando recibido o enviado por `SerialPortData`;
- ninguna traza textual de crucero en EventCenter.

En las 111 tramas `19`, autonomía, consumo, velocidad media, byte sin asignar y
unidades permanecen a cero. Sólo varían velocidad (0–3 km/h) y RPM; combustible,
temperatura y odómetro permanecen estables. Por tanto, activar el control de
crucero no produce un mensaje UART ni modifica un campo de la trama periódica.
La vía EventCenter/MCU queda descartada para conocer su estado o velocidad
configurada con el protocolo actual.

## Lote del 10-08-2026

| Inicio | Tamaño | SHA-256 |
|---|---:|---|
| 22:06:11 | 1.281.341 | `ae8b309169b3054753c3311aac8932992c916ec667e3b4889c2ee284126fab91` |
| 22:06:35 | 1.310.799 | `c43e43ce9739944b640be2d1650c1e8ddf8e18623853bb3c4bd8fb930e943fdf` |
| 22:06:53 | 1.321.543 | `c073a64a1e18340f1a907a96e7e20a80b6f6e3b70ca08eda0d7e62484f2c9578` |
| 22:07:10 | 1.338.461 | `5c6395bef24a04bef1479ecfda39732a4584b65bb5895c62caab2f139adef4d2` |
| 22:07:26 | 1.353.089 | `b38a5f074b03620fd9226cc75bfec7b38df35864580df203a119e036d1d092cd` |
| 22:08:01 | 1.390.025 | `972a37cd427625220d61bf8b923099763a824667e28e61b8acd5a59fa945a08f` |
| 22:08:17 | 1.405.237 | `94c4218f43973f0ae0e3a404d1fb2601253bd79b648fe8b48202c740f92a4e03` |
| 22:08:35 | 1.423.971 | `41ff4a93e6b70f5107b3f9047b7f67f68cc27c079e9165b8ecc785fd3a3f65d7` |
| 22:08:52 | 1.443.358 | `08916cc2b9c3010f90cf7cf0b45d0cab4d656612d8fe6184f8e43a326d9a1009` |
| 22:10:11 | 1.493.923 | `ce3413b927d680f18188d9c2781e7955ebac88edef5d2aac03c44cd0b684f6b9` |
| 22:10:52 | 1.517.602 | `daa1be7ab6fb1e2040a1735437b9bbf38eed1f9fb83adb2e4c9d316841060523` |

## Resumen por ventana

| Inicio | Tramas relevantes | Estado observado |
|---|---|---|
| 22:06:11 | `10`, `15`, `16`, `19` | Estado inmediato, PDC trasero y entrada en marcha atrás; 0–4 km/h. |
| 22:06:35 | `14`, `15`, `16`, `19` | PDC delantero/trasero y entrada/salida de marcha atrás; 0–9 km/h. |
| 22:06:53 | `16`, `19` | Entrada en marcha atrás; 0–1 km/h. |
| 22:07:10 | `19` | Telemetría periódica; 0–2 km/h. |
| 22:07:26 | `14`, `15`, `16`, `19` | PDC delantero/trasero y entrada en marcha atrás; 0–6 km/h. |
| 22:08:01 | `19` | Conducción; 2–43 km/h y 849–2.115 RPM. |
| 22:08:17 | `19` | Conducción; 38–51 km/h y 861–1.781 RPM. |
| 22:08:35 | `10`, `19` | Conducción; 21–33 km/h y estado inmediato sin transición visible. |
| 22:08:52 | `10`, `19` | Conducción y parada; 0–32 km/h. |
| 22:10:11 | `14`, `15`, `16`, `19` | PDC y entrada en marcha atrás; 0–4 km/h. |
| 22:10:52 | `12`, `1C` | Puerta delantera izquierda y climatización; motor/telemetría periódica ausentes. |

## Conclusiones reutilizables

- El combustible permanece en 24 litros y el odómetro pasa de 221.593 a
  221.594 km.
- En todas las muestras `00/A1/19`, autonomía anunciada, consumo anunciado y
  velocidad media valen cero.
- Durante las cuatro ventanas de conducción sólo cambian los bytes ya
  atribuidos a velocidad, RPM y temperatura. El byte sin asignar de `19`
  permanece a cero; no hay un bit oculto de control de crucero en esa trama.
- La captura controlada de las 20:59 confirma que activar el control de crucero
  no genera ningún subcomando ni cambio de campo visible para EventCenter.
- `14`, `15`, `16` y `1C` corresponden a PDC, cámara/marcha atrás y clima. Se
  conservan como conocimiento del protocolo, pero están fuera del alcance del
  launcher por decisión de producto.
