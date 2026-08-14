# Especificación de diseño y UI - Audi A5 Launcher (2400x896)

Este documento detalla las especificaciones de diseño, tokens visuales y
estructura de los componentes Jetpack Compose, calibrados para la superficie
útil real de **2400x896 píxeles** del Navifly.

---

## 1. Tokens de Diseño (Design Tokens)

El sistema visual implementa una estética **moderna, minimalista y oscura**, alineada con la atmósfera de un coche deportivo premium (Audi S-Line).

### A. Paleta de Colores (Colores de la Marca)
- **Fondo General (Deep Obsidian):** `Color(0xFF0F0F12)` (Negro ultra puro mate para evitar reflejos molestos en carretera).
- **Fondo de Contenedor (Asphalt Charcoal):** `Color(0xFF1B1B22)` (Gris carbón oscuro con esquinas redondeadas de `16.dp`).
- **Color de Acento Primario (S-Line Red):** `Color(0xFFE30A17)` (Rojo icónico de Audi S-Line para agujas, límites de revoluciones y alertas).
- **Acento Secundario (Titanium Silver):** `Color(0xFFD1D5DB)` (Gris plateado cepillado para tipografías secundarias e iconos inactivos).
- **Brillo de Estado Activo (Neon Green):** `Color(0xFF10B981)` (Verde para testigos de intermitentes o cinturones correctos).
- **Brillo de Alerta Activa (Warn Amber):** `Color(0xFFF59E0B)` (Naranja para el freno de mano o alertas mecánicas).

### B. Tipografía
Se prioriza una tipografía monoespaciada o sans-serif de alta visibilidad para que el conductor pueda leer la velocidad de un solo vistazo:
- **Velocidad/RPM (Dígitos Principales):** Sans-Serif gruesa o Monoespacio condensado (`FontWeight.Bold`, tamaño `72.sp`).
- **Etiquetas de Unidad (km/h, RPM):** Mayúsculas medianas (`FontWeight.Medium`, tamaño `14.sp`, color `Titanium Silver`).
- **Reloj Principal:** Fuente estilizada de bajo contraste (`36.sp`).

---

## 2. Estructuración del layout (2400x896)

Aprovechando la relación de aspecto ultra panorámica, la pantalla se organiza en un único contenedor horizontal (`Row` a pantalla completa):

