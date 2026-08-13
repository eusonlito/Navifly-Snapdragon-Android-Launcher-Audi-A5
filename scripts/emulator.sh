#!/usr/bin/env bash
# Ejecuta el launcher con replay CAN/GPS sobre un emulador 2400x896 @ 320 dpi.

# Colores ANSI para terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Asegurar que siempre operamos en la raíz del proyecto.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

DISPLAY_SIZE="2400x896"
DISPLAY_DENSITY="320"
REPLAY_LOG=""
FILES_DIR=""
EMULATOR_FILES_DIR="/sdcard/Download/A5-Cockpit"
REPLAY_WORK_DIR=""
GPS_REPLAY_PID=""
FILES_WATCH_PID=""

usage() {
    cat <<EOF
Uso: ./scripts/emulator.sh --replay RUTA_JSONL [--files DIRECTORIO]

  --replay RUTA_JSONL  Reproduce en bucle el CAN y GPS de ese log.
  --files DIRECTORIO   Copia sus ficheros a Downloads/A5-Cockpit.
  -h, --help           Muestra esta ayuda.

El replay es obligatorio y debe apuntar a un JSONL local no versionado.
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --replay)
            [ "$#" -ge 2 ] || { echo "Falta la ruta para --replay" >&2; exit 2; }
            REPLAY_LOG=$2
            shift 2
            ;;
        --files)
            [ "$#" -ge 2 ] || { echo "Falta el directorio para --files" >&2; exit 2; }
            FILES_DIR=$2
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Opción desconocida: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [ -n "$FILES_DIR" ]; then
    [ -d "$FILES_DIR" ] || {
        echo "No existe el directorio indicado para --files: $FILES_DIR" >&2
        exit 2
    }
    FILES_DIR=$(realpath "$FILES_DIR")
fi

if [ -z "$REPLAY_LOG" ]; then
    echo -e "${YELLOW}${BOLD}[AVISO]${NC} Debes indicar un replay local."
    echo "Uso: ./scripts/emulator.sh --replay /ruta/log.jsonl"
    exit 2
fi

if [ ! -f "$REPLAY_LOG" ]; then
    echo -e "${RED}${BOLD}[ERROR]${NC} No existe el replay: $REPLAY_LOG"
    exit 2
fi
REPLAY_LOG=$(realpath "$REPLAY_LOG")

cleanup() {
    if [ -n "$FILES_WATCH_PID" ]; then
        kill "$FILES_WATCH_PID" >/dev/null 2>&1 || true
    fi
    if [ -n "$GPS_REPLAY_PID" ]; then
        kill "$GPS_REPLAY_PID" >/dev/null 2>&1 || true
    fi
    if [ -n "$REPLAY_WORK_DIR" ] && [ -d "$REPLAY_WORK_DIR" ]; then
        rm -rf "$REPLAY_WORK_DIR"
    fi
}
trap cleanup EXIT INT TERM

echo -e "${RED}${BOLD}"
echo "     _       _  _      _                            _ "
echo "    /_\ _  _(_)(_)    | |   __ _ _  _ _ _  __ |_|___ _ _ "
echo "   / _ \ || | || |    | |__/ _\` | || | ' \/ _/  / -_) '_|"
echo "  /_/ \_\_,_|_||_|    |____\__,_|\_,_|_||_\__/\__\___|_|  "
echo "  ========================================================"
echo -e "      S-LINE EMULATOR PIPELINE (2400x896 @ 320 dpi)       ${NC}"
echo ""

# 1. Intentar localizar el SDK de Android
SDK_PATHS=(
    "${ANDROID_HOME:-}"
    "${ANDROID_SDK_ROOT:-}"
    "$HOME/Android/Sdk"
    "$HOME/Library/Android/sdk"
    "/usr/lib/android-sdk"
    "/opt/android-sdk"
)

ANDROID_SDK=""
for path in "${SDK_PATHS[@]}"; do
    if [ -n "$path" ] && [ -d "$path" ]; then
        ANDROID_SDK="$path"
        break
    fi
done

