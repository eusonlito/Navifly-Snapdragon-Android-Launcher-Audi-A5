#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PACKAGE="com.lito.a5launcher"
ACTIVITY="$PACKAGE/.MainActivity"
ASSET_NAME="telemetry-replay.jsonl"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
OUTPUT="$ROOT_DIR/screenshots/a5-capture-$(date +%Y%m%d-%H%M%S)-2400x896.png"
LATITUDE="43.348538"
LONGITUDE="-8.407951"
SPEED=80
RPM=1840
FUEL=56
ODOMETER=220024
OUTSIDE_TENTHS=245
SETTLE_SECONDS=12
KEEP_EMULATOR=false
OFFLINE=false
AVD=""

usage() {
    cat <<'EOF'
Uso: scripts/capture-emulator.sh [opciones]

Genera una captura promocional sin modificar la aplicación ni su replay real.
Requiere un APK debug ya compilado mediante ./gradlew assembleDebug.

Opciones:
  --apk RUTA          APK debug base
  --output RUTA       PNG de salida
  --latitude VALOR    Latitud GPS (43.348538)
  --longitude VALOR   Longitud GPS (-8.407951)
  --speed ENTERO      Velocidad km/h (80)
  --rpm ENTERO        Revoluciones (1840)
  --fuel ENTERO       Combustible en litros (56)
  --odometer ENTERO   Odómetro en km (220024)
  --outside DECIMAS   Temperatura exterior en décimas °C (245 = 24,5 °C)
  --settle SEGUNDOS   Espera antes de capturar (12)
  --avd NOMBRE        AVD concreto; por defecto usa el único disponible
  --offline           Captura explícita sin red para validar caché y avisos
  --keep-emulator     No detener el emulador al terminar
  -h, --help          Mostrar esta ayuda
EOF
}

while (($#)); do
    case "$1" in
        --apk) APK=$2; shift 2 ;;
        --output) OUTPUT=$2; shift 2 ;;
        --latitude) LATITUDE=$2; shift 2 ;;
        --longitude) LONGITUDE=$2; shift 2 ;;
        --speed) SPEED=$2; shift 2 ;;
        --rpm) RPM=$2; shift 2 ;;
        --fuel) FUEL=$2; shift 2 ;;
        --odometer) ODOMETER=$2; shift 2 ;;
        --outside) OUTSIDE_TENTHS=$2; shift 2 ;;
        --settle) SETTLE_SECONDS=$2; shift 2 ;;
        --avd) AVD=$2; shift 2 ;;
        --offline) OFFLINE=true; shift ;;
        --keep-emulator) KEEP_EMULATOR=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Opción desconocida: $1" >&2; usage >&2; exit 2 ;;
    esac
done

for value in "$SPEED" "$RPM" "$FUEL" "$ODOMETER" "$OUTSIDE_TENTHS" "$SETTLE_SECONDS"; do
    [[ $value =~ ^-?[0-9]+$ ]] || { echo "Valor entero no válido: $value" >&2; exit 2; }
done
((SPEED > 0 && SPEED <= 280)) || { echo "La velocidad debe estar entre 1 y 280" >&2; exit 2; }
((RPM >= 900 && RPM <= 6000)) || { echo "Las RPM deben estar entre 900 y 6000" >&2; exit 2; }
((FUEL >= 0 && FUEL <= 63)) || { echo "El combustible debe estar entre 0 y 63" >&2; exit 2; }
((ODOMETER > 0 && ODOMETER <= 16777215)) || { echo "Odómetro fuera del rango de 24 bits" >&2; exit 2; }
((SETTLE_SECONDS >= 3)) || { echo "La espera debe ser de al menos 3 segundos" >&2; exit 2; }
[[ -f $APK ]] || {
    echo "No existe el APK debug: $APK" >&2
    echo "Compílalo primero con: ./gradlew assembleDebug" >&2
    exit 1
}

