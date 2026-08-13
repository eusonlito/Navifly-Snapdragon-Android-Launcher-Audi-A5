# Telemetría nativa del firmware

Esta carpeta conserva una única investigación reproducible del firmware del
Navifly/Mekede. Su objetivo es determinar si podemos sustituir EventCenter y
obtener directamente del MCU datos que hoy no llegan al launcher.

## Resultado actual

EventCenter es el puente entre el hardware y Android: abre `/dev/ttyHS1` a
115200 baudios, decodifica tramas MCU y publica el resultado por Binder,
broadcasts y `SysVarProvider`. La sustitución es técnicamente posible, pero una
APK ordinaria no puede abrir ese dispositivo en la unidad analizada. Las apps
de fabricante relevantes usan UID 1000 y la misma firma de plataforma.

La recomendación es mantener EventCenter en producción mientras se construye
un receptor compatible y se valida el protocolo de forma pasiva. Un reemplazo
directo sólo debe intentarse con instalación como aplicación de sistema,
permisos SELinux correctos y una vía de recuperación del dispositivo.

## Documentos

- [Tarea](TASK.md): alcance y restricciones.
- [Plan](PLAN.md): fases de investigación e implementación.
- [Flujo eficiente](WORKFLOW.md): cómo reutilizar el snapshot y evitar análisis repetidos.
- [Logs del sistema](SYSTEM-LOGS.md): catálogo incremental y resultados de LogCapture.
- [Snapshot](SNAPSHOT.md): procedencia e integridad del paquete.
- [Arquitectura](ARCHITECTURE.md): ruta MCU → EventCenter → consumidores.
- [Protocolo](PROTOCOL.md): tramas y campos confirmados.
- [Viabilidad](FEASIBILITY.md): alternativas, riesgos y decisión recomendada.
- [Cola](QUEUE.md): preguntas pendientes y capturas necesarias.
- [Catálogo](APPS.md): las 28 aplicaciones y sus fichas individuales.

Los APK, fuentes decompiladas y binarios no se versionan. Sólo se versionan los
índices, hashes y conclusiones reproducibles.
