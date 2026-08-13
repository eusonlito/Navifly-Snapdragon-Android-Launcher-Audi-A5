# Seguridad

`.env` permanece exclusivamente en la raíz local del proyecto, ignorado por Git.
Gradle, recursos Android, scripts de entrega, logs y APK no leen ni copian su
contenido.

Las claves configuradas en el dispositivo usan AES-256/GCM con material no
exportable de Android Keystore bajo el alias
`a5_launcher_assistant_api_keys_v1`. Sólo se descifran en memoria para una
conexión solicitada. No se guardan audio, transcripciones, respuestas, destinos
ni conversaciones.

`.env` sólo se usó desde el shell del host para comprobar que ambos endpoints
aceptan las credenciales (HTTP 200), sin mostrar ni copiar sus valores.

La API Realtime de OpenAI considera el WebSocket con clave estándar una conexión
server-to-server y recomienda WebRTC/token efímero en móvil. El uso directo aquí
se limita al dispositivo privado. Antes de distribuir la aplicación fuera de ese
entorno se debe sustituir por tokens efímeros emitidos por un backend: el cifrado
local reduce la exposición en reposo, pero no convierte una clave duradera en una
credencial pública segura.

Gemini Live autentica el WebSocket con `?key=...` porque así lo exige su API
oficial. El launcher no registra URLs ni peticiones. Una distribución pública
usará su endpoint restringido con token efímero.
