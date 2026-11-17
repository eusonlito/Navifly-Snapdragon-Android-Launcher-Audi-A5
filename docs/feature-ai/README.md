# Asistente IA

Este directorio concentra la especificación, la arquitectura, las pruebas y el
integración del Asistente IA del launcher.

- [Implementación](IMPLEMENTATION.md): módulos, contratos y decisiones técnicas.
- [Pruebas](TESTING.md): matriz de verificación y pruebas en dispositivo.
- [Seguridad](SECURITY.md): tratamiento de credenciales, audio y datos sensibles.

**Estado: implementado.** Se distribuye con las releases automáticas; la única
validación abierta es la de voz en el dispositivo físico.

La aplicación existente es la referencia estable. El asistente es una función
optativa y aislada: desactivarlo debe dejar exactamente el comportamiento previo.

## Modelos actuales

- OpenAI: `gpt-realtime-2.1-mini` para voz y navegación, con `gpt-transcribe`
  para transcribir cada intervención al terminar la captura. La respuesta se
  solicita después de recibir la transcripción para conservarla en el historial
  antes de que una orden de navegación cierre el turno.
- Google Gemini: `gemini-3.8-live`, estable, con búsqueda y navegación. La función
  de navegación usa `BLOCKING`; el historial se envía como contexto sin solicitar
  una respuesta hasta recibir la intervención actual. La captura local marca el
  inicio y el final del audio con `activityStart` y `activityEnd`, sin detección
  automática de voz en el servidor.

El panel muestra el modelo de cada proveedor; cambiarlo requiere actualizar la
aplicación. Las claves y la selección de proveedor existentes se conservan.
