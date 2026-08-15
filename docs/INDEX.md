# Índice de documentación

Este directorio es la fuente documental de `a5-launcher`. Los documentos se
agrupan por dominio para que las especificaciones vigentes, las tareas activas y
el historial no se mezclen.

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
| [Ideas](IDEAS.md) | Mejoras candidatas de funcionalidad, interfaz y UX | Propuestas |
| [Dashboard OEM](feature-oem-dashboard/PLAN.md) | Plan y contrato visual Precision GT | En desarrollo |
| [Ideación del arranque](ideation/2026-08-12-animacion-inicio-launcher-ideation.html) | Alternativas evaluadas y autocheque panorámico elegido | Referencia |

## Funcionalidades

### Mapa

| Documento | Contenido | Estado |
|---|---|---|
| [Índice](feature-map/README.md) | Documentación completa de la feature | Vigente |
| [Tarea](feature-map/TASK.md) | Objetivo, requisitos y restricciones | Vigente |
| [Plan](feature-map/PLAN.md) | Implementación de la interfaz vectorial | Histórico |
| [Especificación](feature-map/MAP.md) | Comportamiento y criterios de aceptación | Vigente |
| [Implementación e historial](feature-map/MAPS.md) | Decisiones, incidencias y validaciones | Mixto |
| [MapLibre](feature-map/MAPLIBRE.md) | Elección técnica y referencias del motor vectorial | Referencia |

La implementación activa es MapLibre Native 13.4.1 con OpenFreeMap. Las
secciones fechadas de `MAPS.md` sobre osmdroid, WebView o MapLibre 13.0.2 son
historial de prototipos y no describen el renderer actual.

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
| [Registros funcionales](feature-functional-event-logs/PLAN.md) | Diario de decisiones, consulta y gestión | Implementado |
| [Telemetría nativa](feature-native-telemetry/README.md) | Firmware, EventCenter, protocolo MCU y viabilidad | En investigación |
| [Inicio del dispositivo](feature-boot/README.md) | Boot animation y boot logo | Referencia |

## Tareas y planes activos

| Documento | Contenido | Estado |
|---|---|---|
| [Integración de IA](feature-ai/README.md) | Tarea, plan, implementación, seguridad, pruebas y progreso | En desarrollo |

## Referencias

| Documento | Contenido | Estado |
|---|---|---|
| [Recursos de terceros](reference/THIRD_PARTY_ASSETS.md) | Procedencia y licencias de recursos | Vigente |

## Calidad e historial

| Documento | Contenido | Estado |
|---|---|---|
| [Auditoría técnica - Agosto de 2026](audit/AUDIT-2026-08.md) | Revisión integral actual, riesgos y prioridades de mejora | Vigente |
| [Auditoría](history/AUDIT.md) | Fotografía técnica del 28-07-2026 | Histórico |
| [Remediación](history/REMEDIATION.md) | Correcciones derivadas de la auditoría | Histórico verificado |
| [Plan inicial](history/PLAN-HISTORICAL.md) | Evolución temprana del proyecto | Histórico |

Para decisiones nuevas prevalecen los documentos marcados como vigentes y el
código actual. Los documentos históricos se conservan para explicar decisiones
y prototipos descartados.
