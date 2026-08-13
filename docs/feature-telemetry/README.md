# Telemetría

La especificación funcional usada por el launcher está en [TELEMETRY.md](TELEMETRY.md).
La ingeniería inversa del firmware y el estudio de un EventCenter propio se
mantienen separados en [telemetría nativa](../feature-native-telemetry/README.md)
para no mezclar datos de producción con hipótesis de investigación.

Documentación de las fuentes del vehículo, eventos capturados, decodificación y
valores calculados por el launcher.

- [Telemetría](TELEMETRY.md): contrato vigente de señales CAN/EventCenter,
  persistencia del trayecto y limitaciones conocidas.
- [A5 Inspector](INSPECTOR.md): recolección segura de APK, librerías, SysVar y
  metadatos del sistema para investigar la capa nativa MCU/EventCenter.
