# Remediación de la auditoría

**Fecha:** 28 de julio de 2026

## Cambios aplicados

* Direct Boot no inicializa preferencias, logger, `MediaStore`, ViewModel,
  telemetría ni mapa antes de `UserManager.isUserUnlocked`.
* El mapa observa el ciclo de vida: pausa `MapView` y cancela listeners GPS/red
  y tareas periódicas cuando la actividad deja de estar visible.
* Los `StateFlow` de la pantalla se recogen de forma consciente del ciclo de
  vida.
* Las reglas de marcha numérica, consumo medio y autonomía calculada se
  conservan como decisiones funcionales. Se han centralizado y cubierto con
  tests, dejando documentado que son cálculos locales y no datos CAN.
* Temperatura exterior y odómetro ya no comienzan con números ficticios.
* La decodificación CAN se extrajo a `TelemetryDecoder`; los tests llaman al
  código de producción.
* El worker de EventCenter es supervisado y secuencial, y reintenta el binding
  tras desconexión o muerte del Binder.
* Se eliminaron el motor raster sustituido, su test y tres componentes visuales
  sin referencias.
* El mínimo real se fijó en API 29, acorde con el uso de MediaStore y muy por
  debajo del dispositivo objetivo API 34.
* Se retiraron permisos no utilizados, se desactivó backup y se incrementó la
  versión a `1.1.0` (`versionCode 2`).
* `scripts/compile.sh` ejecuta Lint, genera SHA-256 y conserva el mapping de R8.
* Los formatos numéricos siguen la región configurada.

## Validación automática

La remediación se considera integrada únicamente si finalizan correctamente:

```text
./gradlew clean testDebugUnitTest lintRelease assembleRelease
```

Resultado local de la remediación: **correcto**. Tras actualizar a AGP 9.2, que
no crea `testReleaseUnitTest` en este proyecto, las pruebas se ejecutan mediante
`testDebugUnitTest` contra el mismo código productivo. Lint valida la variante
release y se genera el APK release minificado. Las versiones del toolchain se
registran en `../architecture/DEPENDENCIES.md`.

## Validación pendiente en el vehículo

* Diez arranques en frío, con y sin cobertura.
* Confirmar que abrir Waze/MMI/Ajustes pausa GPS, red y heartbeat del mapa.
* Desconectar y recuperar red y GPS sin recrear el launcher.
* Forzar/reiniciar EventCenter y confirmar que la telemetría se recupera.
* Validar los modos `R`, `N`, `D` y `S` contra el cuadro físico.
* Confirmar que los bytes 5–6 sólo se muestran si coinciden con la autonomía
  física.

La firma sigue siendo la clave debug para conservar la instalación directa
sobre las versiones de prueba existentes. Debe sustituirse por una clave
privada estable antes de cualquier distribución fuera de este flujo.