```
┌─────────────────┬────────────────────────────────────────────────────────────────────────┐
│  SIDEBAR (15%)  │                           DASHBOARD (85%)                              │
│                 │                                                                        │
│ 🧭 Navegación   │  ┌──────────────┐      ┌─────────────────┐      ┌──────────────┐        │
│    (Waze)       │  │  VELOCIDAD   │      │ RELOJ Y FECHA   │      │ REVOLUCIONES │        │
│                 │  │              │      │                 │      │              │        │
│ 📱 Apps Panel   │  │  (Ring 300)  │      │  DIAGRAMA COCHE │      │ (Ring 8000)  │        │
│                 │  │              │      │ (Puertas/Alert) │      │              │        │
│ 🚗 MMI Original │  └──────────────┘      └─────────────────┘      └──────────────┘        │
│                 │  ┌─────────────────────────────────────────────────────────────┐        │
│ ⚙️ Ajustes       │  │             VITALS (CONSUMO / KM)                           │        │
│                 │  └─────────────────────────────────────────────────────────────┘        │
└─────────────────┴────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Especificaciones de Componentes Compose Custom

### A. Barra Lateral de Navegación (`SidebarComponent`)
- **Ancho Fijo:** `360.dp` (~15% del ancho total).
- **Estilo:** Fondo con degradado sutil de negro a gris carbón y un divisor vertical fino en rojo S-Line de `2.dp`.
- **Botones Interactivos:**
  - Área táctil grande de `80.dp` de altura para fácil pulsación en marcha.
  - Efecto de iluminación en el borde izquierdo del botón seleccionado.
  - **Acciones:**
    * **Waze:** Lanza la aplicación `com.waze` mediante el gestor de paquetes.
    * **Apps:** Despliega un App Drawer moderno superpuesto en formato grid.
    * **MMI:** Ejecuta el intent nativo de Szchoiceway para conmutar a la entrada analógica original de Audi.
    * **Ajustes:** Lanza la aplicación de ajustes de Android.

### B. Anillos de Progreso (`ProgressRingIndicator`)
Componentes gráficos premium personalizados dibujados mediante la API `Canvas` de Compose para asegurar el máximo rendimiento gráfico a 60 fps y un look de Virtual Cockpit:
- **Estructura Visual:** Un arco circular incompleto (ángulo de barrido de `270°` con apertura inferior de `135°` a `45°`) con fondo interior de degradado radial oscuro (`0xFF14141A` a `0xFF0C0C0F`) para dar sensación de profundidad mecánica.
- **Marcas de Escala (Radial Ticks):** 20 marcas radiales iluminadas según el valor actual, diferenciando subdivisiones principales y secundarias (mayor/minor ticks).
- **Anillo de Velocidad (Izquierda):**
  - Valor Máximo: `300` km/h.
  - Grosor de línea: `10.dp` con arco de brillo de fondo de `14.dp` para un efecto de halo de neón.
  - Color activo: Azul neón/cian deportivo (`0xFF00E5FF`).
- **Anillo de RPM (Derecha):**
  - Valor Máximo: `8000` RPM.
  - Grosor de línea: `10.dp`.
  - Color activo: Rojo deportivo S-Line (`0xFFE30A17`). Al superar las `6500` RPM (zona roja), el arco de neón entra en un modo de destello parpadeante (alerta de cambio).
  - **Indicador de Marcha Manual:** Centrado en el interior del dial de RPM, muestra la relación de cambio real (`1` a `6`, `N`, o `R`) en una tipografía S-Line Red gigante de alta legibilidad.

### C. Diagrama de Puertas Abiertas (`CarDoorsDiagram`)
Ubicado en el centro del Dashboard para un balance simétrico:
- **Silueta del Vehículo:** Dibuja mediante vectores dinámicos (`Path` de Compose) el contorno cenital detallado de un Audi A5 Coupe deportivo con ensanches de aleta estilo Quattro, retrovisores angulares y lunas de cristal oscuro.
- **Ópticas de Iluminación:** Faros delanteros dibujados en blanco LED Matrix y ópticas traseras en rojo S-Line brillante para mayor realismo.
- **Zonas de Alerta Radiantes (MMI Style):** Basándose en la telemetría, las puertas que estén abiertas no solo dibujan la línea física de la puerta rotada, sino que proyectan un haz degradado de color rojo brillante (`SLineRed` a transparente) hacia el exterior para indicar de forma instantánea al conductor qué zona requiere atención.

### D. Panel de Datos de Chasis (`VitalsComponent`)
Ubicado en la zona inferior central:
- **Climatización:** No se muestra mientras no exista una fuente de datos validada
  para temperatura seleccionada, modo y ventilador.
- **Testigos Físicos:** Fila horizontal de iconos con los testigos del cinturón de seguridad y freno de mano activo.
- **Consumo y Kilómetros:** Datos del odómetro total, autonomía restante en kilómetros, consumo medio del viaje en L/100 y temperatura del refrigerante del motor.

---

## 4. Dirección visual vigente y comportamiento responsive

### Dirección OEM Futuristic

La referencia aprobada es `design-concepts/oem-futuristic-final-2400x896.png`.
Su implementación conserva el mapa claro y sustituye los degradados decorativos
por superficies planas de grafito, anillos concéntricos, bordes de titanio y
sombras localizadas.

* Velocidad y RPM comparten diámetro, centro vertical, tamaño tipográfico y
  baseline.
* La escala de velocidad termina en 280 km/h y se etiqueta cada 40 km/h.
* La escala de RPM termina en 6000 y se etiqueta de 0 a 6.
* Los ticks menores, medios y mayores tienen jerarquías ópticas distintas.
* Las etiquetas de escala usan 14 sp: los valores aún no alcanzados son
  blancos al 90 % de opacidad y cambian a cian/negrita cuando el valor actual
  alcanza su tramo.
* El progreso utiliza un arco cian preciso; el halo queda limitado al propio
  arco, sin contaminar mapa ni fondo.
* La marcha sigue siendo un overlay independiente bajo las RPM.
* Cabecera y pie ocupan ambos el 12,5 % de la altura, comparten 28 dp de padding
  horizontal y se presentan como superficies sin contorno. El cockpit no tiene
  un marco global.
* Las esferas no dibujan contornos exteriores claros. Su corona grande es negra
  y se funde con el lateral; el núcleo pequeño usa grafito claro para separar la
  lectura central y un contorno cromado de 0,7 dp al 50 % compartido con las
  barras, sin recuperar el aspecto de reloj enmarcado.
* Los accesos superiores no muestran caja, relleno ni contorno en reposo; una
  placa grafito aparece únicamente al pulsar o arrastrar. La cabecera y el pie
  usan negro absoluto, sin borde, para prolongar ópticamente el marco del
  dispositivo. Cada separador superior conserva el mismo margen a ambos lados
  respecto a la silueta real de cada icono. Cada pictograma ocupa exclusivamente
  su propio canvas visible —sin padding ni ancho mínimo interno—; `CommandSurface`
  añade el único margen externo común de 16 dp por lado, por lo que el espacio
  icono–divisor es constante y la zona pulsable no fuerza celdas visuales fijas.
  Los pictogramas personalizados extienden su trazo hasta el límite técnico del
  canvas (medio grosor de línea) para no introducir aire propio. No se dibuja una
  línea entre las barras y el área central: añadirla hace más
  evidente el marco físico del dispositivo, por lo que debe mantenerse ausente
  salvo que se reevalúe conscientemente ese efecto. Hora, fecha, temperatura y
  valores del pie se muestran en blanco puro; sólo una condición de alarma
  conserva el rojo semántico. Los pictogramas de la
  cabecera, incluidos los aros Audi, son blancos; el Asistente IA conserva el
  color propio de su icono y sólo aparece cuando está activado.
* Se descartó el marco cromado experimental de cabecera y pie tras validarlo en
  el vehículo: acentuaba el marco físico de la pantalla. Las barras se mantienen
  en negro absoluto y sin contorno.
* Las etiquetas del pie usan blanco al 50 % sobre el fondo negro. Los valores
  permanecen en blanco puro, creando una jerarquía legible sin perder contraste.
* El mapa ocupa todo el ancho del área central, por debajo de las esferas, y no
  recibe filtros, tintes, degradados ni cambios de escala. Una capa Compose
  independiente sustituye los antiguos cortes verticales por dos recortes
  semicirculares negros alineados con los instrumentos.

La implementación se validó en el emulador con el perfil real `2400 × 896` a
320 dpi. La captura local `oem-futuristic-implementation-emulator-2400x896.png`
confirma que las cifras
comparten altura, las escalas no invaden el mapa y las barras delimitan
exactamente su área.

La referencia visual del cuadro panorámico se utiliza como **dirección artística**, no
como una plantilla que se deba copiar literalmente. Los datos y acciones descritos
en este proyecto siguen siendo obligatorios.

La composición vigente abandona la barra lateral fija de 360 dp. En una pantalla
física de 2400x900 configurada a 420 dpi, ese ancho ocupaba aproximadamente el 40 %
de la imagen y dejaba el dashboard recortado. La pantalla principal se distribuye
ahora mediante proporciones calculadas con `BoxWithConstraints`:

* Cabecera negra compacta: fecha y hora, hasta siete accesos directos y
  temperatura exterior.
* Zona principal continua: velocidad desplazada hacia el extremo izquierdo,
  mapa panorámico en el centro y RPM hacia el extremo derecho. Las esferas no
  muestran los rótulos redundantes
  «VELOCIDAD» y «REVOLUCIONES».
* Banda inferior modular: tiempo desde la primera velocidad positiva, distancia
  recorrida en la sesión, consumo, autonomía, combustible con indicador de diez
  segmentos y
  odómetro. Los valores se muestran sin unidades, se separan visualmente y
  comparten el mismo padding lateral; los cuatro testigos ocupan el espacio
  flexible restante entre combustible y odómetro como un único grupo, sin
  separadores propios.
  Sus etiquetas ya aportan el contexto y se evita ruido visual (`6,1`, `24`,
  `393`, `220.409`). El contenido completo se desplaza
  ligeramente hacia abajo para conservar aire respecto al límite continuo del
  mapa sin aumentar de nuevo la altura de la banda. Los decimales de la banda
  respetan el idioma configurado; únicamente las RPM conservan deliberadamente
  el punto como separador decimal.
  Cada segmento de combustible está completamente encendido o apagado; nunca se
  representa una fracción parcial de un bloque. Con 2–4 segmentos disponibles
  los bloques activos son amarillos; sólo el último segmento es rojo; desde 5
  son cian.
  El orden de todos los bloques se puede cambiar mediante pulsación larga y
  arrastre, y queda guardado localmente. Cada bloque de dato conserva su ancho
  natural; únicamente el bloque agrupado de testigos absorbe el ancho sobrante
  (`weight(1f)`) sin importar su posición dentro de la barra.
* La fecha y la hora forman un único bloque. Los accesos superiores muestran sólo
  iconos; el acceso a Aplicaciones utiliza una cuadrícula 2 × 2 de trazo fino y
  el acceso a Recientes abre la vista nativa de Android mediante la acción global
  de accesibilidad, mientras que MMI utiliza los cuatro aros Audi. El orden de
  izquierda a derecha es: Navegación, Aplicaciones, Ajustes del launcher, Ajustes
  del dispositivo, Recientes y Audi MMI. Los iconos conservan el mismo
  tratamiento blanco —incluido Navegación— y sus áreas táctiles invisibles son
  10 dp más anchas que altas. Separadores finos mantienen el ritmo visual sin
  reducir el área de pulsación.
  Ajustes del launcher utiliza controles deslizantes; Ajustes del dispositivo,
  un engranaje. Se evita la llave inglesa para que ambas acciones no se confundan.
* La cabecera se divide en tres zonas de idéntico ancho. Fecha/hora se alinean a
  la izquierda de la primera, las acciones se centran en la pantalla mediante la
  segunda y la temperatura exterior se alinea a la derecha de la tercera.
* El centro del escenario no contiene una representación del vehículo ni
  testigos. Puertas, cinturón, freno de mano y luces se sitúan en una fila horizontal
  centrada en la barra inferior. Sus pictogramas siguen la geometría de los
  testigos OEM, se centran dentro de áreas idénticas, permanecen atenuados en
  estado normal y se iluminan en rojo cuando existe una alerta.
* El mapa sigue siendo el lienzo rectangular de toda la zona comprendida entre
  cabecera y banda inferior. Una capa independiente dibuja dos máscaras
  semicirculares exactamente alineadas con las esferas. No modifica MapLibre ni
  aplica degradados sobre la cartografía.
* La cartografía vectorial permite modo automático, claro u oscuro. El modo
  automático sigue las luces del coche mediante `KSW_DATA_SMALL_LIGHT_ON` y
  usa el modo nocturno de Android como respaldo. Las luces ya activas al arrancar
  aplican el modo oscuro de inmediato; las activaciones posteriores deben durar
  60 segundos y su apagado devuelve el modo claro inmediatamente. En claro se
  puede elegir OpenFreeMap Positron, Liberty o Bright. En modo nocturno adapta
  el estilo Dark con una paleta de mayor contraste para carreteras, edificios,
  agua y rótulos.
  El marcador GPS anima su desplazamiento
  y el mapa admite zoom mediante pellizco con dos dedos.
* El marcador de posición es una flecha de navegación compacta dentro de un
  aro transparente reducido, sin relleno negro ni halo exterior. La
  flecha conserva el rumbo vertical exacto y gana volumen mediante una base
  inferior oscura y una arista iluminada, sin inclinar el eje direccional. Es cian en
  estado normal, amarillo cuando falta señal GPS y rojo cuando no hay red. La
  ausencia de red tiene prioridad si coinciden ambos estados.
* Las escalas máximas del vehículo real son 280 km/h y 6.000 rpm.
* La zona roja del tacómetro comienza exactamente en 4.500 rpm y continúa sin
  interrupciones hasta el final de la escala, en 6.000 rpm.
* La marcha se integra exclusivamente dentro de la esfera derecha de RPM, debajo
  de su valor principal, pero como una lectura separada mediante margen. Se
  muestra sólo el valor, sin la palabra «MARCHA», y no se repite en el centro ni
  en la banda inferior. La unidad del
  tacómetro se simplifica a `x1000`, sin el sufijo `r/min`.
* Por debajo de 900 rpm el estimador muestra `N`. Desde 900 rpm calcula la marcha;
  en movimiento lento hacia delante (1–10 km/h), muestra primera tras la misma confirmación de dos muestras
  empleada en el resto de relaciones. Esta rama evita mostrar `N` en garajes y
  atascos, donde la cuantización de la velocidad invalida el cálculo por ratio.
* El valor de RPM comparte centro y línea horizontal con el valor de velocidad.
  La marcha se dibuja como overlay inferior para que no desplace las RPM. El
  decimal del tacómetro usa una tipografía proporcional compacta.
* El testigo de puertas muestra `ABIERTA` únicamente cuando corresponde, nunca
  `ALERTA`. El cinturón no muestra `OK` cuando está abrochado.
* Los testigos centrales se representan exclusivamente mediante iconos, sin
  labels ni textos de estado.
* Puerta, cinturón y freno de mano usan pictogramas sólidos de automoción. La
  puerta utiliza una silueta cenital sólida con las
  puertas abiertas y pocos trazos interiores; cinturón, freno de estacionamiento
  y luces proceden de Material Design Icons. Por su proporción vertical, el
  testigo de puertas se renderiza a 30 dp desde el SVG ancho de 633 × 717;
  únicamente la carrocería central usa una capa vectorial propia con la
  proporción ensanchada del SVG de referencia. Las puertas activas son recortes
  del mismo vector completo, por lo que nunca se escalan ni cambian de silueta. Su
  SVG se divide en carrocería y cuatro capas de puertas alineadas: ante cualquier
  apertura la carrocería se ilumina en rojo y sólo permanecen rojas las puertas
  cuyos bits estén activos en `DoorStatus`; las puertas cerradas se repintan con
  el color inactivo. Una apertura exclusiva del maletero ilumina la carrocería
  sin activar ninguna puerta lateral. El cinturón se renderiza a 34 dp para
  equilibrar su mayor masa visual; freno y luces se mantienen a 38 dp. Los
  testigos se separan uniformemente 12 dp según sus límites reales, sin cajas
  de ancho fijo. El cinturón representa ocupante y
  banda, y el freno utiliza el símbolo automovilístico completo `(P)`. Los
  recursos conservan sus proporciones sin deformar sus trazados.
* El testigo de luces usa el pictograma vectorial de luz de cruce de la misma
  familia. Se ilumina en verde cuando el modo oscuro efectivo del mapa está
  activo, tanto por detección automática como por selección manual en Ajustes.
* La fila de testigos se centra dentro del espacio libre delimitado por el final
  de `AUTONOMÍA` y el comienzo de `ODÓMETRO`; no se centra respecto a la pantalla
  completa ni utiliza un desplazamiento visual fijo.
* Los datos del pie se leen de izquierda a derecha como `TIEMPO`, `VIAJE`,
  `CONSUMO`, `PARCIAL`, `AUTONOMÍA` e indicador del depósito. `VIAJE` representa
  la distancia de la sesión actual y `PARCIAL` la distancia acumulada desde el
  último repostaje.
* Velocidad y RPM usan la misma familia y grosor tipográfico para sus valores.
  Las RPM usan siempre un punto como separador decimal, independientemente del
  idioma o región configurados en Android.
* El texto de RPM se redondea únicamente para presentación. La posición del
  arco se calcula con el valor CAN entero sin redondear y se suaviza mediante
  una respuesta críticamente amortiguada, evitando saltos entre décimas y
  reinicios de animación a la frecuencia de 10 Hz del EventCenter.
* En el App Drawer, `VOLVER` ocupa el extremo izquierdo por proximidad al
  conductor y utiliza padding amplio para poder accionarlo sin precisión fina.
  Su cabecera reutiliza exactamente la altura calculada para la barra superior
  principal. El contenido comienza inmediatamente bajo esa cabecera, en la
  misma coordenada vertical en la que empiezan mapa y relojes en el dashboard;
  no existe un margen superior intermedio. Una pulsación corta sobre una
  aplicación la abre; una pulsación mantenida abre su ficha nativa de
  información en los ajustes de Android.
* La marcha utiliza un badge circular oscuro con doble anillo de titanio y cian.
  Es un overlay de 68 dp, desplazado 102 dp bajo el centro de la esfera, con un
  número de 44 sp. No modifica la posición de las RPM, que
  permanece alineada verticalmente con la velocidad.
* El clima no se muestra porque el registro disponible no proporciona temperatura
  seleccionada, modo ni ventilador fiables. La autonomía permanece de forma
  provisional mientras se valida en la unidad.
* Cabecera y pie no usan alturas fijas en dp: ambos ocupan el 12,5 % de la
  altura útil, con el mismo padding y la misma presencia visual. Los botones
  conservan el 9,5 % para mantener su área táctil. Los pictogramas superiores
  ocupan aproximadamente el 70 % del botón,
  con menos margen interior pero sin modificar el tamaño ni la posición de sus
  áreas táctiles. Cada familia aplica además una corrección de escala óptica.
  En reposo no se dibuja ningún contenedor; el feedback grafito aparece en menos
  de 250 ms durante pulsación o arrastre.
* El marco exterior global y el gran redondeo del contenedor raíz se han
  eliminado. Cabecera y pie son dos superficies independientes sin contorno,
  con radio exterior de 8 dp; las esquinas en contacto con el mapa son rectas
  para que la unión no deje huecos ni revele el fondo. El mapa llega directamente
  hasta ambos.
* El disco oscuro situado detrás de cada reloj mide un 112 % del diámetro del
  indicador. La escala, los textos y la geometría funcional conservan su tamaño;
  sólo crece esta base visual, dibujada por debajo de cabecera y pie. Su medida
  cuadrada es obligatoria incluso cuando excede el ancho asignado por la fila,
  evitando que las restricciones del layout la deformen en un óvalo. Tanto esta
  base como el relleno interior del indicador usan negro puro opaco (`#000000`).