if [ -z "$ANDROID_SDK" ]; then
    echo -e "${RED}${BOLD}[ERROR]${NC} No se ha encontrado el SDK de Android en las rutas por defecto."
    echo "Por favor, exporta la variable ANDROID_HOME apuntando a tu SDK de Android:"
    echo "  export ANDROID_HOME=/tu/ruta/sdk"
    exit 1
fi

echo -e "${GREEN}[INFO]${NC} SDK de Android localizado en: ${BOLD}$ANDROID_SDK${NC}"

# 2. Localizar el ejecutable del emulador y ADB
EMULATOR=""
EMULATOR_CANDIDATES=(
    "$ANDROID_SDK/emulator/emulator"
    "$ANDROID_SDK/tools/emulator"
)

for cand in "${EMULATOR_CANDIDATES[@]}"; do
    if [ -x "$cand" ]; then
        EMULATOR="$cand"
        break
    fi
done

if [ -z "$EMULATOR" ]; then
    if command -v emulator >/dev/null 2>&1; then
        EMULATOR=$(command -v emulator)
    else
        echo -e "${RED}${BOLD}[ERROR]${NC} No se encontró el binario 'emulator'."
        echo "Asegúrate de que el componente 'Emulator' esté instalado en Android Studio SDK Manager."
        exit 1
    fi
fi

ADB_PATH="$ANDROID_SDK/platform-tools/adb"
if [ ! -f "$ADB_PATH" ]; then
    if command -v adb >/dev/null 2>&1; then
        ADB_PATH=$(command -v adb)
    else
        echo -e "${RED}${BOLD}[ERROR]${NC} No se encontró el binario 'adb' en platform-tools ni en el PATH del sistema."
        exit 1
    fi
fi

echo -e "${GREEN}[INFO]${NC} Ejecutable del emulador: ${BOLD}$EMULATOR${NC}"
echo -e "${GREEN}[INFO]${NC} Ejecutable de ADB: ${BOLD}$ADB_PATH${NC}"

