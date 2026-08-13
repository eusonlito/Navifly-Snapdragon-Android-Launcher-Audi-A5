# Especificación de Recursos de Imagen - Audi A5 Launcher

Este documento define la estructura, tamaños, formatos y propósitos de los recursos gráficos (imágenes y assets) requeridos para el diseño de la interfaz de usuario del lanzador del Audi A5.

Aunque la interfaz actual dibuja gran parte de los elementos de forma dinámica mediante la API `Canvas` de Compose para garantizar un rendimiento óptimo a 60 fps sin consumo de I/O de disco, esta guía define los assets necesarios para una implementación basada en recursos de imagen de alta fidelidad.

---

## 1. Estándares Técnicos Generales
* **Formatos de Imagen Recomendados:**
  * **SVG (Vector):** Para logotipos, iconos de testigos y siluetas que requieran escalado sin pérdida de calidad. En Android se compilan a **VectorDrawables** XML.
  * **PNG (Raster transparente):** Con canal Alfa de 8-bits para texturas complejas, efectos de brillo/neón o renders 3D preprocesados de vehículos.
  * **WebP:** Excelente alternativa de compresión sin pérdidas para texturas complejas.
* **Espacio de Color:** sRGB optimizado para pantallas automotrices de alto contraste.
* **Ubicación en el Proyecto:** `/app/src/main/res/drawable/` o `/app/src/main/res/drawable-nodpi/` para evitar reescalados por densidad.

---

## 2. Inventario de Imágenes Requeridas

### A. Elementos de Esferas e Instrumentos (`ProgressRingIndicator`)

| ID de Recurso | Descripción Visual | Formato | Dimensiones (Píxeles) | Propósito / Uso |
| :--- | :--- | :--- | :--- | :--- |
| `bg_dial_speedometer.png` | Fondo de la esfera del velocímetro con un acabado metálico cepillado circular oscuro. | PNG (Transparente) | `320 x 320` | Fondo estático del dial de velocidad que aporta profundidad visual de cabina de carreras. |
| `bg_dial_tachometer.png` | Fondo de la esfera de revoluciones con el logo "S Line" sutil grabado en la mitad inferior. | PNG (Transparente) | `320 x 320` | Fondo estático del dial de RPM sobre el que se dibuja el indicador dinámico. |
| `ic_redline_zone.svg` | Arco indicador de zona roja de advertencia (de 6,500 a 8,000 RPM). | SVG / Vector | `280 x 280` | Superposición estática sobre el tacómetro para delimitar la zona de peligro mecánico. |

---

### B. Diagrama de Estado del Vehículo (`CarDoorsDiagram`)

Para mostrar el estado de las puertas mediante imágenes superpuestas de alta fidelidad en lugar de Canvas vectorial, se requiere una imagen base transparente del vehículo y piezas aisladas con el mismo centro para las puertas abiertas:

| ID de Recurso | Descripción Visual | Formato | Dimensiones (Píxeles) | Propósito / Uso |
| :--- | :--- | :--- | :--- | :--- |
| `car_chassis_topview.png` | Silueta cenital limpia y futurista de un Audi A5 Coupe (sin puertas ni maletero). Color gris titanio y negro lunas. | PNG (Transparente) | `240 x 300` | Chasis base centrado. Todos los demás recursos de puertas se superponen exactamente encima. |
| `door_front_left_open.png` | Puerta delantera izquierda abierta (piloto), rotada hacia el exterior con un haz degradado de luz roja de aviso. | PNG (Transparente) | `240 x 300` | Superpuesto encima del chasis cuando `doorStatus.driverOpen` sea verdadero. |
| `door_front_right_open.png`| Puerta delantera derecha abierta (copiloto), rotada hacia el exterior con haz degradado de luz roja. | PNG (Transparente) | `240 x 300` | Superpuesto encima del chasis cuando `doorStatus.passengerOpen` sea verdadero. |
| `door_rear_left_open.png`  | Puerta trasera izquierda abierta, rotada hacia el exterior con haz de luz roja. | PNG (Transparente) | `240 x 300` | Superpuesto encima del chasis cuando `doorStatus.rearLeftOpen` sea verdadero. |
| `door_rear_right_open.png` | Puerta trasera derecha abierta, rotada hacia el exterior con haz de luz roja. | PNG (Transparente) | `240 x 300` | Superpuesto encima del chasis cuando `doorStatus.rearRightOpen` sea verdadero. |
| `car_trunk_open.png`       | Portón del maletero abierto con un halo de aviso rojo proyectado verticalmente hacia atrás. | PNG (Transparente) | `240 x 300` | Superpuesto encima del chasis cuando `doorStatus.trunkOpen` sea verdadero. |

---

### C. Logotipos y Badges Corporativos