ADB=$(command -v adb || true)
EMULATOR=$(command -v emulator || true)
if [[ -z $ADB ]]; then ADB="$HOME/Android/Sdk/platform-tools/adb"; fi
if [[ -z $EMULATOR ]]; then EMULATOR="$HOME/Android/Sdk/emulator/emulator"; fi
for tool in "$ADB" "$EMULATOR" python3; do
    if [[ $tool == */* ]]; then
        [[ -x $tool ]] || { echo "Herramienta no disponible: $tool" >&2; exit 1; }
    else
        command -v "$tool" >/dev/null || { echo "Herramienta no disponible: $tool" >&2; exit 1; }
    fi
done

TEMP_DIR=$(mktemp -d)
STARTED_EMULATOR=false
SERIAL=""
cleanup() {
    local status=$?
    rm -rf "$TEMP_DIR"
    if [[ $STARTED_EMULATOR == true && $KEEP_EMULATOR == false && -n $SERIAL ]]; then
        "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    fi
    exit "$status"
}
trap cleanup EXIT INT TERM

FIXTURE="$TEMP_DIR/$ASSET_NAME"
python3 - "$FIXTURE" "$SPEED" "$RPM" "$FUEL" "$ODOMETER" "$OUTSIDE_TENTHS" <<'PY'
import json
import sys

path, speed, rpm, fuel, odometer, outside = sys.argv[1:]
speed, rpm, fuel, odometer, outside = map(int, (speed, rpm, fuel, odometer, outside))

frame = bytearray(24)
frame[0] = 0xF2
frame[1] = 0x00
frame[2] = 0xA1
frame[3] = 0x13
frame[4] = 0x19
frame[11:13] = speed.to_bytes(2, "big")
frame[13:15] = rpm.to_bytes(2, "big")
frame[15:17] = fuel.to_bytes(2, "big")
frame[17:19] = outside.to_bytes(2, "big", signed=True)
frame[20:23] = odometer.to_bytes(3, "big")

with open(path, "w", encoding="utf-8", newline="\n") as target:
    provider = {
        "timestamp": 0,
        "source": "SYSVAR_INITIAL",
        "key": "KSW_DATA_SMALL_LIGHT_ON",
        "value": "0",
    }
    target.write(json.dumps(provider, separators=(",", ":")) + "\n")
    for index in range(100):
        event = {
            "timestamp": 100 + index * 100,
            "source": "AIDL_CALLBACK",
            "msg_what": 90,
            "arg1": 0,
            "arg2": 0,
            "bytes_hex": frame.hex().upper(),
            "str": "",
        }
        target.write(json.dumps(event, separators=(",", ":")) + "\n")
PY

"$ROOT_DIR/scripts/prepare-replay-apk.sh" \
    "$APK" \
    "$FIXTURE" \
    "$TEMP_DIR/capture.apk"

SERIAL=$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')
if [[ -z $SERIAL ]]; then
    if [[ -z $AVD ]]; then
        mapfile -t avds < <("$EMULATOR" -list-avds)
        ((${#avds[@]} == 1)) || {
            echo "Indica --avd; se encontraron ${#avds[@]} dispositivos virtuales" >&2
            exit 1
        }
        AVD=${avds[0]}
    fi
    "$EMULATOR" -avd "$AVD" -skin 2400x896 -no-snapshot -no-window \
        -gpu swiftshader_indirect -no-audio >"$TEMP_DIR/emulator.log" 2>&1 &
    STARTED_EMULATOR=true
    "$ADB" wait-for-device
    while [[ $("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') != 1 ]]; do
        sleep 2
    done
    SERIAL=$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')
fi

"$ADB" -s "$SERIAL" shell wm size 2400x896 >/dev/null
"$ADB" -s "$SERIAL" shell wm density 320 >/dev/null

if [[ $OFFLINE == true ]]; then
    "$ADB" -s "$SERIAL" shell settings put global airplane_mode_on 1
    "$ADB" -s "$SERIAL" shell svc wifi disable
else
    "$ADB" -s "$SERIAL" shell settings put global airplane_mode_on 0
    "$ADB" -s "$SERIAL" shell svc wifi enable
    network_ready=false
    for _ in {1..30}; do
        if "$ADB" -s "$SERIAL" shell dumpsys connectivity |
            grep -q 'Transports: WIFI Capabilities:.*VALIDATED'; then
            network_ready=true
            break
        fi
        sleep 1
    done
    [[ $network_ready == true ]] || {
        echo "El emulador no consiguió una red Wi-Fi validada; usa --offline sólo si esa es la prueba deseada" >&2
        exit 1
    }
fi

"$ADB" -s "$SERIAL" install -r "$TEMP_DIR/capture.apk" >/dev/null
"$ADB" -s "$SERIAL" shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION
"$ADB" -s "$SERIAL" shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION
"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"

PREFERENCES='<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="map_color_mode">LIGHT</string><string name="map_tile_style">POSITRON</string><boolean name="map_debug" value="false" /></map>'
printf '%s' "$PREFERENCES" | "$ADB" -s "$SERIAL" shell \
    "run-as $PACKAGE sh -c 'mkdir -p shared_prefs; cat > shared_prefs/launcher_settings.xml'"
"$ADB" -s "$SERIAL" shell \
    "run-as $PACKAGE rm -f shared_prefs/current_boot_trip.xml"

"$ADB" -s "$SERIAL" emu geo fix "$LONGITUDE" "$LATITUDE" >/dev/null
"$ADB" -s "$SERIAL" shell am start -n "$ACTIVITY" >/dev/null
sleep 1
"$ADB" -s "$SERIAL" emu geo fix "$LONGITUDE" "$LATITUDE" >/dev/null
sleep "$SETTLE_SECONDS"

mkdir -p "$(dirname "$OUTPUT")"
"$ADB" -s "$SERIAL" exec-out screencap -p > "$OUTPUT"
dimensions=$(file "$OUTPUT")
[[ $dimensions == *"2400 x 896"* ]] || { echo "Captura con tamaño inesperado: $dimensions" >&2; exit 1; }

expected_consumption=$(python3 -c "print(f'{4.8 + ($RPM / 1800.0) * 1.5:.1f}')")
echo "Captura creada: $OUTPUT"
echo "Escena: ${SPEED} km/h · ${RPM} rpm · consumo aproximado ${expected_consumption} · odómetro ${ODOMETER}"
echo "GPS: ${LATITUDE}, ${LONGITUDE} · mapa LIGHT/POSITRON · red $([[ $OFFLINE == true ]] && echo OFFLINE || echo CONECTADA)"
