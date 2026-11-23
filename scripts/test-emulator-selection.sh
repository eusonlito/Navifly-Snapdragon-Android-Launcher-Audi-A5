#!/usr/bin/env bash
# Exercise the launcher up to deployment with a fake SDK and no real devices.
set -euo pipefail
scripts=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
export ANDROID_HOME="$work/sdk" EMULATOR_TEST="$work"
mkdir -p "$ANDROID_HOME"/{emulator,platform-tools} "$work/project/scripts"
cp "$scripts/emulator.sh" "$work/project/scripts/"
touch "$work/replay.jsonl"
printf '#!/bin/sh\ntouch "$EMULATOR_TEST/build-started"\nexit 1\n' > "$work/project/gradlew"

cat > "$ANDROID_HOME/platform-tools/adb" <<'ADB'
#!/usr/bin/env bash
echo "$*" >> "$EMULATOR_TEST/adb.log"
case "$*" in
    'devices -l')
        echo 'emulator-5554 device transport_id:1'
        echo 'physical-device device transport_id:3'
        if [[ -f $EMULATOR_TEST/online ]]; then echo 'emulator-5562 device transport_id:7'; fi ;;
    '-t 1 shell getprop ro.boot.qemu.avd_name') echo A5_Local_AI_Probe_Other ;;
    '-t 7 shell getprop ro.boot.qemu.avd_name') printf 'A5_Local_AI_Probe\r\n' ;;
    '-t 7 shell getprop sys.boot_completed') echo 1 ;;
    '-t 7 shell wm '*) ;;
    *) exit 1 ;;
esac
ADB
cat > "$ANDROID_HOME/emulator/emulator" <<'EMU'
#!/usr/bin/env bash
echo "$*" >> "$EMULATOR_TEST/launch.log"
[[ $1 == -avd && $2 == A5_Local_AI_Probe && ! -f $EMULATOR_TEST/missing ]] || exit 1
touch "$EMULATOR_TEST/online"
EMU
chmod +x "$ANDROID_HOME/platform-tools/adb" "$ANDROID_HOME/emulator/emulator"

for scenario in already-running start missing; do
    rm -f "$work"/{online,missing,build-started,launch.log,adb.log}
    if [[ $scenario == already-running ]]; then touch "$work/online"; fi
    if [[ $scenario == missing ]]; then touch "$work/missing"; fi
    # The stub Gradle stops successful selections just before installation.
    if bash "$work/project/scripts/emulator.sh" --replay "$work/replay.jsonl" >"$work/log" 2>&1; then exit 1; fi
    if [[ $scenario == already-running ]]; then
        [[ ! -e $work/launch.log ]]
    else
        [[ $(wc -l < "$work/launch.log") == 1 ]]
        grep -q '^-avd A5_Local_AI_Probe ' "$work/launch.log"
    fi
    if [[ $scenario == missing ]]; then
        [[ ! -e $work/build-started ]]
        if grep -q 'shell wm' "$work/adb.log"; then exit 1; fi
    else
        [[ -f $work/build-started ]]
        grep -qx -- '-t 7 shell wm size 2400x896' "$work/adb.log"
    fi
    if grep -vEx -- 'devices -l|-t 1 shell getprop ro.boot.qemu.avd_name|-t 7 .*' "$work/adb.log"; then exit 1; fi
done
echo 'Emulator selection: OK'
