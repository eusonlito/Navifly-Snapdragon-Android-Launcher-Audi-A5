# A5 Inspector

`a5-inspector` es una herramienta independiente y de uso puntual para recopilar
material necesario durante la investigación de la comunicación nativa entre
Android, EventCenter y la MCU del dispositivo Navifly/Mekede.

## Objetivo

La aplicación permite obtener un paquete técnico completo sin ADB. El usuario
pulsa `GENERAR PAQUETE DE ANÁLISIS`, elige el destino mediante el selector de
documentos de Android y espera a que finalice la escritura directa del ZIP.

Está diseñada exclusivamente para Android 14/API 34 y no requiere conexión de
red. No reemplaza EventCenter ni modifica el comportamiento del vehículo.

## Información recopilada

- inventario de todas las aplicaciones instaladas, componentes, permisos, UID,
  rutas y firmas SHA-256;
- APK base y splits de ChoiceWay/SzChoiceWay y de herramientas relacionadas con
  MCU, CAN, Panel y diagnóstico;
- librerías nativas incluidas en esos APK y directorios nativos legibles;
- propiedades `getprop` y datos del dispositivo;
- estado y permisos de `/dev/ttyHS1`, `/dev/ttyS9`, `/dev/vehicle` y particiones
  de configuración conocidas, sin abrir esos dispositivos;
- inventario de framework, librerías y permisos de `system` y `vendor`, copiando
  los archivos relevantes que Android permita leer;
- contenido accesible de SysVar Storage, con credenciales y secretos redactados;
- `manifest.json` con el contenido obtenido y cada acceso rechazado.

## Límites de seguridad

El inspector no detiene EventCenter, no abre el puerto serie de la MCU, no envía
comandos y no intenta leer los directorios privados de otras aplicaciones. Una
APK convencional tampoco puede superar restricciones de UID o SELinux: esos
casos aparecen en el informe y sirven para determinar si una fase posterior
necesitaría privilegios de sistema.

## Artefactos

El proyecto está en `a5-inspector/`. Su `compile.sh` genera
`a5-inspector/out/A5Inspector.apk` y copia el instalable en
`a5-launcher/../a5-inspector/out/A5Inspector.apk`.

La primera compilación crea una firma privada en
`a5-inspector/release.keystore`. No se versiona, pero debe guardarse para que
Android acepte futuras versiones como actualizaciones de la aplicación.
