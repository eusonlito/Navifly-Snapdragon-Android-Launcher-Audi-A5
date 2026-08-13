# Plan de implementación — Dashboard OEM Precision GT

## Objetivo

Evolucionar el dashboard vigente hacia un acabado OEM inspirado en las dos
referencias Precision GT aprobadas, sin alterar su geometría, información ni
comportamiento funcional. La implementación combina la limpieza de la segunda
referencia con la profundidad material localizada de la primera.

## Límites de alcance

- La transformación visual se concentra en la pantalla principal del launcher.
- Ajustes y Aplicaciones conservan sus componentes, pero alinean la altura de
  cabecera y el inicio del contenido con la barra superior y el área central
  del dashboard para evitar saltos al cambiar de panel.
- No se cambia telemetría, cálculos, navegación, Asistente IA, MapLibre,
  estilos cartográficos, caché, puntos de interés ni persistencia.
- Los relojes mantienen diámetro, centro, posición y escala actuales.
- No se añaden dependencias, preferencias ni recursos raster.

## Dirección visual

### Instrumentos

- Corona grande en negro puro, sin contorno exterior, para fundirse con el
  lateral negro; núcleo interior en grafito claro para separar la lectura.
- El núcleo conserva un contorno cromado de 0,7 dp al 50 % de opacidad con la
  misma paleta metálica de los marcos experimentales de cabecera y pie.
- Un único canal técnico y profundidad localizada, sin grandes degradados.
- Escala OEM condensada: valores alcanzados en cian y pendientes en blanco al
  90 %.
- Arco activo en tres capas: canal oscuro, halo corto y núcleo cian con remate
  preciso.
- Velocidad de 0 a 280 km/h; RPM de 0 a 6.000 y zona roja desde 4.500.
- La marcha continúa integrada bajo las RPM sin desplazar su valor.

### Integración del mapa

- El mapa conserva toda su superficie y proyección.
- Dos máscaras semicirculares coinciden con la geometría real de los relojes.
- No se aplica degradado, sombra ni filtro sobre el mapa. La unión se limita al
  recorte semicircular negro que sigue exactamente el contorno exterior de cada
  instrumento.
- El centro cartográfico no recibe filtro, escala ni tinte.
- La máscara permanece idéntica en mapa claro y oscuro para no teñir ni alterar
  la cartografía.

### Cabecera y pie

- Cabecera negra en tres zonas iguales, con una repisa central muy discreta.
- Los accesos no muestran contenedor en reposo. Separadores de titanio de baja
  opacidad conservan su ritmo; al pulsar o arrastrar aparece una placa grafito
  temporal.
- El orden por pulsación prolongada se mantiene y persiste como hasta ahora.
- Cabecera y pie no dibujan bordes contra el área central. La ausencia de estas
  líneas es intencionada: evita remarcar el rectángulo útil de la pantalla y
  reduce visualmente la presencia de los marcos físicos del dispositivo.
- El pie mantiene orden, altura y datos. Los bloques de combustible y odómetro
  también quedan delimitados; los cuatro testigos forman un único grupo sin
  separadores internos.

> Prueba temporal en vehículo (12-08-2026): se activa un marco cromado completo
> y redondeado en ambas barras para juzgarlo sobre el marco físico real. Si éste
> vuelve a destacar, se retirará `oemChromeFrame()` y prevalecerá la decisión
> anterior de mantener las barras sin contorno.

### Movimiento

- Autocheque panorámico de 2.200 ms una vez por proceso: contorno, escala,
  profundidad y contenido se revelan en cuatro fases coordinadas. Durante la
  secuencia no se muestran lecturas parciales ni se simulan datos del vehículo.
- Telemetría y mapa arrancan de forma asíncrona por debajo de la presentación;
  el dashboard nunca espera a la red, al GPS ni a los tiles para completarla.
- El arco de velocidad interpola durante 160 ms. El de RPM usa una respuesta
  amortiguada continua para no reiniciar un `tween` con cada trama CAN de 100 ms:
  persigue el entero crudo (por ejemplo, `1923 / 6000`) aunque el número se
  presente redondeado como `1.9`. Ambos números reflejan siempre el dato real.
- La marcha escala hasta 1,10 y refuerza brevemente su doble aro al cambiar.
- Cada testigo realiza un único pulso material al activarse, sin parpadeo
  continuo.

## Arquitectura

1. Centralizar tokens y funciones puras de presentación OEM en un componente
   interno del dashboard.
2. Compartir un único progreso de arranque entre ambos instrumentos.
3. Dibujar la integración cartográfica en una capa Compose entre MapLibre y los
   instrumentos, recibiendo únicamente la geometría de los relojes.
4. Mantener los controles y callbacks existentes; los cambios de cabecera y pie
   son únicamente de presentación.
5. Cachear pinceles, rutas, marcas y texturas con `drawWithCache`.

## Verificación

- Pruebas unitarias para las reglas puras de presentación y reordenación.
- Suite completa `:app:testDebugUnitTest` y compilación `:app:assembleDebug`.
- Emulador Navifly 2400 × 896 a 320 dpi con escenas claro y oscuro.
- Revisar valores canónicos, ralentí, zona roja, testigos, pulsación y
  reordenación de accesos, así como estados sin red/GPS.
- Guardar las capturas de validación fuera del repositorio antes de generar producción.

El autocheque de 2.200 ms y la alineación de Aplicaciones/Ajustes se validaron
en el emulador Navifly. Las evidencias vigentes son
`startup-autocheck-2400x896.mp4`, `apps-layout-aligned-2400x896.png` y
`settings-layout-aligned-2400x896.png`,, generadas como artefactos locales no versionados.

La validación final del 12-08-2026 cubre compilación, pruebas JVM, lint,
capturas clara/oscura y una escena límite de `155 km/h` y `5.500 rpm`. La
revisión final también corrige la cancelación de los pulsos de marcha y
testigos, y permite reintentar el barrido inicial si la composición se destruye
antes de completarlo.

## Criterios de aceptación

- Todos los datos y acciones actuales siguen disponibles.
- El mapa no se deforma, no cambia de escala y permanece independiente.
- Los relojes no cambian de tamaño ni posición.
- No hay separadores entre los cuatro testigos del pie.
- No hay placas visibles detrás de los accesos superiores en reposo.
- El resultado funciona de forma coherente en cartografía clara y oscura.
