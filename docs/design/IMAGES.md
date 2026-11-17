# Recursos de imagen - A5 Cockpit

Inventario de los recursos gráficos que existen en `app/src/main/res/`. La
interfaz dibuja casi todo en tiempo de ejecución con `Canvas` de Compose
(esferas, escalas, barras, aros Audi del acceso MMI, iconos de Ajustes), de modo
que apenas hay imágenes rasterizadas.

> Una versión anterior de este documento especificaba un conjunto de PNG de
> esferas, chasis cenital con capas de puertas y logotipos de barra lateral. Ese
> enfoque nunca se implementó: la barra lateral desapareció y los recursos de
> abajo son los vigentes.

---

## 1. Criterios generales

* **Vectores primero.** Los pictogramas son `VectorDrawable` XML, teñidos en
  ejecución (`tint`) según el estado.
* **PNG sólo donde hace falta.** Se usan para el asistente y el marcador de
  posición del mapa, en `drawable-nodpi` para controlar la escala sin
  reescalado por densidad.
* **Sin conjuntos extendidos.** Ajustes usa trazos propios (`SettingsIcons.kt`)
  y no añade `material-icons-extended` al APK; sólo se declara
  `material-icons-core`.

---

## 2. Inventario

### Testigos (`drawable`)

| Recurso | Uso |
|---|---|
| `ic_witness_door.xml` | Pictograma completo de puertas (viewport 717) |
| `ic_witness_door_body.xml` | Carrocería del testigo de puertas |
| `ic_witness_door_front_left.xml`, `_front_right.xml`, `_rear_left.xml`, `_rear_right.xml` | Capas de puerta alineadas con la carrocería; sólo se pintan en rojo las abiertas según `DoorStatus` |
| `ic_witness_seatbelt.xml` | Cinturón de seguridad |
| `ic_witness_parking_brake.xml` | Freno de estacionamiento `(P)` |
| `ic_witness_lights.xml` | Luz de cruce; verde con el modo oscuro efectivo del mapa |

Los testigos se muestran a 30 dp (puertas), 34 dp (cinturón) y 38 dp (freno y
luces) en la escala 1 del tema Clásico; el resto de temas los escalan con el
mismo grupo (`HomeBlocks.kt`).

### Mapa y asistente

| Recurso | Formato | Uso |
|---|---|---|
| `map_position_waze.png` | PNG 768 x 707 | Marcador de posición (estado normal) |
| `map_position_waze_no_gps.png` | PNG 768 x 707 | Marcador sin señal GPS |
| `map_position_waze_no_network.png` | PNG 768 x 707 | Marcador sin red |
| `ic_map_poi.xml` | Vector 32 dp | Icono de punto de interés en el mapa y en sus ajustes |
| `assistant_robot.png` | PNG 256 x 184 | Mascota del Asistente IA |

### Identidad de la aplicación

| Recurso | Uso |
|---|---|
| `ic_launcher_foreground.xml` | Primer plano del icono adaptativo (108 dp); también es el icono monocromo |
| `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | Icono adaptativo; fondo `launcher_background` (`#071014`) |
| `ic_splash_cockpit.xml` | Icono animado de la pantalla de arranque (192 dp), configurado en el tema |

---

## 3. Contrato de integración

* Los recursos conservan sus proporciones; no se deforman para encajar en un
  layout (las puertas activas son recortes del mismo vector completo).
* Los tamaños se derivan del alto disponible (véase [DESIGN.md](DESIGN.md)); no
  se introducen anchos estructurales fijos pensados para una densidad concreta.
* Un cambio de pictograma exige una captura del emulador a `2400x896` con el
  estado normal y con cada alerta afectada (véase [CAPTURES.md](CAPTURES.md)).

Las capturas públicas de la documentación están en `docs/screenshots/`.
