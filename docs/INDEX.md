# Índice de documentación

Este directorio es la fuente documental de `a5-launcher`. Los documentos se
agrupan por dominio y describen lo que contiene la última versión.

## Arquitectura y plataforma

| Documento | Contenido | Estado |
|---|---|---|
| [Arquitectura](architecture/ARCHITECTURE.md) | Componentes, ciclo de vida, launcher y compilación | Vigente |
| [Dependencias](architecture/DEPENDENCIES.md) | Toolchain, librerías activas y verificación de actualizaciones | Vigente |
| [Dispositivo](architecture/DEVICE.md) | Hardware, Android, resolución y densidad reales | Referencia |

## Diseño y recursos

| Documento | Contenido | Estado |
|---|---|---|
| [Diseño](design/DESIGN.md) | Geometría, estilo OEM Futuristic y responsive | Vigente |
| [Capturas](design/CAPTURES.md) | Escena canónica y herramienta externa de captura | Vigente |
| [Imágenes](design/IMAGES.md) | Recursos gráficos e iconografía | Vigente |
| [Temas de la home](feature-home-themes/README.md) | Clásico, Horizonte, Facetas y Telemetría | Vigente |

## Funcionalidades

### Mapa

| Documento | Contenido | Estado |
|---|---|---|
| [Índice](feature-map/README.md) | Documentación completa de la feature | Vigente |
| [Especificación](feature-map/MAP.md) | Comportamiento y criterios de aceptación | Vigente |
| [Implementación](feature-map/MAPS.md) | Implementación vigente, diagnóstico y validación | Vigente |

La implementación activa es MapLibre Native 13.7.1 con OpenFreeMap.

### Puntos de Interés

| Documento | Contenido | Estado |
|---|---|---|
| [Índice](feature-poi/README.md) | Fuentes múltiples y gestión desde la aplicación | Vigente |
| [Formato](feature-poi/POI.md) | Contrato GeoJSON, PNG y límites | Vigente |
| [Conversores](feature-poi/CONVERTERS.md) | Cómo generar fuentes compatibles | Vigente |

### Vehículo e inicio

| Documento | Contenido | Estado |
|---|---|---|
| [Telemetría](feature-telemetry/README.md) | Eventos CAN, fuentes y cálculos | Vigente |
| [Telemetría nativa](feature-native-telemetry/README.md) | Firmware, EventCenter, protocolo MCU y viabilidad | En investigación |
| [Inicio del dispositivo](feature-boot/README.md) | Boot animation y boot logo | Referencia |

## Asistente de voz

| Documento | Contenido | Estado |
|---|---|---|
| [Integración de IA](feature-ai/README.md) | Implementación, seguridad y pruebas | Vigente (falta validar la voz en el coche) |

## Referencias

| Documento | Contenido | Estado |
|---|---|---|
| [Recursos de terceros](reference/THIRD_PARTY_ASSETS.md) | Procedencia y licencias de recursos | Vigente |

La documentación describe únicamente la última versión publicada; no se conserva
historial de planes ni de decisiones anteriores (para eso está `git log`).
