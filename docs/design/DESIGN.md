# Especificación de diseño y UI - Audi A5 Launcher (2400x896)

Este documento detalla las especificaciones de diseño, tokens visuales y
estructura de los componentes Jetpack Compose, calibrados para la superficie
útil real de **2400x896 píxeles** del Navifly.

## 1. Dirección visual y comportamiento responsive

### Dirección OEM Futuristic

La entrada progresiva del launcher dura 2.200 ms y se reproduce una vez por
proceso: primero aparecen las esferas y después el resto del contenido. El
estado inicial ya es el fotograma oculto, antes de ejecutar el efecto Compose,
para evitar mostrar el cuadro completo y volver a negro al arrancar. Al volver
al launcher dentro del mismo proceso se muestra directamente el cuadro.

La referencia aprobada fue una imagen de concepto que no se versiona en este
repositorio. Su implementación conserva el mapa claro y sustituye los degradados decorativos
por superficies planas de grafito, anillos concéntricos, bordes de titanio y
sombras localizadas.

* Velocidad y RPM comparten diámetro, centro vertical, tamaño tipográfico y
  baseline.
* La escala de velocidad termina en 240 km/h y se etiqueta cada 20 km/h
  (`SPEED_DIAL_MAX_KMH`).
* La escala de RPM termina en 6000 y se etiqueta de 0 a 6.
* Los ticks menores, medios y mayores tienen jerarquías ópticas distintas.
* Las etiquetas de escala usan 14 sp: los valores aún no alcanzados son
  blancos al 90 % de opacidad y cambian a cian/negrita cuando el valor actual
  alcanza su tramo.
* El progreso utiliza un arco cian que se aviva hacia el valor actual: empieza
  en un cian profundo y llega al cian pleno en la punta, que lleva un pequeño
  destello y un punto blanco. El halo crece hacia la punta y queda limitado al
  propio arco, sin contaminar mapa ni fondo.
* En RPM la zona roja (desde 4.500) siempre es roja: arco, marcas y cifras 5 y
  6. Al entrar en ella el arco rojo gana un halo suave y la punta del progreso
  pasa a rojo; las marcas alcanzadas se encienden por completo.
* La marcha sigue siendo un overlay independiente bajo las RPM.
* Cabecera y pie ocupan ambos el 12,5 % de la altura, comparten 28 dp de padding
  horizontal y se presentan como superficies sin contorno. El cockpit no tiene
  un marco global.
* Las esferas no dibujan contornos exteriores claros. Su corona grande es negra
  y se funde con el lateral; el núcleo pequeño usa grafito con un degradado
  radial muy suave, más claro arriba, para separar la lectura central, un
  contorno cromado de 0,7 dp al 50 % y un reflejo suave en la parte superior
  izquierda de ese contorno, sin recuperar el aspecto de reloj enmarcado.
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
* En el tema Clásico el mapa ocupa todo el ancho del área central, por debajo de las esferas, y no
  recibe filtros, tintes, degradados ni cambios de escala. Una capa Compose
  independiente sustituye los antiguos cortes verticales por dos recortes
  semicirculares negros alineados con los instrumentos.

La implementación se validó en el emulador con el perfil real `2400 × 896` a
320 dpi, confirmando que las cifras
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
  odómetro. Los valores se muestran sin unidades, se separan con divisores que
  se desvanecen en sus extremos y comparten el mismo padding lateral; los cuatro testigos ocupan el espacio
  flexible restante entre combustible y odómetro como un único grupo, sin
  separadores propios.
  Sus etiquetas ya aportan el contexto y se evita ruido visual (`6,1`, `24`,
  `393`, `220.409`); se volvió a probar con unidades y se descartó de nuevo.
  El contenido completo se desplaza
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
  izquierda a derecha es, por defecto: Asistente IA (sólo si está activado),
  Navegación, Aplicaciones, Ajustes del launcher, Ajustes del dispositivo,
  Recientes y Audi MMI; el usuario puede reordenarlos con pulsación larga y el
  orden (`top_command_order`) es común a todos los temas. Los iconos conservan el mismo
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
  flecha conserva el rumbo vertical exacto durante el seguimiento y gana volumen mediante una base
  inferior oscura y una arista iluminada, sin inclinar el eje direccional. Es cian en
  estado normal, amarillo cuando falta señal GPS y rojo cuando no hay red. La
  ausencia de red tiene prioridad si coinciden ambos estados.
  Al arrastrar o hacer zoom, el marcador continúa ligado a la coordenada GPS
  y muestra el rumbo respecto a la cámara. Al alejar, se reduce progresivamente
  desde 38 dp hasta un mínimo de 19 dp. «Volver a la Posición Actual» restaura
  los 38 dp y el seguimiento, conservando el zoom elegido. El marcador sigue
  actualizando su posición mientras se explora el mapa.