| ID de Recurso | Descripción Visual | Formato | Dimensiones (Píxeles) | Propósito / Uso |
| :--- | :--- | :--- | :--- | :--- |
| `logo_audi_rings.svg` | Los icónicos cuatro aros de Audi con efecto plateado cromado 3D sutil o plano limpio. | SVG / Vector | `180 x 60` | Encabezado principal de la barra lateral (`SidebarComponent`). |
| `badge_sline.svg` | Insignia deportiva "S Line". Logotipo S plateado sobre un rectángulo rojo brillante con borde metálico. | SVG / Vector | `80 x 40` | Decorador inferior de la barra lateral que refuerza el look premium. |

---

### D. Iconografía de Testigos de Alerta (Vitals & Witnesses)

Estos iconos se renderizan en formato vectorial nativo y cambian de color dinámicamente (`tint`) según el estado de alerta activa o inactiva del vehículo:

| ID de Recurso | Descripción Visual | Formato | Dimensiones (Píxeles) | Uso de Estado Activo | Uso de Estado Inactivo |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `ic_witness_seatbelt.svg` | Silueta clásica de pasajero con el cinturón de seguridad abrochado de perfil. | SVG | `44 x 44` | **Rojo S-Line** (`0xFFE30A17`) si está desabrochado. | Gris oscuro (`0xFF33333F`) si está correcto. |
| `ic_witness_handbrake.svg`| Círculo con una letra "P" en el centro rodeada de dos arcos de freno `(P)`. | SVG | `44 x 44` | **Naranja / Rojo** si el freno de mano está accionado. | Gris inactivo si está liberado. |
| `ic_witness_coolant.svg`  | Termómetro sumergido en ondas de refrigerante de motor. | SVG | `44 x 44` | **Rojo S-Line** si supera los 105.0°C (temperatura crítica). | Blanco/Plata en rangos normales de temperatura. |
| `ic_witness_fuel.svg`     | Surtidor clásico de gasolinera. | SVG | `44 x 44` | **Naranja Warn Amber** si el depósito tiene menos de 8 litros. | Blanco/Plata si el nivel es suficiente. |

---

## 3. Integración en Jetpack Compose

Para pintar estos recursos sobreponiendo capas transparentes de igual tamaño en el `CarDoorsDiagram`:

```kotlin
Box(modifier = Modifier.size(240.dp, 300.dp)) {
    // Capa 1: Chasis base siempre visible
    Image(
        painter = painterResource(id = R.drawable.car_chassis_topview),
        contentDescription = "Chasis Audi A5"
    )

    // Capas superpuestas condicionales
    if (doorStatus.driverOpen) {
        Image(
            painter = painterResource(id = R.drawable.door_front_left_open),
            contentDescription = "Puerta Conductor Abierta"
        )
    }
    if (doorStatus.passengerOpen) {
        Image(
            painter = painterResource(id = R.drawable.door_front_right_open),
            contentDescription = "Puerta Copiloto Abierta"
        )
    }
    // ... resto de puertas
}
```
Esto reduce la lógica matemática del renderizado y permite cambiar el render del coche por cualquier otro modelo de Audi simplemente reemplazando las imágenes del directorio de recursos.

---

## 4. Contrato de integración con la UI responsive

Mientras se preparan los recursos definitivos, la interfaz usa dibujos de Compose
como placeholders funcionales. La incorporación de imágenes no debe alterar las
proporciones del layout.

* El chasis y todas las variantes de puertas deben compartir lienzo, centro,
  escala y canal alfa. Compose los superpondrá dentro del mismo contenedor central.
* Los fondos de las esferas deben ser cuadrados y mantener contenido importante
  dentro de un margen seguro del 8 %. El valor, las unidades, las marcas activas y
  los avisos continuarán dibujándose dinámicamente por encima.
* Los PNG del vehículo no deben incluir carretera, anillos de proximidad ni fondo;
  esos elementos pertenecen al escenario dinámico.
* Los halos pueden formar parte de cada capa de puerta, pero no deben extenderse
  fuera del lienzo común de 240x300.
* Todos los recursos se integrarán en `drawable-nodpi` para conservar el control
  explícito de escala en la pantalla de 2400x900.

La sustitución se considerará terminada únicamente después de verificar una
captura del emulador con puertas cerradas y otra con cada zona de apertura activa.

---

## 5. Identidad de la aplicación de diagnóstico

`A5 Event Logger` utiliza un icono adaptativo propio, separado del launcher:

* fondo obsidiana compartido con A5 Cockpit;
* aro cian que representa el bus y la captura continua;
* traza naranja de eventos CAN;
* punto blanco de registro;
* variante redonda y pequeño icono monocromo para la notificación del servicio.

Los recursos se encuentran en `app/src/main/res/` del proyecto raíz y no dependen
de imágenes rasterizadas, por lo que mantienen nitidez en el Navifly a 320 dpi.
