# Implementación

La integración vive bajo `com.lito.a5launcher.assistant` y se divide en:

- `AssistantModels`: contratos comunes, estados y validación de destinos.
- `AssistantSettings`: preferencias y cifrado AES/GCM con Android Keystore.
- `AssistantAudio`: captura PCM mono con final local por silencio y reproducción.
- `RealtimeVoiceProvider`: límite común de transporte.
- `OpenAiRealtimeProvider`: WebSocket Realtime con PCM a 24 kHz.
- `GeminiLiveProvider`: WebSocket Live con PCM a 16 kHz y búsqueda de Google.
- `ProviderConnectionTester`: validación barata de claves mediante texto, sin
  abrir audio ni sesiones Realtime/Live.
- `AssistantErrorLogger`: un JSON interno por fallo y exportación conjunta ZIP.
- `AssistantController`: orquestación, timeout, contexto volátil y acción cerrada.
- `NavigationAction`: deep links tipados para Waze y Google Maps.
- `GooglePlacesDestinationResolver`: búsqueda de POI relativa a la posición con
  Text Search (New), field mask mínimo y validación de la respuesta.
- `AssistantUi`: botón, estado, conversación y configuración.
- `SettingsSegmentedSelector`: control visual compartido por los ajustes de
  Mapa y del Asistente IA.

El dashboard sólo construye el controlador, presenta los componentes y solicita
el permiso de micrófono. `TelemetryService` y `CockpitMap` no dependen del
asistente ni han sido modificados.

La elección `Desactivado`, `OpenAI` o `Google Gemini` se persiste al pulsar el
segmento izquierdo y es independiente del editor de credenciales. El panel
derecho ofrece pestañas para `OpenAI`, `Google Gemini` y `Google Places`; cada
clave se guarda, prueba y elimina por separado sin cambiar el proveedor activo.
Si el proveedor activo no tiene clave, el botón del launcher abre directamente
la pestaña `Asistente IA`.

La acción `Probar` conserva temporalmente el estado HTTP, el código y el mensaje
estructurado que devuelve el proveedor. Esto permite diagnosticar credenciales,
permisos, API deshabilitada, cuota o modelo sin reducirlos a un error genérico.
Los posibles patrones de credencial se ocultan antes de mostrarlos y el detalle
se guarda únicamente cuando `Registro de errores` está activo. Cada fallo genera
un JSON independiente, sin clave, audio, prompt ni conversación. `Descargar
errores` abre siempre el selector de documentos de Android antes de escribir y
exporta todos los JSON como una carpeta dentro de un ZIP en el destino elegido
por el usuario; cancelar el selector no crea ningún fichero. `Limpiar errores`
elimina los originales internos.

La prueba no abre los modelos de voz. Envía un texto mínimo mediante
`gpt-5.4-nano` en Responses API o `gemini-3.5-flash-lite` en GenerateContent,
según el proveedor. Los modelos Realtime/Live sólo se usan al conversar.
`Guardar` ejecuta primero esa misma validación y sólo persiste la clave si la
prueba es correcta; el resultado se muestra como una alerta temporal con el
mismo formato visual que los estados del Asistente IA.
Mientras la petición está activa se muestra `Validando…` y se bloquean el campo,
el cambio de credencial y las acciones Guardar, Eliminar y Probar. Al finalizar,
la alerta cambia a confirmación o al error detallado del proveedor.

La escucha espera como máximo tres segundos para detectar el comienzo de la
voz. Si no se habla, cancela la sesión y vuelve a `Listo` sin solicitar una
respuesta, mostrar un error ni crear un registro. Cuando sí hay voz, el turno
termina tras el silencio posterior habitual. Los mensajes y errores se muestran
de forma transitoria durante tres segundos.

La alerta sólo representa fases útiles para el conductor: `Escuchando` durante
la captura del micrófono y `Esperando respuesta` después de enviar el audio.
Después se muestra directamente la acción, la respuesta o el error; no existe
un estado adicional de procesamiento prolongado.
Durante `Escuchando`, un medidor animado representa la amplitud real del audio
PCM recibido para poder comprobar visualmente que el micrófono está activo.
La fuente `VOICE_RECOGNITION` se combina con el supresor de ruido de Android
cuando el dispositivo lo ofrece. No se activa ganancia automática explícita,
porque en un habitáculo ruidoso puede amplificar también el ruido de rodadura.

## Resolución de destinos

La función común de ambos proveedores es `navigate_to(destination,
relative_to_current_location)`. Para un destino concreto, el controlador abre
directamente el deep link universal de Waze con `q` y `navigate=yes`; no solicita
coordenadas al modelo ni llama a Google Places.

Sólo cuando `relative_to_current_location=true`, el controlador consulta Places
Text Search con sesgo circular sobre la última posición y orden por distancia.
La petición usa `X-Goog-Api-Key` y un `X-Goog-FieldMask` mínimo; la clave se cifra
por separado en Android Keystore y nunca se añade a la URL ni se envía al
proveedor de IA. Los fallos de Places se incorporan al registro de errores del
Asistente IA cuando éste está activo.

La acción `Probar` de Google Places realiza una consulta mínima real contra
Places API (New). Una respuesta satisfactoria valida la clave y el acceso a Text
Search; cualquier rechazo conserva el HTTP, código y mensaje saneado de Google
y puede guardarse en el registro de errores.

Para hablar desde el equipo de desarrollo, `scripts/emulator.sh` inicia Android Emulator
con `-allow-host-audio`; el cambio sólo se aplica al arrancar un emulador nuevo.

El handshake Gemini Live coloca `responseModalities` dentro de
`generationConfig`, como exige `BidiGenerateContentSetup`. El historial se envía
como contenido de cliente después de recibir `setupComplete`, sin añadir campos
auxiliares no reconocidos al mensaje de configuración.
Las respuestas WebSocket se procesan tanto si Gemini las entrega como frames de
texto como si las entrega como frames binarios UTF-8.

## Protocolos

- OpenAI: <https://developers.openai.com/api/docs/guides/realtime-websocket> y
  <https://developers.openai.com/api/docs/guides/realtime-conversations>.
- Gemini: <https://ai.google.dev/gemini-api/docs/live-api/get-started-websocket>
  y <https://ai.google.dev/gemini-api/docs/live-api/tools>.
- Waze: <https://developers.google.com/waze/deeplinks>.
- Google Places Text Search: <https://developers.google.com/maps/documentation/places/web-service/text-search>.
- Google Maps: <https://developers.google.com/maps/documentation/urls/get-started>.

OpenAI recomienda WebRTC y tokens efímeros para clientes móviles. Esta versión
mantiene la conexión WebSocket directa solicitada para el dispositivo privado;
la limitación queda registrada en `SECURITY.md`.

`gpt-realtime-2.1-mini` admite funciones pero no anuncia búsqueda web alojada en
Realtime. Gemini sí ofrece consultas actuales con grounding. OpenAI resuelve
navegación y conversación con el conocimiento del modelo, sin presentar una
búsqueda web en vivo como realizada.

La conexión OpenAI utiliza la forma GA de `/v1/realtime`: autenticación Bearer y
sin la cabecera retirada `OpenAI-Beta`. El modelo configurado es
`gpt-realtime-2.1-mini`. Los formatos PCM de entrada y salida declaran
explícitamente la frecuencia obligatoria de 24 kHz antes de iniciar la captura.