* Las escalas máximas de las esferas son 240 km/h y 6.000 rpm.
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
  familia. Se ilumina en verde en cuanto el coche enciende las luces de posición
  (`KSW_DATA_SMALL_LIGHT_ON`) y se apaga con ellas. No depende del mapa: ni el
  modo claro u oscuro fijado en Ajustes ni la espera de 60 segundos del modo
  automático cambian lo que muestra.
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
  El velocímetro muestra valores de 0 a 240 km/h en intervalos de 20 km/h.
  No existe un círculo decorativo alrededor de la lectura central: arco,
  marcas y cifras forman una única escala próxima al contorno exterior.
  El diámetro de las esferas ocupa exactamente el espacio vertical entre
  cabecera y pie.
* La fecha usa el formato largo correspondiente al idioma y región configurados
  en Android (`miércoles, 2 de septiembre de 2026`, por ejemplo) y comparte una
  línea con la hora mediante un punto medio pequeño y atenuado. La hora conserva
  el tamaño principal; la fecha utiliza el 62 % y menor peso para caber dentro
  del tercio izquierdo. Todo el bloque queda centrado verticalmente.
* Los ajustes reutilizan la misma cabecera, altura e icono de retorno del panel
  de Aplicaciones. Ambos paneles secundarios usan fondo negro absoluto, incluida
  su cabecera, sin contorno ni matiz metálico, que integra el retorno y el título.
  El icono ocupa 28 dp dentro de una zona táctil circular de 40 dp. La superficie
  usa el propio cian al 10 % en modo oscuro y al 24 % en modo claro, aumentando
  su presencia con luz diurna sin competir con el título durante la noche.
  Su contenido también comienza inmediatamente bajo la cabecera para conservar
  la misma retícula vertical al alternar entre dashboard, Aplicaciones y
  Ajustes.
  Ajustes se abre en la última pestaña consultada (se conserva también tras
  reiniciar; la primera vez, `Viajes`). El orden es `Viajes`, `Mapa`,
  `Asistente`, `Registros`, `Temas` y `Sistema`. Las pestañas son píldoras con icono integradas en la
  propia cabecera, a la derecha del título, para que el contenido disponga de
  toda la altura restante; ninguna pestaña necesita desplazamiento vertical salvo
  sus listas.
* El sistema visual de Ajustes vive en `SettingsComponents.kt` y
  `SettingsIcons.kt` y es común a todas las pestañas:
  * Fondo negro absoluto, como el cockpit. Las tarjetas son superficies planas
    de grafito (`#0D1317`) con borde de 1 dp, radio de 14 dp, padding de 16 dp
    y 12 dp de separación. El cian del dashboard (`#00DDF4`) es el único acento;
    el rojo queda reservado a acciones destructivas y el verde/ámbar a puntos de
    estado.
  * Cada tarjeta empieza con un título de sección: pictograma cian de 18 dp,
    texto en mayúsculas de 12 sp y un hueco opcional a la derecha para
    contadores o resúmenes (`3 de 3`, `Ficheros: 0 · 0 B`).
  * Los pictogramas son trazos finos propios sobre una retícula de 24 unidades,
    coherentes con los iconos de la cabecera del dashboard, sin añadir el
    conjunto extendido de Material Icons al APK.
  * Selectores segmentados de 36 dp con cápsula cian animada y texto en
    minúsculas; botones de 40 dp con icono, contorno y relleno tenue del tono
    de su acción. La acción principal de una tarjeta (Guardar, Instalar) usa
    relleno cian completo.
  * Los campos (fechas, modelo, clave API) muestran etiqueta y valor dentro de
    una caja elevada de 54 dp; los datos de solo lectura usan filas
    `etiqueta … valor` separadas por líneas finas, o filas de dos líneas
    (etiqueta encima del valor) cuando el ancho de la tarjeta es reducido.
  * Ningún texto baja de 10 sp; los valores principales usan 12–13 sp.
  * Los controles inferiores de las tarjetas contiguas quedan alineados en la
    misma línea base.
