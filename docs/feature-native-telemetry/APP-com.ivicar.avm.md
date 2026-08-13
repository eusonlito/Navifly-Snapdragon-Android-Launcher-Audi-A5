# com.ivicar.avm

<!-- GENERATED:START -->
## Identidad de la captura

- **Snapshot:** `d941ef16657cc2e90be1d73b6e00e7f84db270033930a1d07ac8a333b15f94d3`
- **APK SHA-256:** `42b1aac0f6ebf6b9d051d3ab7ca4fcc6b24628cb9d4ea3468a0604b0309d20e0`
- **Versión:** `2026.01.28-14:46`
- **UID:** `1000` · **Sistema:** `True`
- **Prioridad:** nivel 1 · **Estado:** `decompilada_parcial`
- **DEX:** 2 · **Librerías nativas:** 6
- **Permisos declarados:** 24
<!-- GENERATED:END -->

## Relevancia para la telemetría

Alta para la visualización 360, radares y trayectoria; no es el lector MCU.

## Interfaces y datos

- Recibe broadcasts JSON de control del vehículo.
- Modelos `Radar`, `SteeringWheel`, puertas, luces y marcha atrás.
- Capa JNI propia de cámara/AVM; no debe confundirse con CAN.

## Evidencias y hallazgos

| Confianza | Evidencia | Interpretación |
|---|---|---|
| Alta | `VehicleControlBroadcastReceiver` | Recibe `radar` y `steering_wheel` serializados. |
| Alta | `CanBusListener` | Entrega 16 posiciones de radar y ángulo al render 3D. |
| Media | `vehicle/test/canbus/Receiver` | Contiene broadcasts de prueba útiles para validar la interfaz, no el origen físico. |

## Búsquedas realizadas

- `VehicleControlBroadcastReceiver`, `CanBusListener`, `Radar`, `SteeringWheel`.

## Preguntas pendientes

- Localizar la conversión exacta de bytes EventCenter a las 16 distancias AVM.
