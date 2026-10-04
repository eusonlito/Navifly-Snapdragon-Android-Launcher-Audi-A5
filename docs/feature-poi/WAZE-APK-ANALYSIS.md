# Análisis local del APK de Waze 5.22.90.123

Este apunte conserva los resultados de inspeccionar el APK local para evitar
repetir la extracción. Está basado exclusivamente en el APK y en el código de
este repositorio; no contiene una prueba de llamadas contra los servidores.

## Artefacto y reproducción

- APK: `local/waze-5-22-90-123.apk`
- Paquete/versión según el manifiesto: `com.waze`, `5.22.90.123`
- SHA-256: `a45040acce4dbf79c1445a76415b8e3457dd8a720d373f81c8a98c5529f08c80`
- JADX: `1.5.5`
- Salida conservada (ignorada por Git): `local/waze-5-22-90-123-jadx/`
- Repetición: `scripts/decompile-waze-apk.sh [APK [DIRECTORIO_SALIDA]]`

La decompilación recorrió 34 505 unidades y dejó 39 423 fuentes Java y 6 997
recursos. JADX terminó con 122 errores de recuperación. La salida permite
buscar código y constantes, pero no debe tomarse como una reconstrucción exacta
del comportamiento: parte del código de red está en `libwaze.so` (aprox. 73 MB
para `arm64-v8a`) y la decompilación de DEX también reportó errores.

## Hallazgos de red relevantes

En `sources/com/waze/config/ConfigValues.java`, la constante
`CONFIG_VALUE_GEO_CONFIG_WEB_SERVICE_ADDRESS` referencia una lambda de
configuración que devuelve `https://rt.waze.com/rtserver/distrib`
(`lambda$static$63`). La misma clase contiene una dirección de staging,
`https://rt.gcp.wazestg.com:443/rtserver` (`lambda$static$64`). El APK declara
además permisos de Internet y ubicación.

Esto identifica el endpoint de distribución en tiempo real configurado por el
cliente Waze. La inspección estática no revela aquí un contrato HTTP público,
parámetros de consulta suficientes, un esquema de respuesta consumible por otras
apps ni que la llamada sea anónima. La ruta aparece como configuración del
cliente; el APK no basta para concluir que pueda consultarse directamente sin
sesión, identificadores del cliente u otros datos que construya el código
nativo. No se hizo una petición de red.

El límite nativo también aparece en las alertas: `RtAlertsNativeManager` declara
`getRtAlertsOnRouteNTV()`, y `C18657z.getRtAlertsOnRoute()` procesa los bytes
devueltos con `RtAlertItemList.parser()`. El mensaje generado `RtAlertItem`
contiene identificador, tipo, título, icono, latitud, longitud, marca temporal
y descripción. Esto confirma que la app maneja elementos que podrían
representarse en el mapa, pero el puente JNI no identifica qué llamada de red
los alimenta ni establece que la respuesta HTTP de `rtserver/distrib` sea ese
protobuf.

El APK también define opciones de mapa para mostrar cámaras de velocidad,
cámaras de semáforo, accidentes y carriles bloqueados. Son preferencias/capas
del cliente; su presencia no demuestra que todos esos elementos se entreguen
por `rtserver/distrib` ni que ese servicio proporcione una respuesta integral
para una región.

## Encaje con el mapa del launcher

El launcher puede representar esos objetos si se obtienen en un formato que se
pueda convertir a GeoJSON: `PoiGeoJsonParser` valida `FeatureCollection` con
geometrías `Point`, y `CockpitMap.kt` las carga en una fuente `GeoJsonSource` de
MapLibre. Los campos de categoría y estilo ya pueden elegir icono y animación.

La tubería actual solo importa ficheros GeoJSON elegidos por el usuario y los
guarda en `filesDir/poi`; no tiene cliente remoto, refresco temporal ni modelo
de vigencia de eventos. Los límites locales son 5 MiB por fuente, 10 000 puntos
por fuente y 50 000 puntos en total. Para un feed autorizado en un formato
estable, el encaje visual es directo mediante un adaptador que convierta cada
evento a `Feature` Point y actualice una fuente dinámica. Mantener alertas
activas/expiradas y distinguir radares fijos de eventos temporales requiere
lógica adicional.

## Conclusión de viabilidad

- **Pintar eventos en el mapa:** sí, técnicamente, si hay un origen de datos
  utilizable y coordenadas convertibles a GeoJSON.
- **Usar `rtserver/distrib` como API externa:** el APK confirma que el cliente
  lo configura, pero no aporta evidencia suficiente para afirmar que se pueda
  consumir de forma anónima o estable desde el launcher. La respuesta,
  autenticación y compatibilidad externa quedan sin validar.
- **Sustituir la gestión interna de POIs usando solo este APK:** no queda
  validado. Extraer la URL no aporta un contrato de datos externo. El sistema
  actual puede seguir siendo la capa de dibujo mientras una fuente remota
  autorizada se resuelve; retirar la importación y administración existente
  requeriría un cambio separado en la app.

## Pistas para repetir la inspección

```sh
aapt dump badging local/waze-5-22-90-123.apk
scripts/decompile-waze-apk.sh
rg -n 'GEO_CONFIG_WEB_SERVICE_ADDRESS|rtserver/distrib|MAP_SHOW_.*CAMERA|MAP_SHOW_ACCIDENTS|MAP_SHOW_BLOCKED_LANES' \
  local/waze-5-22-90-123-jadx/sources/com/waze/config/ConfigValues.java
```

Referencias locales de la integración:

- `app/src/main/java/com/lito/a5launcher/ui/components/PoiRepository.kt`
- `app/src/main/java/com/lito/a5launcher/ui/components/CockpitMap.kt`
