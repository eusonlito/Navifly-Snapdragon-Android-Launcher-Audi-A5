---
date: 2026-08-11
topic: launcher-evolution
focus: Mejoras de funcionalidad, interfaz y UX del launcher
mode: repo-grounded
---

# Ideation: siguiente evolución del launcher

## Codebase Context

El dashboard actual ya tiene una identidad clara: mapa panorámico, dos instrumentos dominantes, cabecera de accesos rápidos y una banda inferior que conserva todos los datos necesarios.
La captura canónica del 11-08-2026 confirma que velocidad, RPM y mapa forman una jerarquía legible, mientras que la telemetría secundaria permanece disponible sin competir con ellos.

Las pantallas secundarias no alcanzan todavía ese nivel.
En Ajustes, el cian deja de funcionar como acento y se convierte en una superficie enorme; las tres pestañas fuerzan contenidos muy distintos dentro de la misma plantilla; todos los controles parecen tener la misma importancia; y Versión deja casi toda la pantalla vacía.
La superposición semitransparente permite además percibir los iconos del dashboard por detrás de la cabecera, generando ruido visual.

Capturas de referencia locales (no versionadas):

- `a5-current-interface-20260811-dashboard.png`
- `a5-current-interface-20260811-settings-map.png`
- `a5-current-interface-20260811-settings-ai.png`
- `a5-current-interface-20260811-settings-version.png`

La aplicación ya dispone de telemetría persistente en segundo plano, mapa vectorial, puntos de interés, caché, diagnósticos, actualizador, panel de aplicaciones y Asistente IA.
Las ideas se apoyan en esas capacidades y evitan depender de nuevos datos del coche que EventCenter no proporciona.

