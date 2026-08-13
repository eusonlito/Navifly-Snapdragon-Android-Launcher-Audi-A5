# Ideas para la siguiente evolución visual

> Documento histórico de exploración visual. La ideación vigente, que incluye
> funcionalidad, interfaz y UX, se mantiene en [IDEAS.md](../IDEAS.md).
> La dirección seleccionada para el dashboard se concreta en
> [Dashboard OEM Precision GT](../feature-oem-dashboard/PLAN.md).

## Objetivo

La siguiente versión debe conservar la claridad y estabilidad actuales, pero
producir una impresión visual más sofisticada: un cuadro diseñado para el coche,
no una aplicación Android colocada en una pantalla grande.

El impacto debe proceder de la composición, profundidad, movimiento y calidad
gráfica. No de añadir información, colores o animaciones sin propósito.

## Contrato de información

Ninguna propuesta puede eliminar, ocultar permanentemente ni dificultar la
lectura de los datos actuales:

* hora y fecha localizadas;
* temperatura exterior;
* accesos a navegación, aplicaciones, MMI, ajustes del vehículo y ajustes del
  launcher;
* velocidad;
* revoluciones;
* marcha;
* mapa, posición y estados de red/GPS;
* consumo medio;
* combustible;
* autonomía;
* odómetro;
* puertas;
* cinturón;
* freno de mano.

Se puede cambiar jerarquía, posición, representación o comportamiento visual,
pero todos estos elementos deben seguir disponibles en la pantalla principal.

## Dirección recomendada: Precision GT

La evolución recomendada combina el lenguaje de un cuadro Gran Turismo con una
interfaz cartográfica limpia. Debe sentirse técnica, rápida y precisa, sin
imitar literalmente un cuadro analógico.

### 1. Esferas con profundidad real

Las esferas pueden evolucionar de discos planos a instrumentos formados por
capas:

1. sombra ambiental muy suave contra el mapa;
2. aro exterior metálico oscuro;
3. canal interior para la escala;
4. iluminación cian rasante sobre el valor activo;
5. cristal virtual con un reflejo casi imperceptible;
6. centro oscuro con textura extremadamente sutil.

El efecto debe construirse con degradados y geometría, evitando imágenes
pesadas. La lectura numérica continúa dominando.

### 2. Escalas vivas

Los arcos pueden reaccionar con más personalidad:

* tramo recorrido sólido y luminoso;
* pequeña cabeza brillante en el extremo del arco;
* marcas principales más visibles que las secundarias;
* aceleración y frenada con interpolación física;
* zona alta de RPM que pase progresivamente de cian a ámbar y rojo;
* breve pulso al cambiar de marcha, sin mover la cifra de RPM.

No deben vibrar ni perseguir cada pequeña fluctuación CAN. Toda animación
necesita filtrado y una duración coherente.

### 3. Mapa integrado como escenario

El mapa puede parecer una superficie situada por debajo de los instrumentos:

* contraste ligeramente reducido bajo los bordes de las esferas;
* sombra de contacto limpia, sin difuminados laterales;
* marcador más refinado, con cuerpo, dirección y halo de precisión;
* edificios, vías principales y agua con jerarquía visual clara;
* transición suave entre tiles y niveles de zoom;
* perspectiva visual ligera, manteniendo coordenadas y geometría correctas;
* estados sin red/GPS integrados en el marcador, sin grandes mensajes que
  tapen el mapa.

La cabecera y el pie siguen siendo los límites verticales exactos.

### 4. Cabecera de una sola pieza

La cabecera actual puede ganar carácter manteniendo sus tres zonas:

* fondo translúcido con una línea inferior cian de baja intensidad;
* hora con mayor presencia que la fecha;
* botones con contenedor más fino y respuesta luminosa al pulsar;
* iconos ajustados por tamaño óptico, no sólo por caja;
* temperatura alineada con el bloque horario;
* pequeñas transiciones de entrada, nunca animación continua.

Los botones deben seguir siendo grandes y fáciles de acertar conduciendo.

### 5. Banda inferior técnica

Los cuatro datos pueden adoptar una presentación más instrumental:

* etiqueta pequeña y espaciada;
* valor numérico de mayor presencia;
* línea o punto cian que marque cada bloque;
* separadores casi invisibles;
* alertas centradas con un espacio propio;
* cambio de color sólo cuando el dato requiera atención.

Se mantienen los valores sin unidades, tal como están ahora.

### 6. Testigos con iluminación material

Puerta, cinturón y freno pueden sentirse como testigos físicos:

* apagados: grabado gris oscuro;
* advertencia: relleno ámbar con halo corto;
* estado crítico, si llegara a existir: rojo controlado;
* encendido y apagado con una transición breve;
* mismo tamaño óptico, trazo y área visual.

## Momentos de impacto

### Arranque

La dirección aprobada es un **autocheque panorámico de instrumentos** de
2.200 ms, ejecutado una sola vez por proceso:

1. se trazan los contornos de ambas esferas;
2. marcas y cifras se recorren de forma sincronizada de 0 a 280 km/h y de
   0 a 6.000 rpm;
3. aparecen la profundidad, los canales cian y la zona roja;
4. entran conjuntamente las lecturas, el mapa, la cabecera y el pie.

La telemetría y MapLibre se inicializan en paralelo y nunca condicionan la
duración del autocheque. La secuencia no inventa valores ni activa testigos, y
si la composición se destruye antes de completarla puede reproducirse de nuevo.

### Cambio de marcha

La marcha puede aumentar brevemente un 8–10 %, iluminar el doble anillo y volver
a reposo. Debe durar menos de 250 ms y no desplazar las RPM.

### Aparición de una alerta

El testigo correspondiente se ilumina y realiza un único pulso. No parpadea
continuamente.

### Pérdida de red o GPS

El marcador cambia de color con una transición y aparece un badge compacto
durante unos segundos. Después queda únicamente la señal cromática persistente.

## Otras direcciones visuales

### Minimal RS

Más agresiva y deportiva:

* negro profundo;
* tipografía más ancha;
* rojo reservado para altas RPM y alertas;
* menos decoración;
* arcos más gruesos y rápidos.

Es impactante, pero puede resultar más cansada y menos coherente con el mapa
claro.

### Digital Touring

Más elegante y tecnológica:

* tonos grafito y azul petróleo;
* líneas finas;
* profundidad suave;
* mapa como protagonista;
* animaciones lentas y refinadas.

Es la alternativa más cercana al diseño actual.

### Audi Concept

Inspirada en interfaces de prototipo, sin copiar un modelo concreto:

* geometría angular;
* paneles flotantes;
* cifras muy grandes;
* escalas segmentadas;
* transiciones más teatrales.

Puede causar mayor impacto inicial, pero exige mucho cuidado para no perder
legibilidad.

## Ideas opcionales

* Tema visual seleccionable entre `Precision GT`, `Minimal RS` y
  `Digital Touring`, manteniendo exactamente la misma estructura de datos.
* Brillo del cockpit ajustable desde los ajustes del launcher.
* Intensidad de animaciones: completa, reducida o desactivada.
* Color de acento seleccionable dentro de una paleta controlada.
* Variación cromática automática según la marcha o régimen, sin crear un modo
  nocturno separado.
* Pequeño historial gráfico de consumo medio, sólo si no sustituye ningún dato.
* Indicador visual de aceleración lateral o longitudinal si se obtiene una señal
  fiable del sistema; no debe calcularse ni mostrarse hasta validarla.
* Vista de demostración en ajustes para probar colores y animaciones sin mover el
  vehículo.

## Reglas de movimiento

* 60 fps como objetivo y ausencia de asignaciones grandes por frame.
* Animaciones de estado entre 160 y 300 ms.
* Movimiento continuo sólo para valores que realmente cambian.
* Interpolación angular por el camino más corto.
* Sin rebotes, partículas, destellos largos ni parpadeos decorativos.
* Respetar una futura opción de movimiento reducido.
* El mapa, la telemetría y la interfaz deben continuar siendo independientes.

## Reglas de legibilidad y seguridad

* Velocidad y RPM deben poder leerse de un vistazo.
* Ningún fondo puede reducir el contraste de las cifras.
* El color no puede ser la única señal de una alerta importante.
* Las áreas táctiles superiores no deben reducirse.
* No se muestran paneles emergentes sobre los relojes mientras se conduce.
* Los estados de carga y conectividad no bloquean el dashboard.
* Si falla cualquier efecto visual, debe permanecer la representación estática.

## Propuesta de ejecución

### Fase 1: sistema visual

* Definir paleta, tipografía, grosores, radios y niveles de profundidad.
* Crear una captura estática de `Precision GT`.
* Compararla a tamaño físico y con la densidad real del Navifly.

### Fase 2: instrumentos

* Rediseñar una sola esfera como componente aislado.
* Validar velocidad de renderizado y legibilidad.
* Aplicar el mismo sistema a RPM y marcha.

### Fase 3: mapa y estructura

* Refinar la integración visual sin tocar el motor de tiles.
* Rediseñar cabecera, banda inferior y testigos.
* Mantener las dimensiones y zonas táctiles comprobadas.

### Fase 4: movimiento

* Añadir arranque, cambios de marcha y alertas.
* Probar con el replay de eventos reales.
* Verificar pérdidas de red/GPS y arranque sin cobertura.

### Fase 5: validación en coche

* Captura con vehículo parado.
* Prueba con sol, noche y distintos niveles de brillo.
* Comprobación de lectura rápida en movimiento.
* Revisión de temperatura, memoria, batería y fluidez.

## Criterio de éxito

La nueva versión será mejor si, al verla por primera vez, parece un cuadro de
instrumentos de producción y no una colección de widgets; y si, después del
impacto inicial, toda la información continúa encontrándose exactamente con la
misma facilidad que ahora.