# 3. Reutilizar únicamente un emulador, nunca un dispositivo físico.
TARGET_SERIAL=$("$ADB_PATH" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')

if [ -n "$TARGET_SERIAL" ]; then
    echo -e "${YELLOW}${BOLD}[DETECTADO]${NC} Emulador activo: ${BOLD}$TARGET_SERIAL${NC}"
    echo -e "Se reutilizará y se aplicará de nuevo el perfil de pantalla Navifly."
else
    # 4. Listar AVDs disponibles si no hay ninguno encendido
    echo -e "${CYAN}[SEARCH]${NC} Buscando dispositivos virtuales (AVDs)..."
    AVD_LIST=()
    while IFS= read -r AVD; do
        if [ -n "$AVD" ]; then
            AVD_LIST+=("$AVD")
        fi
    done < <("$EMULATOR" -list-avds 2>/dev/null)

    if [ ${#AVD_LIST[@]} -eq 0 ]; then
        echo -e "${YELLOW}${BOLD}[ADVERTENCIA]${NC} No se han encontrado dispositivos virtuales (AVD) creados."
        echo "Crea un AVD en Android Studio -> Device Manager antes de volver a ejecutar."
        exit 1
    fi

    SELECTED_AVD=""
    if [ ${#AVD_LIST[@]} -eq 1 ]; then
        SELECTED_AVD="${AVD_LIST[0]}"
        echo -e "${GREEN}[INFO]${NC} Se ha detectado un único dispositivo: ${BOLD}$SELECTED_AVD${NC}"
    else
        echo -e "${CYAN}[SELECT]${NC} Selecciona el dispositivo virtual que deseas arrancar:"
        for i in "${!AVD_LIST[@]}"; do
            echo -e "  [$i] ${BOLD}${AVD_LIST[$i]}${NC}"
        done

        echo -n "Introduce el número [0-$((${#AVD_LIST[@]} - 1))]: "
        read -r INDEX

        if [[ "$INDEX" =~ ^[0-9]+$ ]] && [ "$INDEX" -ge 0 ] && [ "$INDEX" -lt ${#AVD_LIST[@]} ]; then
            SELECTED_AVD="${AVD_LIST[$INDEX]}"
        else
            echo -e "${RED}${BOLD}[ERROR]${NC} Selección no válida. Saliendo..."
            exit 1
        fi
    fi

    # 5. Lanzar el emulador con la resolución y densidad reales del Navifly.
    echo ""
    echo -e "${GREEN}${BOLD}[BOOT]${NC} Iniciando ${BOLD}$SELECTED_AVD${NC} con perfil Navifly..."
    echo "  - Superficie útil: $DISPLAY_SIZE (Landscape)"
    echo "  - Densidad lógica: $DISPLAY_DENSITY dpi"
    echo ""

    # Arrancar proceso en background
    "$EMULATOR" \
        -avd "$SELECTED_AVD" \
        -skin "$DISPLAY_SIZE" \
        -dpi-device "$DISPLAY_DENSITY" \
        -allow-host-audio \
        -no-snapstorage > /dev/null 2>&1 &

    # 6. Esperar a que el emulador esté completamente online y listo
    echo -e "${CYAN}[WAIT]${NC} Esperando a que el emulador responda a ADB..."
    while [ -z "$TARGET_SERIAL" ]; do
        sleep 1
        TARGET_SERIAL=$("$ADB_PATH" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')
    done

    echo -e "${CYAN}[WAIT]${NC} Esperando a que Android finalice el arranque de la interfaz (boot_completed)..."
    BOOT_COMPLETED=""
    while [ "$BOOT_COMPLETED" != "1" ]; do
        sleep 2
        BOOT_COMPLETED=$("$ADB_PATH" -s "$TARGET_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo "")
    done
    echo -e "${GREEN}${BOLD}[LISTO]${NC} ¡El emulador ha arrancado correctamente!"
fi

# El skin configura el framebuffer. Estos overrides garantizan también que Android
# use exactamente la superficie y densidad lógica del dispositivo físico.
echo -e "${BLUE}[DISPLAY]${NC} Aplicando ${BOLD}$DISPLAY_SIZE @ $DISPLAY_DENSITY dpi${NC}..."
"$ADB_PATH" -s "$TARGET_SERIAL" shell wm size "$DISPLAY_SIZE"
"$ADB_PATH" -s "$TARGET_SERIAL" shell wm density "$DISPLAY_DENSITY"
echo -e "  $("$ADB_PATH" -s "$TARGET_SERIAL" shell wm size | tr -d '\r')"
echo -e "  $("$ADB_PATH" -s "$TARGET_SERIAL" shell wm density | tr -d '\r')"

# Los selectores SAF de Android no pueden acceder al sistema de archivos del
# host. Para las pruebas, publicamos explícitamente el directorio solicitado en
# Downloads, que sí es visible desde el selector nativo del emulador.
if [ -n "$FILES_DIR" ]; then
    echo -e "${BLUE}[FILES]${NC} Sincronizando ${BOLD}$FILES_DIR${NC}..."
    "$ADB_PATH" -s "$TARGET_SERIAL" shell mkdir -p "$EMULATOR_FILES_DIR"
    if "$ADB_PATH" -s "$TARGET_SERIAL" push "$FILES_DIR/." "$EMULATOR_FILES_DIR/"; then
        echo -e "${GREEN}[FILES]${NC} Disponibles en ${BOLD}Downloads/A5-Cockpit${NC}."
    else
        echo -e "${RED}${BOLD}[ERROR]${NC} No se pudieron copiar los ficheros al emulador."
        exit 1
    fi

    python3 -u "$SCRIPT_DIR/watch-emulator-files.py" \
        --adb "$ADB_PATH" \
        --serial "$TARGET_SERIAL" \
        --source "$FILES_DIR" \
        --destination "$EMULATOR_FILES_DIR" &
    FILES_WATCH_PID=$!
    sleep 1
    if ! kill -0 "$FILES_WATCH_PID" >/dev/null 2>&1; then
        echo -e "${RED}${BOLD}[ERROR]${NC} No se pudo iniciar la vigilancia de ficheros."
        exit 1
    fi
    echo -e "${GREEN}[FILES]${NC} Vigilando cambios locales durante esta sesión."
fi

# 7. Compilar e instalar el Launcher en el emulador
echo ""
echo -e "${BLUE}[BUILD]${NC} Compilando el Launcher S-Line (${BOLD}assembleDebug${NC})..."
chmod +x gradlew
if ! ./gradlew assembleDebug -PtargetAbi=x86_64; then
    echo -e "${RED}${BOLD}[ERROR]${NC} Error durante la compilación del Launcher."
    exit 1
fi
REPLAY_WORK_DIR=$(mktemp -d)
INSTALL_APK="$REPLAY_WORK_DIR/app-debug-replay.apk"
"$SCRIPT_DIR/prepare-replay-apk.sh" \
    "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" \
    "$REPLAY_LOG" \
    "$INSTALL_APK"
echo -e "${GREEN}[REPLAY]${NC} CAN preparado desde ${BOLD}$REPLAY_LOG${NC}."
if "$ADB_PATH" -s "$TARGET_SERIAL" install -r "$INSTALL_APK"; then
    echo -e "${GREEN}${BOLD}[OK]${NC} Instalación completada con éxito."
else
    echo -e "${RED}${BOLD}[ERROR]${NC} Error durante la compilación o instalación del Launcher."
    exit 1
fi

# 8. Arrancar la actividad principal automáticamente en el emulador
echo ""
echo -e "${GREEN}${BOLD}[START]${NC} Lanzando la interfaz de S-Line en el emulador..."
"$ADB_PATH" -s "$TARGET_SERIAL" shell am start -n "com.lito.a5launcher/com.lito.a5launcher.MainActivity"

# El servicio reproduce el CAN empaquetado. LocationManager recibe en paralelo
# el GPS del mismo fichero, con la misma cronología y pausa de bucle.
python3 "$SCRIPT_DIR/replay-emulator-gps.py" \
    "$REPLAY_LOG" \
    --check \
    --serial "$TARGET_SERIAL"
GPS_REPLAY_LOG="$REPLAY_WORK_DIR/gps-replay.log"
python3 "$SCRIPT_DIR/replay-emulator-gps.py" \
    "$REPLAY_LOG" \
    --serial "$TARGET_SERIAL" \
    --loop >"$GPS_REPLAY_LOG" 2>&1 &
GPS_REPLAY_PID=$!
sleep 1
if ! kill -0 "$GPS_REPLAY_PID" >/dev/null 2>&1; then
    echo -e "${RED}${BOLD}[ERROR]${NC} No se pudo iniciar el replay GPS:"
    cat "$GPS_REPLAY_LOG" >&2
    exit 1
fi
echo -e "${GREEN}[GPS]${NC} Reproduciendo en bucle las coordenadas de ${BOLD}$(basename "$REPLAY_LOG")${NC}."

echo ""
echo -e "${GREEN}${BOLD}[ÉXITO]${NC} ¡Despliegue finalizado de forma completamente automatizada!"
echo -e "Puedes presionar el botón Home en el emulador y seleccionar ${BOLD}com.lito.a5launcher${NC} como predeterminado."
echo ""

# 9. Mostrar logs en tiempo real para telemetría
echo "--------------------------------------------------------"
echo -e "📜 ${BOLD}MONITORIZANDO LOGS DE TELEMETRÍA EN TIEMPO REAL${NC}"
echo -e "Filtro activo: ${CYAN}TelemetryService${NC}, ${CYAN}LauncherViewModel${NC}, y ${CYAN}MainActivity${NC}."
echo -e "Presiona ${RED}Ctrl+C${NC} para detener la visualización de logs."
echo "--------------------------------------------------------"

"$ADB_PATH" -s "$TARGET_SERIAL" logcat -c
"$ADB_PATH" -s "$TARGET_SERIAL" logcat TelemetryService:D LauncherViewModel:D MainActivity:D *:S
