# Formato de Puntos de Interés

## Fuente GeoJSON

Cada fichero debe ser un `FeatureCollection` cuyos elementos sean geometrías
`Point`. Se usan las coordenadas GeoJSON habituales: longitud primero y latitud
después.

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "camera-001",
      "geometry": {
        "type": "Point",
        "coordinates": [-8.40795, 43.34854]
      },
      "properties": {
        "name": "Radar fijo",
        "category": "speed-camera"
      }
    }
  ]
}
```

`category` es el único enlace entre los datos y su presentación. Debe ser un
identificador en minúsculas. El icono y el pulso no se repiten en cada punto:
se resuelven mediante el catálogo común de categorías.

## Catálogo `categories.json`

La aplicación admite un único catálogo activo y lo reemplaza por completo al
importar otro fichero con el mismo nombre:

```json
{
  "schema": 1,
  "categories": {
    "speed-camera": {
      "icon": "speed-camera",
      "pulseEnabled": true,
      "pulseColor": "#FF3030"
    },
    "fuel-station": {
      "icon": "fuel-station",
      "pulseEnabled": false
    }
  }
}
```

`icon` puede apuntar a un PNG compartido por varias categorías. Si se omite,
usa el mismo identificador que la categoría. El pulso está desactivado por
defecto y su color predeterminado es rojo. El catálogo admite hasta 256
categorías y 256 KiB.

Para que el tamaño visual sea coherente con el cockpit, la medida recomendada
para cada icono es **64 × 64 píxeles**. El PNG debe aprovechar bien ese lienzo,
sin márgenes transparentes innecesarios. El límite de 512 × 512 indicado más
abajo es únicamente una protección del importador, no el tamaño de diseño
recomendado.

Si una categoría no está definida, el catálogo está ausente o el PNG indicado
no está instalado, se utiliza el marcador POI genérico sin pulso. El resto de
propiedades GeoJSON se conserva para futuros usos.

## Límites de seguridad

- 5 MiB y 10.000 puntos por fuente.
- 50.000 puntos combinados.
- Tamaño recomendado del icono: 64 × 64 píxeles.
- PNG de hasta 1 MiB y 512 × 512 píxeles; máximo de 64 iconos y 4 millones de
  píxeles combinados.
- Sólo nombres simples; no se aceptan rutas ni geometrías distintas de `Point`.

Importar otro fichero con el mismo nombre sustituye la versión anterior de
forma atómica. Desde Ajustes del Launcher se pueden añadir y eliminar fuentes,
el catálogo de categorías e iconos de forma independiente.

La relación de elementos instalados tiene desplazamiento propio para admitir
catálogos largos sin desplazar los controles de importación ni desbordar el
panel. Los resultados de cada importación se comunican mediante una
notificación flotante temporal; no se reserva una línea de estado dentro del
panel.

El selector admite tanto los MIME específicos de GeoJSON/JSON como
`application/octet-stream`. Algunos proveedores SAF y los ficheros copiados por
ADB asignan este último a la extensión `.geojson`; la aplicación mantiene la
validación estricta de nombre y contenido después de la selección.

## Añadir otra fuente

Un conversor sólo necesita generar el contrato anterior. No debe modificar la
aplicación ni introducir código dependiente del proveedor. Conviene incluir un
fixture sintético y validar el resultado con `jq` antes de importarlo.
