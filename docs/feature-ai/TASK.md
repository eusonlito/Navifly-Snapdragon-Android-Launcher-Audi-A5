# Tarea: integración del Asistente IA

## Objetivo

Incorporar al launcher un Asistente IA opcional y accionado por voz, sin alterar
el comportamiento estable del mapa, la telemetría, los relojes ni el arranque.

## Requisitos

- Mostrar el botón del asistente como primera acción de la barra superior sólo
  cuando la función esté activada.
- Permitir elegir entre OpenAI y Google Gemini mediante una capa común de
  proveedores.
- Capturar voz, mostrar el estado de escucha, responder mediante audio y
  mantener una conversación corta que pueda continuarse, repetirse o cerrarse.
- Abrir Waze con búsquedas textuales para destinos concretos.
- Resolver mediante Google Places las peticiones relativas a la posición actual,
  como una gasolinera cercana, antes de abrir Waze con coordenadas verificadas.
- Guardar por separado las claves de OpenAI, Gemini y Places mediante Android
  Keystore, validándolas antes de persistirlas.
- Permitir activar, descargar y limpiar diagnósticos de peticiones fallidas sin
  registrar claves, audio ni conversaciones.
- Respetar el idioma configurado en Android.

## Restricciones

- No existe escucha permanente: cada pulsación abre un único turno cerrado.
- El asistente desactivado no debe inicializar red, audio ni proveedores.
- La conversación sólo se conserva en memoria.
- `.env` permanece exclusivamente en el host, fuera de Git y del APK.
- La integración debe ser modular para evitar cambios colaterales en código ya
  validado del launcher.

## Resultado esperado

La función debe fallar de forma recuperable ante ausencia de red, permisos,
credenciales inválidas o errores del proveedor, sin bloquear el launcher. Los
criterios técnicos y las fases de ejecución se detallan en [PLAN.md](PLAN.md).
