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
- Clave configurada validada en emulador contra Places API (New): `Conexión
  correcta`. La captura `assistant-credential-tabs-places-valid-final-20260807.png`
  confirma además que Google Places puede probarse manteniendo Google Gemini
  como proveedor activo.
- Parsers de función OpenAI y eventos multiparte/audio/fin de turno Gemini.
- Prueba estructural del handshake Gemini: `responseModalities` pertenece a
  `generationConfig` y no se envía en la raíz de `setup`.
- Prueba de la confirmación `setupComplete` recibida como frame binario UTF-8.
- Configuración Gemini con búsqueda y función de navegación simultáneas.
- `testDebugUnitTest`, `lintDebug` y `assembleDebug` correctos.
- `testDebugUnitTest`, `lintRelease` y `assembleRelease` correctos mediante
  `scripts/compile.sh`; APK optimizado copiado a `out/A5Cockpit.apk`.
- Inspección del APK: no contiene `.env` ni los valores de las credenciales de
  prueba configuradas en el host.
- Cada turno tiene identidad propia: callbacks tardíos, audio y navegación de
  una sesión cancelada no pueden afectar a la siguiente. Los cierres WebSocket
  incompletos fallan de inmediato.
- El historial se envía como mensajes nativos con rol (`user`/`assistant` o
  `user`/`model`), nunca interpolado en las instrucciones de sistema.
- Credenciales locales aceptadas por los endpoints de ambos modelos: HTTP 200.
- Capturas locales 2400×896: `ai-assistant-tabs-20260807.png` valida las
  pestañas y dimensiones de los controles; `ai-assistant-disabled-dashboard-20260807.png`
  confirma que el icono no se compone cuando el Asistente IA está desactivado.
- `launcher-settings-blue-ai-20260807.png` valida la continuidad azul entre la
  pestaña activa y su contenido, los selectores compartidos y la localización
  española; `launcher-settings-blue-map-20260807.png` cubre el mismo patrón en
  Mapa.
- Verificado en emulador que elegir `Desactivado` persiste `DISABLED` de forma
  inmediata y oculta el icono al volver, sin eliminar las claves almacenadas.

Pendiente de dispositivo físico: calidad del micrófono, umbral de silencio,
volumen, deep links instalados y cobertura intermitente. El micrófono virtual del
emulador no representa fielmente esas condiciones.
La eficacia del supresor de ruido depende de la implementación de audio del
dispositivo y debe comprobarse conduciendo; la compilación no puede validarla.