* `Viajes` reserva un tercio para activar el historial y filtrar por tipo o
  fechas, y dos tercios para la lista de Viajes y Parciales. Cada fila muestra
  icono, periodo, distancia y duración en columnas alineadas, y se despliega
  para ver el detalle. Las primeras filas son el Viaje y el Parcial actuales,
  marcados con una barra cian. La descarga genera un JSON con las filas visibles.
* `Mapa` reparte cuatro tarjetas iguales: apariencia (color, estilo y límite de
  caché), estado (mapa, red, GPS, coordenadas y proveedor en filas de dos
  líneas con puntos de color, sin desplazamiento),
  caché y logs de diagnóstico, y puntos de interés.
* `Sistema` muestra aplicación (versión, compilación, instalación e idioma),
  actualización desde GitHub o APK local, y dispositivo (modelo, versión de
  Android y reinicio con confirmación previa mediante EventCenter, sin root).
  Las fechas respetan el formato regional y las horas usan 24 horas.
  La calibración óptica de los pictogramas (símbolos circulares y diagonales
  algo menores, horizontales algo mayores) iguala su masa visual aunque sus
  `viewBox` sean diferentes. Se ha realizado con la pantalla real de 2400x896,
  320 dpi y 8 pulgadas documentada en `../architecture/DEVICE.md`.

Los tamaños de esferas y vehículo se derivan del alto disponible. No deben volver
a introducirse anchos estructurales fijos pensados para una densidad concreta.

### Temas de la home

La home admite ocho temas (Clásico, Horizonte, Facetas, Telemetría,
Submarino, Luna, Galaxia y Aventura) que se eligen en Ajustes → Temas, una cuadrícula de tarjetas
con la miniatura y el nombre centrado de cada uno. Las reglas de cada tema, los márgenes
comunes y la reordenación por tema están en
[`../feature-home-themes/README.md`](../feature-home-themes/README.md).
Clásico sigue siendo la referencia de esta sección.

### Paneles de estadísticas

Al abrir las estadísticas de viaje, parcial o total desde el pie, el panel
sustituye al mapa en todos los temas (ocupa su zona con el fondo, el acento y
las cifras del tema; véase `../feature-home-themes/README.md`). Los registros
anteriores se consultan en Ajustes → Viajes.

### Validación visual

Después de cada cambio relevante se debe:

1. Compilar e instalar la variante `debug`.
2. Lanzar `com.lito.a5launcher.MainActivity`.
3. Capturar la pantalla con `adb exec-out screencap -p` (o
   `scripts/capture-emulator.sh`, véase [CAPTURES.md](CAPTURES.md)).
4. Comparar la captura completa de 2400x896 con la iteración anterior,
   prestando especial atención a recortes, jerarquía, contraste y alineación.
5. Guardar la captura final fuera del repositorio para su revisión. Este paso
   es obligatorio para cualquier cambio visual, aunque las pruebas automáticas
   y la compilación ya hayan finalizado correctamente.

El script `scripts/emulator.sh` fija tanto el framebuffer como la densidad lógica
en `2400x896 @ 320 dpi`, coincidiendo con `../architecture/DEVICE.md`.

Las capturas de trabajo se almacenan en el directorio local ignorado
`screenshots/` de la raíz del repositorio. Las capturas finales seleccionadas
para la documentación pública se guardan en `docs/screenshots/` (versión en
inglés) y `docs/screenshots/es/` (versión en español).
