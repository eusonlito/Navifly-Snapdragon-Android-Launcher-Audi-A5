#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
ROOT_DIR=$(realpath -e "$SCRIPT_DIR/..")

read_local_sdk_dir() {
    local project_root=$1
    local key value
    local properties="$project_root/local.properties"

    [ -f "$properties" ] || return 0
    while IFS='=' read -r key value; do
        if [ "$key" = "sdk.dir" ]; then
            printf '%s\n' "$value"
            return
        fi
    done < "$properties"
}

list_build_tool_versions() {
    local build_tools_dir=$1
    local version_dir version_name

    for version_dir in "$build_tools_dir"/*; do
        [ -d "$version_dir" ] || continue
        version_name=${version_dir##*/}
        [[ $version_name == [0-9]* ]] && printf '%s\n' "$version_dir"
    done | sort -Vr
    for version_dir in "$build_tools_dir"/*; do
        [ -d "$version_dir" ] || continue
        version_name=${version_dir##*/}
        [[ $version_name == [0-9]* ]] || printf '%s\n' "$version_dir"
    done | sort -Vr
}

resolve_build_tools_from_candidates() {
    local path_value=$1
    shift
    local path_dir sdk_dir version_dir
    local -a path_dirs

    IFS=':' read -r -a path_dirs <<< "$path_value"
    for path_dir in "${path_dirs[@]}"; do
        [ -n "$path_dir" ] || path_dir=.
        if [ -x "$path_dir/zipalign" ] && [ -x "$path_dir/apksigner" ]; then
            printf '%s\n%s\n' "$path_dir/zipalign" "$path_dir/apksigner"
            return
        fi
    done

    for sdk_dir in "$@"; do
        [ -d "$sdk_dir/build-tools" ] || continue
        while IFS= read -r version_dir; do
            if [ -x "$version_dir/zipalign" ] && [ -x "$version_dir/apksigner" ]; then
                printf '%s\n%s\n' "$version_dir/zipalign" "$version_dir/apksigner"
                return
            fi
        done < <(list_build_tool_versions "$sdk_dir/build-tools")
    done

    echo "No se encuentra una versión completa de Android SDK Build Tools." >&2
    echo "Instálala, añádela al PATH o configura ANDROID_HOME, ANDROID_SDK_ROOT o sdk.dir." >&2
    return 1
}

resolve_build_tools() {
    local project_root=${1:-$ROOT_DIR}
    local local_sdk=""
    local -a sdk_paths

    local_sdk=$(read_local_sdk_dir "$project_root")
    sdk_paths=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "$local_sdk"
        "$HOME/Android/Sdk"
        "$HOME/Library/Android/sdk"
        "/usr/lib/android-sdk"
        "/opt/android-sdk"
    )
    resolve_build_tools_from_candidates "${PATH:-}" "${sdk_paths[@]}"
}

WORK_DIR=""

cleanup_work_dir() {
    if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}

main() {
    if [ "$#" -ne 3 ]; then
        echo "Uso: prepare-replay-apk.sh APK_BASE LOG_JSONL APK_SALIDA" >&2
        return 2
    fi

    local base_apk=$1
    local replay_log=$2
    local output_apk=$3
    local replay_asset="telemetry-replay.jsonl"
    local resolved_tools zipalign apksigner
    local file
    local -a build_tools

    resolved_tools=$(resolve_build_tools)
    mapfile -t build_tools <<< "$resolved_tools"
    zipalign=${build_tools[0]}
    apksigner=${build_tools[1]}

    for file in "$base_apk" "$replay_log"; do
        [ -f "$file" ] || { echo "No existe: $file" >&2; return 1; }
    done

    WORK_DIR=$(mktemp -d)

    python3 - "$replay_log" <<'PY'
import json
import sys

aidl = 0
gps = 0
with open(sys.argv[1], encoding="utf-8") as source:
    for number, line in enumerate(source, 1):
        try:
            event = json.loads(line)
        except json.JSONDecodeError as error:
            raise SystemExit(f"JSONL no válido en la línea {number}: {error}")
        aidl += event.get("source") == "AIDL_CALLBACK"
        gps += event.get("source") == "GPS_LOCATION"

if aidl == 0:
    raise SystemExit("El log no contiene callbacks AIDL")
print(f"Log validado: {aidl} callbacks AIDL · {gps} posiciones GPS")
PY

    mkdir -p "$WORK_DIR/assets" "$(dirname "$output_apk")"
    cp "$base_apk" "$WORK_DIR/base.apk"
    cp "$replay_log" "$WORK_DIR/assets/$replay_asset"
    (
        cd "$WORK_DIR"
        zip -q -d base.apk "assets/$replay_asset" 'META-INF/*' >/dev/null || true
        zip -9 -q base.apk "assets/$replay_asset"
    )
    "$zipalign" -p -f 4 "$WORK_DIR/base.apk" "$WORK_DIR/aligned.apk"
    "$apksigner" sign \
        --ks "$HOME/.android/debug.keystore" \
        --ks-pass pass:android \
        --key-pass pass:android \
        --out "$output_apk" \
        "$WORK_DIR/aligned.apk"
    "$apksigner" verify "$output_apk"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    trap cleanup_work_dir EXIT INT TERM
    main "$@"
fi
