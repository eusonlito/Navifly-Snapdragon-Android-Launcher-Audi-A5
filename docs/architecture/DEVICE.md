# Dispositivo Navifly de referencia

## Sistema

```text
Android Version: 14
API Level: 34
Security Patch Level: 2023-11-01
Bootloader: unknown
Build ID: QCOM 685-GT7HPro-GL-user-20260330.162110
Java VM: ART 2.1.0
OpenGL ES: 3.2
Kernel Architecture: aarch64
Kernel Version: 4.19.157-perf (20260330.162110)
Root Access: No
Google Play Services: 26.26.34 (190400-945364269)
```

## Dispositivo

```text
Model: GT7HPro-CAR (GT7HPro-CAR)
Manufacturer: QUALCOMM
Brand: GT7HPro-CAR
Board: FIB-KSW-002
Hardware: qcom
Screen Size: 8,00 inches
Screen Resolution: 2400 x 896 pixels
Screen Density: 320 dpi
Total RAM: 7690 MB
Available RAM: 4827 MB (62%)
Internal Storage: 211,48 GB
```

## SoC

```text
Qualcomm Snapdragon 685
2,80 GHz

Model: Qualcomm Technologies, Inc KHAJE
Cores: 8
big.LITTLE: HMP (2 clusters)
Architecture: Kryo 260
Topology:
  4x Qualcomm Kryo 280 LP @ 1,90 GHz
  4x ARM Cortex-A73 @ 2,80 GHz
Revision: r10p4
Process: 6 nm
```

## Implicaciones para la interfaz

La superficie útil observada en las capturas es `2400 x 896` y Android informa
`320 dpi` (`density = 2`). En este panel de 8 pulgadas, las bandas anteriores de
48 dp medían unos 96 píxeles, aproximadamente 7,6 mm físicos, y sus textos de
7–14 sp resultaban demasiado pequeños desde la posición de conducción.

La interfaz se calibra por altura física renderizada:

* cabecera y pie: `14,5 %` de la altura útil, unos 130 px;
* botones superiores: `9,5 %`, unos 85 px;
* texto principal de cabecera: unos 36 px;
* labels y valores inferiores: unos 22 px y 34 px respectivamente.

Las esferas continúan limitadas por el espacio central y mantienen su escala
responsive.

`scripts/emulator.sh` reproduce este perfil con `-skin 2400x896`,
`-dpi-device 320` y los overrides Android `wm size 2400x896` y
`wm density 320`. El script sólo aplica estos cambios a un serial
`emulator-*`, nunca a un dispositivo físico conectado.
