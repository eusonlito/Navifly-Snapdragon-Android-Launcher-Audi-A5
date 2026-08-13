#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
boot_root=$(realpath -e "$script_dir/..")
test_name="generator-test-$$"
test_dir="$boot_root/$test_name"

cleanup() {
  rm -rf -- "$test_dir"
}
trap cleanup EXIT
mkdir -p "$test_dir"

for command_name in ffmpeg convert identify compare unzip zipinfo sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "Falta la herramienta requerida para el test: $command_name" >&2
    exit 1
  }
done

ffmpeg -v error -f lavfi \
  -i "color=c=0x112233:s=640x480:r=6:d=1,drawbox=x=240:y=140:w=160:h=200:color=white:t=fill" \
  -an -c:v mpeg4 -q:v 2 -y "$test_dir/bootanimation.mp4"

"$script_dir/generate.sh" "$test_name"

for output_name in bootanimation.zip bootlogo.zip bootanimation.gif bootlogo.png; do
  [[ -s "$test_dir/$output_name" ]] || {
    echo "No se generó $output_name" >&2
    exit 1
  }
done

[[ "$(unzip -p "$test_dir/bootanimation.zip" desc.txt)" == $'2400 896 6\np 0 0 part0' ]]
desc_tail=$(unzip -p "$test_dir/bootanimation.zip" desc.txt | tail -c 2 | od -An -tx1 | tr -d ' \n')
[[ "$desc_tail" == "0a0a" ]]
[[ "$(zipinfo -1 "$test_dir/bootlogo.zip")" == "2400_900/logo_customer1.bmp" ]]
[[ "$(identify -format '%wx%h' "$test_dir/bootlogo.png")" == "2400x896" ]]
[[ "$(identify -format '%wx%h' "$test_dir/bootanimation.gif[0]")" == "1200x448" ]]

unzip -p "$test_dir/bootlogo.zip" 2400_900/logo_customer1.bmp > "$test_dir/logo-check.bmp"
[[ "$(identify -format '%m %wx%h %[depth]' "$test_dir/logo-check.bmp")" == \
  "BMP3 2400x896 8" ]]

unzip -p "$test_dir/bootanimation.zip" part0/frame-00000.png > "$test_dir/frame-check.png"
frame_properties=$(identify -format '%wx%h %[opaque]' "$test_dir/frame-check.png" | \
  tr '[:upper:]' '[:lower:]')
[[ "$frame_properties" == "2400x896 true" ]]
pixel_difference=$(compare -metric AE "$test_dir/frame-check.png" \
  "$test_dir/logo-check.bmp" null: 2>&1 || true)
[[ "$pixel_difference" == "0" ]]

# A 4:3 source must have equal-coloured side padding rather than being cropped.
left_pixel=$(convert "$test_dir/frame-check.png" -format '%[hex:p{0,448}]' info:)
right_pixel=$(convert "$test_dir/frame-check.png" -format '%[hex:p{2399,448}]' info:)
center_pixel=$(convert "$test_dir/frame-check.png" -format '%[hex:p{1200,448}]' info:)
[[ "$left_pixel" == "$right_pixel" && "$left_pixel" != "$center_pixel" ]]

zipinfo -l "$test_dir/bootanimation.zip" | awk '
  /^-/ && $7 != "stor" { exit 1 }
'
zipinfo -l "$test_dir/bootlogo.zip" | awk '
  /^-/ && $7 != "stor" { exit 1 }
'

before_hashes=$(sha256sum "$test_dir"/bootanimation.zip "$test_dir"/bootlogo.zip \
  "$test_dir"/bootanimation.gif "$test_dir"/bootlogo.png)
mv -- "$test_dir/bootanimation.mp4" "$test_dir/bootanimation-valid.mp4"
printf 'not a video\n' > "$test_dir/bootanimation.mp4"
if "$script_dir/generate.sh" "$test_name" >/dev/null 2>&1; then
  echo "El generador aceptó un vídeo inválido" >&2
  exit 1
fi
after_hashes=$(sha256sum "$test_dir"/bootanimation.zip "$test_dir"/bootlogo.zip \
  "$test_dir"/bootanimation.gif "$test_dir"/bootlogo.png)
[[ "$before_hashes" == "$after_hashes" ]]

if "$script_dir/generate.sh" ../default >/dev/null 2>&1; then
  echo "El generador aceptó una ruta insegura" >&2
  exit 1
fi

echo "Test del generador boot: OK"
