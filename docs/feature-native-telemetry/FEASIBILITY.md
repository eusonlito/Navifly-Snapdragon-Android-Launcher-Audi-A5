# Evaluación de viabilidad

## Veredicto

Crear nuestro propio EventCenter completo es viable técnicamente, pero ya no es
el objetivo. La opción proporcionada es conservar el servicio del fabricante
para alimentación, cámara, overlays y MMI, y construir una capa modular que
normalice o descubra únicamente la telemetría integrable en el launcher.

## Opciones

| Opción | Acceso | Riesgo | Recomendación |
|---|---|---:|---|
| Consumir EventCenter y normalizar datos | Binder/provider | Bajo | Producción actual |
| Observador/logger pasivo | IPC existente | Bajo | Siguiente paso |
| Servicio propio junto a EventCenter | Puerto exclusivo/contención | Alto | Sólo laboratorio para datos ausentes |
| Reemplazo de sistema | UART + permisos de plataforma | Muy alto | Descartado para el producto |

## Qué podemos obtener ya

Velocidad, RPM, combustible, temperatura exterior, odómetro, luces, cinturón,
freno y puertas. Para marcha hacia delante se dispone actualmente de un
estimador validado con muestras de 1.ª a 5.ª; el valor nativo de dos bits no
identifica las seis marchas manuales.

## Qué sigue sin demostrarse

- Consumo medio oficial del vehículo y autonomía oficial.
- Estado/valor del control de crucero o ACC del coche. Las coincidencias `ACC`
  del código son mayoritariamente estado de alimentación Android, no crucero.
- Semántica completa de los tipos de marcha 0–2.
- Existencia de otros subcomandos MCU de conducción no publicados por el
  callback actual.

## Criterios para una ampliación segura

La integración debe vivir en un módulo independiente, conservar EventCenter,
ser exclusivamente de lectura y aportar tests por trama y fallback a los datos
actuales. Si exige acceder al puerto MCU, sólo se probará en laboratorio con
permisos adecuados y recuperación disponible; nunca debe competir por el puerto
en la versión de uso diario.
