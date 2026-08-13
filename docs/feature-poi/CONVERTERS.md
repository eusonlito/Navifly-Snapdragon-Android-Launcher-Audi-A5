# Conversores de Puntos de Interés

Los conversores externos deben producir GeoJSON compatible con
[POI.md](POI.md). Los catálogos generados deben mantenerse fuera del árbol
versionado y ningún resultado se distribuye dentro del APK.

El repositorio no incluye conversores ligados a proveedores concretos ni sus
catálogos. Cada usuario es responsable de comprobar la licencia y las
condiciones de uso de su fuente antes de obtener, transformar o redistribuir
datos.

Como sólo puede existir un `categories.json` activo, quien combine varias
fuentes debe fusionar sus definiciones en un único catálogo antes de importarlo.

## Nuevos conversores

Cada fuente adicional debería:

1. aceptar siempre un destino de salida explícito;
2. generar un `FeatureCollection` de geometrías `Point`;
3. validar coordenadas, cantidad de elementos y salida antes de reemplazarla;
4. escribir de forma atómica;
5. documentar procedencia, licencia y dependencias;
6. no introducir credenciales ni datos generados en Git.
