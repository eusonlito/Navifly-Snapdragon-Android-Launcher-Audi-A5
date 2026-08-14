# Plan de integración del Asistente IA

## Objetivo

Añadir un Asistente IA accionado desde el primer botón de la barra superior
cuando la función esté activada.
Debe poder mantener conversaciones generales y resolver destinos mediante una
acción tipada de navegación. El usuario elige OpenAI o Gemini y aporta su propia
clave API.

Modelos fijados para esta primera versión:

- OpenAI: `gpt-realtime-2.1-mini`.
- Google: `gemini-3.1-flash-live-preview`.

Ambos usan entrada y salida de audio nativas del modelo. El launcher no encadena
servicios STT, LLM y TTS independientes.

La comprobación manual de claves queda deliberadamente separada: usa una
petición de texto mínima con `gpt-5.4-nano` o `gemini-3.5-flash-lite`, evitando
el coste de iniciar Realtime/Live y procesar audio sólo para validar acceso.

## Límites de alcance

- Una pulsación inicia un único turno de micrófono; no hay escucha permanente.
  Si no comienza a hablarse en cinco segundos, el turno se cierra silenciosamente.
- La alerta pasa de `Escuchando` a `Esperando respuesta` y después muestra la
  acción, respuesta o error final, sin estados intermedios de procesamiento.
- Las respuestas conversacionales abren un panel con `Responder`, `Repetir` y
  `Cerrar`. `Responder` abre un nuevo turno cerrado y `Repetir` vuelve a
  reproducir la respuesta de audio ya recibida.
- La conversación sólo vive en memoria y se elimina al cerrar el panel, cambiar
  de proveedor o reiniciar el proceso.
- La única acción v1 es abrir navegación. Los destinos concretos se entregan a
  Waze como texto; sólo las búsquedas relativas se resuelven a coordenadas.
- No se controla el coche, no se altera la telemetría y no se modifica el mapa.
- No hay funcionamiento IA sin conexión ni inicialización del proveedor al
  arrancar el launcher.
- No se promete añadir una parada a una navegación Waze en curso: su interfaz
  pública sólo permite abrir una nueva navegación.

## Principio de modularidad

La integración se implementa como un módulo lógico autocontenido bajo el paquete
`assistant`. El dashboard sólo conoce un controlador de estado, un callback del
botón y componentes Compose independientes. El mapa, `TelemetryService`, los
relojes y sus flujos no se refactorizan para acomodar el asistente.

Si una necesidad futura exige cambios extensos en esos subsistemas, se detendrá
esa ampliación para rediseñarla antes de introducir parches en código estable.

## Arquitectura

1. **Dominio común**: estados de sesión, resultados, errores y acción
   `NavigateToDestination`.
2. **Orquestador**: ciclo de vida de un turno, historial volátil, validación y
   ejecución de acciones permitidas.
3. **Audio**: captura PCM mono y reproducción PCM aisladas del proveedor.
4. **Adaptadores**: `OpenAiRealtimeProvider` y `GeminiLiveProvider` implementan
   un contrato común y traducen sus protocolos WebSocket.
5. **Acciones**: registro cerrado; sólo navegación puede producir efectos.
6. **Navegación**: adaptadores Waze/Google Maps mediante intents/deep links.
7. **Persistencia**: preferencias no sensibles y almacén Android Keystore para
   las claves. Nunca se persiste una conversación.
8. **UI**: botón robot, panel compacto sobre el mapa y modal de respuesta.

## Flujo de navegación

El proveedor sólo extrae `destination` y
`relative_to_current_location`; nunca inventa ni solicita coordenadas.

- Destino concreto (`centro de Ordes`, una dirección o un aeropuerto): se abre
  `https://waze.com/ul?q=...&navigate=yes`. Waze resuelve el texto.
- Búsqueda relativa (`gasolinera más cercana`): Google Places Text Search usa la
  última ubicación conocida, devuelve un lugar verificable y sólo entonces se
  abre Waze con `ll=latitud,longitud&navigate=yes`.

Las coordenadas devueltas por Places deben ser finitas y estar en los intervalos
`[-90, 90]` y `[-180, 180]`. Una búsqueda concreta no depende de Places ni de su
clave, por lo que sigue funcionando aunque esa integración no esté configurada.

Una acción válida se ejecuta y termina el turno. No se presenta como realizada
si Android no encuentra una aplicación compatible.

## Estados y recuperación

`Desactivado`, `Listo`, `Escuchando`, `Procesando`, `Hablando`, `Sin conexión` y
`Error`. Los errores de permiso, autenticación, red, protocolo, timeout o audio
son recuperables y nunca deben bloquear el dashboard. A los 10 segundos se muestra
un aviso de demora; alrededor de 30 segundos el turno falla de forma controlada.

## Ajustes

- La pantalla de ajustes se divide en las pestañas superiores `Mapa`,
  `Asistente IA` y `Versión` para mantener cada bloque aislado y legible.
- Activación: desactivado, OpenAI o Gemini.
- El panel derecho dispone de pestañas independientes `OpenAI`, `Google Gemini`
  y `Google Places`; cambiar el proveedor activo no es necesario para guardar,
  probar o eliminar una clave concreta.
- Las tres claves se guardan en campos protegidos. La de Google Maps Platform se
  usa sólo para búsquedas cercanas y no se entrega a OpenAI ni a Gemini.
- Modelo informativo y no editable.
- Probar conexión.
- Eliminar individualmente la clave mostrada.
- Activar el registro local de peticiones fallidas, descargar todos los errores
  como un ZIP y limpiar el registro.
- Los selectores y botones mantienen la misma altura que los controles de los
  ajustes del mapa.

La interfaz y las respuestas siguen el idioma configurado en Android.

## Seguridad y privacidad

- `.env` es sólo una ayuda local del host; está ignorado por Git y jamás se lee
  desde Gradle, se empaqueta, copia ni registra.
- Las claves introducidas en el coche se cifran con una clave AES/GCM no
  exportable de Android Keystore.
- No se escriben audio, transcripciones, prompts, respuestas, destinos ni claves
  en los logs del launcher.
- La conexión se crea bajo demanda y se cierra al terminar o cancelar la sesión.
- Esta arquitectura directa implica que la clave reside en el dispositivo. Para
  una distribución pública se deberá migrar a tokens efímeros/proxy.

## Unidades de implementación

1. Protección del repositorio y documentación.
2. Contratos, validación de navegación y almacén seguro, con pruebas unitarias.
3. Audio y transporte WebSocket común.
4. Adaptadores OpenAI y Gemini con parsers probados.
5. Orquestación y ejecución de navegación.
6. UI y ajustes, desactivados por defecto.
7. Verificación de regresión, emulador, APK de producción y documentación final.

## Definición de terminado

- Con el asistente desactivado no aparece su botón en el dashboard y no cambia
  el arranque ni el resto del launcher.
- Ninguna conexión se abre durante el arranque.
- Un turno cierra el micrófono antes de procesar la respuesta.
- Los dos proveedores comparten los mismos contratos y acciones.
- Destinos inválidos o ambiguos no abren navegación.
- Waze recibe texto para destinos concretos y coordenadas validadas exclusivamente
  para resultados relativos resueltos por Places.
- El diálogo se puede responder, repetir y cerrar sin escucha continua.
- Las claves sobreviven al reinicio cifradas; el contexto conversacional no.
- Los artefactos compilados no contienen `.env` ni claves de prueba.
- Pruebas, lint/compilación y validación visual pasan antes del APK de producción.