Como referencia externa, las guías oficiales para interfaces de coche recomiendan estados comprensibles en una mirada de unos dos segundos, respuesta visual a una pulsación en menos de 250 ms, tareas frecuentes en tres pasos o menos y objetivos táctiles muy generosos.
Estas reglas no obligan a copiar Android Automotive, pero son una base útil para nuestro dispositivo: [principios de interacción](https://developers.google.com/cars/design/design-foundations/interaction-principles), [principios visuales](https://developers.google.com/cars/design/design-foundations/visual-principles) y [dimensionado](https://developers.google.com/cars/design/automotive-os/design-system/sizing).

## Decisiones de implementación del 11-08-2026

- Implementar Ajustes Cockpit tomando como referencia una composición clara de pestañas, tarjetas y controles, con colores planos y sin degradados.
- Aplicar el mismo sistema visual al panel de Aplicaciones para que las pantallas secundarias sean coherentes.
- Limitar la personalización de la barra superior a reordenar sus iconos mediante pulsación prolongada y arrastre.
- Implementar el Asistente IA orientado a acciones antes de continuar con los instrumentos.
- Descartar el zoom cartográfico inteligente, el historial local de viajes y el centro de estado y mantenimiento.
- Dejar el acabado OEM de los instrumentos como siguiente fase, una vez validadas estas pantallas.

---

## Topic Axes

- Coherencia visual entre dashboard y pantallas secundarias.
- Interacción rápida y segura durante la conducción.
- Aprovechamiento de mapa, viaje, combustible y telemetría existente.
- Mantenimiento y diagnóstico sin conocimientos técnicos.
- Evolución del Asistente IA como parte natural del cockpit.

---

## Ranked Ideas

### 1. Ajustes Cockpit

**Description:** Rediseñar Ajustes como una pantalla propia del mismo vehículo, no como un formulario ampliado.
La referencia aceptada utiliza una carcasa grafito plana, contenido gris claro, tarjetas blancas y cian reservado para la pestaña activa, el control seleccionado y los estados correctos.
No se utilizarán degradados.
Las pestañas quedarán unidas visualmente a la superficie común de contenido, sin marcos o fondos que interrumpan esa continuidad.
Cada sección usaría una composición adecuada: Mapa con configuración y una pequeña previsualización del estilo; Asistente IA con una lista compacta de proveedor/diagnóstico y un editor de credenciales claramente diferenciado; Sistema con estado, almacenamiento, versión, actualización y mantenimiento.
Las acciones primarias, neutrales y destructivas dejarían de compartir el mismo aspecto.

**Axis:** Coherencia visual y configuración.

**Basis:** `direct:` las tres capturas de Ajustes muestran un gran marco cian, controles de 34 dp, texto secundario pequeño, doble navegación en Asistente IA y una pestaña Versión con mucho espacio sin propósito.

**Rationale:** Es el cambio con mayor impacto perceptible fuera del dashboard y corrige una pantalla que el usuario ya identifica como débil.
También crea un sistema de componentes reutilizable para futuros ajustes sin ir añadiendo paneles especiales.

**Downsides:** Requiere revisar la arquitectura visual completa del overlay y validar cuidadosamente que todas las opciones entren sin scroll en 2400 × 896.

**Confidence:** 98%

**Complexity:** Medium

### 2. Acabado OEM para los instrumentos

> **Seleccionado para implementación.** La especificación ejecutable se
> conserva en [feature-oem-dashboard/PLAN.md](feature-oem-dashboard/PLAN.md).

**Description:** Refinar las esferas sin cambiar su tamaño, posición ni información.
La mejora usaría superficies sólidas, un segundo nivel de negro, contornos técnicos y sombras de contacto muy controladas, nunca grandes degradados.
Los valores ya alcanzados de la escala ganarían presencia progresiva y el extremo del arco activo tendría un remate preciso.
La marcha recibiría una transición breve al cambiar y los testigos un único encendido material, sin animación continua.

**Axis:** Coherencia visual del dashboard.

**Basis:** `direct:` la composición actual funciona, pero las esferas siguen siendo grandes discos negros casi planos sobre el mapa; `reasoned:` mejorar profundidad local y respuesta de estado produce impacto sin alterar una geometría ya validada.

**Rationale:** Aumenta la sensación de cuadro de producción conservando íntegramente la lectura actual y evitando el riesgo de un rediseño estructural.

**Downsides:** Los efectos deben medirse en el dispositivo real; una sombra o brillo excesivo empeoraría mapa, noche y rendimiento.

**Confidence:** 91%

**Complexity:** Medium

### 3. Reordenación directa de la barra superior

**Description:** Mantener pulsado cualquier acceso de la cabecera para entrar directamente en arrastre y cambiar su posición horizontal.
El nuevo orden se conservará entre sesiones.
No se permitirá ocultar botones, añadir favoritos ni configurar la barra desde Ajustes.
El botón del Asistente IA seguirá ocultándose cuando esté desactivado y conservará su posición para cuando vuelva a activarse.

**Axis:** Interacción rápida durante la conducción.

**Basis:** `direct:` varios iconos de la cabecera han necesitado iteraciones para diferenciarse y actualmente no tienen texto; `external:` las guías de conducción piden correspondencia consistente entre icono y función y una respuesta visible en menos de 250 ms.

**Rationale:** Permite adaptar la prioridad de los accesos con una interacción directa y elimina la necesidad de añadir otra pantalla de configuración.

**Downsides:** El arrastre debe exigir una pulsación prolongada clara para que una pulsación normal nunca cambie el orden por accidente.

**Confidence:** 90%

**Complexity:** Low

### 7. Asistente IA orientado a acciones

**Description:** Hacer que el HUD del Asistente IA muestre de forma muy breve qué ha entendido y qué acción va a ejecutar: “Navegar a Ordes”, “Buscar gasolinera cercana” o “Responder pregunta”.
Durante escucha mantendría el indicador de nivel ya disponible; durante espera mostraría un estado único; y al finalizar conservaría sólo una confirmación corta, con repetición de voz accesible.
En Ajustes, cada proveedor mostraría disponibilidad, clave validada y modelo en una sola ficha, eliminando la sensación de dos grupos de pestañas que compiten entre sí.

**Axis:** Integración natural del Asistente IA.

**Basis:** `direct:` el asistente ya dispone de estados de escucha, conexión, acciones de navegación, pruebas de credenciales y logs; la captura de Ajustes muestra que proveedor activo y credencial editada usan controles visualmente similares aunque sean conceptos diferentes.

**Rationale:** Aumenta confianza al usar voz y reduce la incertidumbre sobre si el sistema está escuchando, esperando o actuando.

**Downsides:** El texto debe permanecer extremadamente breve y nunca tapar velocidad, RPM o una alerta del vehículo.

**Confidence:** 89%

**Complexity:** Medium

---

## Rejection Summary

| # | Idea | Reason Rejected |
|---:|---|---|
| 1 | Añadir muchos temas visuales completos | Fragmentaría el sistema visual y multiplicaría las pruebas; es preferible perfeccionar una dirección OEM y, como máximo, variar acento o intensidad. |
| 2 | Recuperar una barra lateral de navegación | Ya se descartó por ocupar demasiado ancho útil en la pantalla panorámica y reducir el mapa. |
| 3 | Mostrar climatización o control de crucero | EventCenter no proporciona datos fiables para esas funciones; implicaría enseñar información inventada o incompleta. |
| 4 | Sustituir la vista nativa de cámara/PDC | Está fuera del alcance actual y exigiría controlar una ruta privilegiada del sistema con riesgo operativo. |
| 5 | Añadir widgets de música, tiempo o notificaciones | Aumentaría densidad y distraería del cuadro sin resolver una necesidad principal del launcher. |
| 6 | Mapa 3D con edificios y efectos atmosféricos | Coste de GPU y complejidad altos para una mejora principalmente decorativa; el mapa claro actual prioriza carreteras y posición. |
| 7 | Alertas sonoras nuevas para puntos de interés | Podrían competir con la navegación o con avisos OEM y requieren una política por categoría. |
| 8 | Gradientes, partículas o animaciones ambientales | No encajan con la dirección visual aprobada y pueden deslumbrar o distraer, especialmente de noche. |
| 9 | Reorganizar automáticamente todo el dashboard según contexto | El movimiento de datos entre posiciones perjudicaría la memoria visual; las mejoras contextuales deben cambiar énfasis, no geometría. |
| 10 | Zoom cartográfico inteligente | Se mantiene el zoom manual para evitar oscilaciones, movimientos inesperados de cámara y nueva complejidad sobre un mapa ya estable. |
| 11 | Historial local de viajes | No aporta suficiente valor inmediato frente al coste de definir y mantener sesiones históricas. |
| 12 | Centro de estado y mantenimiento | Se conservan los estados y diagnósticos dentro de sus bloques actuales, sin crear otra pantalla general. |
