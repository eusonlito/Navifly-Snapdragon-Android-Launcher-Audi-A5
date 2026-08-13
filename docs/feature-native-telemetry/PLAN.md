# Plan de investigación e implementación

## Fase 0 — inventario reproducible (completada)

1. Verificar el ZIP completo y calcular su SHA-256.
2. Ingerir cada entrada una vez en una caché direccionada por contenido.
3. Indexar las 28 aplicaciones y crear una ficha persistente por paquete.
4. Decompilar sólo núcleo y consumidores prioritarios; conservar el resultado
   por hash para no repetir JADX.

## Fase 1 — contrato compatible (completada para los mensajes conocidos)

1. Documentar Binder, broadcasts y `SysVarProvider` de EventCenter.
2. Confirmar la estructura de las tramas recibidas del MCU con logs reales.
3. Crear pruebas de decodificación sobre capturas, sin acceso al puerto serie.
4. Comprobar si pueden coexistir varios callbacks; el dashboard conserva una
   única referencia. El logger identifica explícitamente este canal como
   exclusivo; la API no permite recuperar ni encadenar el callback anterior.

## Fase 2 — observador compatible (en curso)

1. Añadir al logger un receptor de los contratos públicos confirmados. Hecho
   para broadcasts, providers, estado Binder de sólo lectura y el callback
   diagnóstico 90–96; este último no es pasivo porque el contrato es exclusivo.
2. Guardar bytes crudos, secuencia, timestamp monotónico y estado conocido.
   Hecho en el formato JSONL.
3. Ejecutar maniobras controladas y completar sólo campos reproducibles.
4. No escribir en `/dev/ttyHS1` ni enviar comandos MCU.

Los decodificadores de 90, 91, 93, 95 y 96 están aislados y cubiertos con
pruebas. El mensaje 90 conserva los bytes 5–6 como `short_distance_raw`, sin
afirmar que sean autonomía, y el 95 como códigos del climatizador, no como
temperatura del refrigerante.

## Fase 3 — prototipo nativo aislado

Requiere acceso de sistema real: firma de plataforma o incorporación controlada
a la imagen, permisos del nodo y política SELinux. El prototipo debe ejecutarse
fuera del launcher, con watchdog, parada segura y EventCenter disponible como
fuente principal/fallback. Se limita a lectura de telemetría de conducción; no
implementará escritura MCU ni intentará controlar overlays.

## Fase 4 — decisión de integración

EventCenter se conserva. Sólo se incorporará al launcher una fuente nueva cuando
aporte un dato de conducción reproducible, tenga tests y no interfiera con el
servicio del fabricante. Los overlays quedan expresamente fuera del producto.