* El acceso MMI reproduce los cuatro aros Audi como cuatro círculos idénticos,
  alineados y solapados. No se debe comprimir horizontalmente el lienzo ni
  sustituir los aros por elipses verticales.
* Cada esfera emplea un disco y un único contorno exterior, evitando el efecto
  de líneas paralelas. Las cifras de escala usan 14 sp y pasan de gris a cian,
  con mayor peso, a medida que el valor medido alcanza cada marca.
  El velocímetro muestra valores de 0 a 280 km/h en intervalos de 20 km/h.
  No existe un círculo decorativo alrededor de la lectura central: arco,
  marcas y cifras forman una única escala próxima al contorno exterior.
  El diámetro de las esferas ocupa exactamente el espacio vertical entre
  cabecera y pie.
* La fecha usa el formato largo correspondiente al idioma y región configurados
  en Android (`miércoles, 2 de septiembre de 2026`, por ejemplo) y comparte una
  línea con la hora mediante un punto medio pequeño y atenuado. La hora conserva
  el tamaño principal; la fecha utiliza el 62 % y menor peso para caber dentro
  del tercio izquierdo. Todo el bloque queda centrado verticalmente.
* Los ajustes reutilizan la misma cabecera, altura y botón `VOLVER` del panel de
  Aplicaciones. Ambos paneles secundarios usan fondo negro absoluto, incluida
  su cabecera, sin contorno ni matiz metálico, que integra `VOLVER` y el título.
  Su contenido también comienza inmediatamente bajo la cabecera para conservar
  la misma retícula vertical al alternar entre dashboard, Aplicaciones y
  Ajustes.
  Ajustes distribuye el contenido en tres paneles visualmente uniformes.
  Los paneles no necesitan títulos propios ni desplazamiento vertical. El
  primero reúne color, estilo, límite de caché y diagnóstico. Los cinco límites
  de caché comparten una única fila.
  Todas las opciones de cada ajuste reparten por igual el ancho disponible y
  centran su texto. Los selectores segmentados usan una cápsula interior que se
  desplaza con una animación breve hasta la opción elegida y cada segmento se
  expone como opción de radio accesible. El segundo panel contiene únicamente
  información del mapa en filas de una línea con formato `ETIQUETA: valor`. El
  tercero agrupa almacenamiento y mantenimiento: ocupación de caché frente al
  límite seleccionado, tamaño de logs y sus acciones de borrado. Selectores y
  botones de mantenimiento comparten exactamente la misma altura compacta. Este
  último panel también identifica el APK mediante fecha y hora de compilación,
  fecha y hora de instalación o actualización y `versionName`, en ese orden
  visual: versión, compilación e instalación. Las fechas respetan el formato
  regional del dispositivo y las horas se muestran siempre en formato de 24
  horas. La compilación se
  inyecta automáticamente en `BuildConfig` durante cada build y la instalación
  procede de `PackageInfo.lastUpdateTime`; ninguno de estos valores se mantiene
  manualmente. La pestaña de versión incorpora una acción de reinicio con
  confirmación previa; envía exclusivamente la orden nativa de EventCenter que
  ya utiliza el logger y no requiere acceso root. También permite seleccionar
  desde el selector de documentos de Android un APK de actualización. La
  cancelación del selector no altera el estado; antes de abrir el instalador
  nativo, el fichero se copia a caché privada y se comprueba que pertenece al
  paquete de A5 Cockpit. Android conserva la confirmación final de instalación
  y solicita una sola vez el permiso para instalar aplicaciones desde esta
  fuente. No se muestra el nombre del
  fichero de diagnóstico ni
  configuración para asignar el launcher predeterminado.
  los símbolos circulares y diagonales se reducen ligeramente y los horizontales
  se amplían, de modo que su masa visual sea uniforme aunque sus `viewBox` sean
  diferentes. La calibración se ha realizado con la pantalla real de 2400x896,
  320 dpi y 8 pulgadas documentada en `../architecture/DEVICE.md`.

