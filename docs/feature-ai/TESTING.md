# Pruebas

La validación combina pruebas unitarias deterministas con proveedores falsos,
pruebas de parsers de eventos y pruebas manuales en emulador/dispositivo.

Casos obligatorios: función desactivada, permiso denegado, sin red, clave inválida,
timeout, destino válido, coordenadas inválidas, ambigüedad, ubicación obsoleta,
respuesta conversacional, repetición, cambio de proveedor y cierre de contexto.

## Evidencia disponible

- Validación unitaria de coordenadas válidas, fuera de rango y ambigüedad.
- Contrato híbrido: destino concreto a Waze mediante `q`, petición cercana a
  Places con posición, orden por distancia, field mask mínimo y clave fuera de URL.
- Prueba manual de la clave de Places mediante Text Search real y diagnóstico
  detallado de restricciones, permisos, cuota o facturación.
- Clave de Places validada en emulador contra Places API (New): `Conexión
  correcta`; puede probarse manteniendo Google Gemini como proveedor activo.
- Parsers de función OpenAI y eventos multiparte/audio/fin de turno Gemini.
- Prueba estructural del handshake Gemini: `responseModalities` pertenece a
  `generationConfig` y no se envía en la raíz de `setup`.
- Prueba de la confirmación `setupComplete` recibida como frame binario UTF-8.
- Configuración Gemini con búsqueda y función de navegación simultáneas.
- La CI (JDK 21, `.github/workflows/android.yml`) ejecuta `:app:testDebugUnitTest`,
  `:app:lintRelease` y `:app:assembleRelease` en cada commit de `master`, y
  `scripts/compile.sh` hace lo mismo en local.
- Protocolo y sesión (`RealtimeProtocolTest`, `RealtimeProviderSessionTest`):
  la respuesta OpenAI espera la transcripción del usuario antes de que una
  navegación cierre el turno; una transcripción tardía tras cancelar no crea
  respuesta; Gemini envía el historial antes de marcar `activityStart`.
- Privacidad: `providerPromptNeverContainsVehicleCoordinates` comprueba que el
  prompt general no incluye la posición; `DestinationSearchRoutingTest` cubre el
  rechazo local de búsquedas cercanas sin ubicación validada.
- Inspección del APK: no contiene `.env` ni los valores de las credenciales de
  prueba configuradas en el host.
- Cada turno tiene identidad propia: callbacks tardíos, audio y navegación de
  una sesión cancelada no pueden afectar a la siguiente. Los cierres WebSocket
  incompletos fallan de inmediato.
- El historial se envía como mensajes nativos con rol (`user`/`assistant` o
  `user`/`model`), nunca interpolado en las instrucciones de sistema.
- Credenciales locales aceptadas por los endpoints de ambos modelos: HTTP 200.
- La captura vigente de los ajustes del Asistente IA es
  `../screenshots/settings-ai.png` (2400×896).
- Verificado en emulador que elegir `Desactivado` persiste `DISABLED` de forma
  inmediata y oculta el icono al volver, sin eliminar las claves almacenadas.

Pendiente de dispositivo físico: calidad del micrófono, umbral de silencio,
volumen, deep links instalados y cobertura intermitente. El micrófono virtual del
emulador no representa fielmente esas condiciones.
La eficacia del supresor de ruido depende de la implementación de audio del
dispositivo y debe comprobarse conduciendo; la compilación no puede validarla.
