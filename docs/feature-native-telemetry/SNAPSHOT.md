# Snapshot analizado

- **Archivo:** `a5-system-analysis-20260809-194801.zip`
- **SHA-256:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **Tamaño:** 821.393.965 bytes comprimidos.
- **Contenido:** 139 entradas, 28 APK seleccionados y 78 objetos únicos.
- **Dispositivo:** Android 14/API 34, plataforma Qualcomm, placa
  `FIB-KSW-002`.

La copia local de trabajo está deduplicada bajo `.firmware-cache/`; no se
versiona. [SNAPSHOT.json](SNAPSHOT.json) y [APPS.json](APPS.json) permiten
verificar exactamente el origen de cada conclusión.

## Nodo principal detectado

`/dev/ttyHS1` existe con modo `0660`, UID 1000 y GID 1000. El inspector, como
APK ordinaria, no puede leerlo ni escribirlo. `/dev/ttyS9` no existe en esta
unidad. EventCenter selecciona `ttyHS1` para esta plataforma.