Los tamaños de esferas y vehículo se derivan del alto disponible. No deben volver
a introducirse anchos estructurales fijos pensados para una densidad concreta.

### Validación visual

Después de cada cambio relevante se debe:

1. Compilar e instalar la variante `debug`.
2. Lanzar `com.lito.a5launcher.MainActivity`.
3. Capturar la pantalla con `adb exec-out screencap -p`.
4. Comparar la captura completa de 2400x896 con la referencia y con la iteración
   anterior, prestando especial atención a recortes, jerarquía, contraste y
   alineación.
5. Guardar la captura final fuera del repositorio para su revisión. Este paso
   es obligatorio para cualquier cambio visual, aunque las pruebas automáticas
   y la compilación ya hayan finalizado correctamente.

El script `scripts/emulator.sh` fija tanto el framebuffer como la densidad lógica
en `2400x896 @ 320 dpi`, coincidiendo con `../architecture/DEVICE.md`.

Las capturas de trabajo se almacenan en el directorio local ignorado
`a5-launcher/screenshots/`. Las capturas finales seleccionadas para la
documentación pública se guardan en `docs/screenshots/`. La captura
`iteration-01.png` corresponde a la primera composición panorámica responsive.
La validación de los aros y barras refinados se conserva como captura local
`oem-refined-rings-bars-emulator-2400x896.png`.
La geometría final con barras independientes y esferas a toda altura se
validó a 2400x896 y se conserva como
`oem-independent-bars-full-height-dials-2400x896.png`.
La variante de producción con el mapa a todo lo ancho y las esquinas interiores
rectas superó pruebas, lint y optimización R8 el 29 de julio de 2026.
La composición sin contornos de barra y con bases de reloj ampliadas al 112 %
superó la misma validación de producción el 29 de julio de 2026.
Su validación visual a 2400x896 se conserva como captura local
`oem-borderless-bars-expanded-dial-backplates-2400x896.png`.
La corrección geométrica posterior se validó en
`oem-circular-backplates-faithful-audi-rings-2400x896.png`: las bases mantienen
una relación 1:1 incluso al exceder su celda y el acceso MMI muestra cuatro aros
Audi circulares sin compresión horizontal.
La aplicación de negro puro opaco a cabecera, pie y rellenos de reloj se validó
en `oem-opaque-black-bars-dials-2400x896.png`.
El cambio de los valores de cabecera y pie a blanco puro se validó en
`oem-white-header-footer-values-2400x896.png`.
La cabecera completamente blanca —valores y cinco pictogramas— se validó en
`oem-white-header-icons-values-2400x896.png`.
Los contornos blancos uniformes de los cinco botones se validaron en
`oem-white-header-button-borders-2400x896.png`.
El ajuste final de contraste —contornos al 48 % y etiquetas inferiores al 68 %—
se validó en `oem-balanced-header-borders-footer-labels-2400x896.png`.
La unificación posterior de ambos elementos al 50 % se validó en
`oem-unified-secondary-contrast-50-percent-2400x896.png`.
