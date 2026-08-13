#!/usr/bin/env bash
set -euo pipefail

readonly CANVAS_WIDTH=2400
readonly CANVAS_HEIGHT=896
readonly GIF_WIDTH=1200
readonly GIF_HEIGHT=448
readonly MAX_FPS=30
readonly MAX_GIF_FPS=12
readonly MAX_ANIMATION_BYTES=$((100 * 1024 * 1024))

fail() {
  echo "Error: $*" >&2
  exit 1
}

if (( $# != 1 )); then
  echo "Uso: $0 NOMBRE_CARPETA" >&2
  echo "El vídeo debe estar en boot/NOMBRE_CARPETA/bootanimation.mp4." >&2
  exit 2
fi

package_name=$1
[[ "$package_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || \
  fail "nombre de carpeta no válido: $package_name"
[[ "$package_name" != "." && "$package_name" != ".." ]] || \
  fail "la carpeta debe estar dentro de boot/"

for command_name in ffmpeg ffprobe convert identify zip unzip zipinfo realpath awk sed grep stat; do
  command -v "$command_name" >/dev/null 2>&1 || \
    fail "falta la herramienta requerida: $command_name"
done

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
boot_root=$(realpath -e "$script_dir/..")
package_dir="$boot_root/$package_name"

[[ -d "$package_dir" ]] || fail "no existe el directorio: $package_dir"
[[ ! -L "$package_dir" ]] || fail "no se admiten directorios enlazados: $package_dir"
package_real=$(realpath -e "$package_dir")
[[ "$package_real" == "$package_dir" && "$package_real" == "$boot_root/"* ]] || \
  fail "el directorio sale de boot/: $package_dir"

video_path="$package_real/bootanimation.mp4"
[[ -f "$video_path" ]] || fail "no existe el vídeo: $video_path"
[[ ! -L "$video_path" ]] || fail "no se admiten vídeos enlazados: $video_path"
[[ "$(realpath -e "$video_path")" == "$video_path" ]] || \
  fail "el vídeo sale de su directorio"

duration=$(ffprobe -v error -select_streams v:0 \
  -show_entries format=duration -of default=nw=1:nk=1 "$video_path")
frame_rate=$(ffprobe -v error -select_streams v:0 \
  -show_entries stream=avg_frame_rate -of default=nw=1:nk=1 "$video_path")

[[ "$duration" =~ ^[0-9]+([.][0-9]+)?$ ]] || fail "no se pudo determinar la duración"
[[ "$frame_rate" =~ ^[0-9]+/[1-9][0-9]*$ ]] || fail "no se pudo determinar la cadencia"

fps=$(awk -v rate="$frame_rate" -v maximum="$MAX_FPS" 'BEGIN {
  split(rate, parts, "/"); value = parts[1] / parts[2];
  rounded = int(value + 0.5); if (rounded < 1) rounded = 1;
  if (rounded > maximum) rounded = maximum; print rounded;
}')
gif_fps=$fps
(( gif_fps > MAX_GIF_FPS )) && gif_fps=$MAX_GIF_FPS

build_dir=$(mktemp -d "$package_real/.boot-build.XXXXXX")
output_suffix=".new.$$"
cleanup() {
  rm -rf -- "$build_dir"
  for output_name in bootanimation.zip bootlogo.zip bootanimation.gif bootlogo.png; do
    temporary_output="$package_real/.${output_name}${output_suffix}"
    [[ ! -e "$temporary_output" ]] || rm -f -- "$temporary_output"
  done
}
trap cleanup EXIT
mkdir -p "$build_dir/samples" "$build_dir/edges" \
  "$build_dir/animation/part0" "$build_dir/logo/2400_900"

# A single background colour is derived from the perimeter of five frames.
# Keeping it fixed for the whole sequence prevents brightness flicker.
for sample_index in 0 1 2 3 4; do
  timestamp=$(awk -v duration="$duration" -v sample="$sample_index" 'BEGIN {
    t = duration * sample / 5;
    if (t >= duration && duration > 0.02) t = duration - 0.02;
    if (t < 0) t = 0;
    printf "%.6f", t;
  }')
  sample_path="$build_dir/samples/sample-$sample_index.png"
  ffmpeg -v error -ss "$timestamp" -i "$video_path" -map 0:v:0 \
    -frames:v 1 -an -y "$sample_path"

  read -r sample_width sample_height < <(
    identify -format '%w %h\n' "$sample_path"
  )
  edge_width=$((sample_width / 32))
  edge_height=$((sample_height / 32))
  (( edge_width < 1 )) && edge_width=1
  (( edge_height < 1 )) && edge_height=1

  convert "$sample_path" -crop "${sample_width}x${edge_height}+0+0" +repage \
    -resize 1x1\! "$build_dir/edges/$sample_index-top.png"
  convert "$sample_path" -crop "${sample_width}x${edge_height}+0+$((sample_height - edge_height))" +repage \
    -resize 1x1\! "$build_dir/edges/$sample_index-bottom.png"
  convert "$sample_path" -crop "${edge_width}x${sample_height}+0+0" +repage \
    -resize 1x1\! "$build_dir/edges/$sample_index-left.png"
  convert "$sample_path" -crop "${edge_width}x${sample_height}+$((sample_width - edge_width))+0" +repage \
    -resize 1x1\! "$build_dir/edges/$sample_index-right.png"
done

convert "$build_dir"/edges/*.png +append -resize 1x1\! \
  "$build_dir/background.png"
background_hex=$(convert "$build_dir/background.png" -format '%[hex:p{0,0}]' info:)
background_hex=${background_hex:0:6}
[[ "$background_hex" =~ ^[0-9A-Fa-f]{6}$ ]] || fail "no se pudo calcular el fondo"
background_color="0x${background_hex}"

normalize_filter="fps=$fps,scale=${CANVAS_WIDTH}:${CANVAS_HEIGHT}:force_original_aspect_ratio=decrease,pad=${CANVAS_WIDTH}:${CANVAS_HEIGHT}:(ow-iw)/2:(oh-ih)/2:${background_color},format=rgb24"
ffmpeg -v error -i "$video_path" -map 0:v:0 -an -vf "$normalize_filter" \
  -start_number 0 -compression_level 9 -pred mixed -y \
  "$build_dir/animation/part0/frame-%05d.png"

frame_count=$(find "$build_dir/animation/part0" -type f -name 'frame-*.png' | wc -l)
(( frame_count > 0 )) || fail "FFmpeg no generó fotogramas"

# A common indexed palette keeps the stored ZIP under the firmware limit while
# preserving a stable colour treatment across the complete animation.
ffmpeg -v error -framerate "$fps" -i "$build_dir/animation/part0/frame-%05d.png" \
  -vf "palettegen=max_colors=256:stats_mode=full" -y "$build_dir/palette.png"
mkdir -p "$build_dir/indexed"
ffmpeg -v error -framerate "$fps" -i "$build_dir/animation/part0/frame-%05d.png" \
  -i "$build_dir/palette.png" -lavfi "paletteuse=dither=sierra2_4a:alpha_threshold=0" \
  -start_number 0 -y "$build_dir/indexed/frame-%05d.png"
rm -rf -- "$build_dir/animation/part0"
mv -- "$build_dir/indexed" "$build_dir/animation/part0"

printf '%s %s %s\np 0 0 part0\n\n' \
  "$CANVAS_WIDTH" "$CANVAS_HEIGHT" "$fps" > "$build_dir/animation/desc.txt"

first_frame="$build_dir/animation/part0/frame-00000.png"
cp -- "$first_frame" "$build_dir/bootlogo.png"
convert "$first_frame" -background "#$background_hex" -alpha remove -alpha off \
  -type TrueColor "BMP3:$build_dir/logo/2400_900/logo_customer1.bmp"

gif_filter="[0:v]fps=$gif_fps,scale=${GIF_WIDTH}:${GIF_HEIGHT}:force_original_aspect_ratio=decrease,pad=${GIF_WIDTH}:${GIF_HEIGHT}:(ow-iw)/2:(oh-ih)/2:${background_color},split[gif_a][gif_b];[gif_a]palettegen=max_colors=256:stats_mode=full[gif_palette];[gif_b][gif_palette]paletteuse=dither=sierra2_4a[gif]"
ffmpeg -v error -i "$video_path" -an -filter_complex "$gif_filter" -map '[gif]' \
  -loop 0 -y "$build_dir/bootanimation.gif"

(
  cd "$build_dir/animation"
  (printf '%s\n' desc.txt; find part0 -type f -name 'frame-*.png' | sort) |
    zip -0 -q "$build_dir/bootanimation.zip" -@
)
(
  cd "$build_dir/logo"
  zip -0 -q "$build_dir/bootlogo.zip" 2400_900/logo_customer1.bmp
)

animation_size=$(stat -c '%s' "$build_dir/bootanimation.zip")
(( animation_size <= MAX_ANIMATION_BYTES )) || \
  fail "bootanimation.zip supera 100 MiB: $animation_size bytes"

unzip -tq "$build_dir/bootanimation.zip" >/dev/null
unzip -tq "$build_dir/bootlogo.zip" >/dev/null
[[ "$(zipinfo -1 "$build_dir/bootlogo.zip")" == "2400_900/logo_customer1.bmp" ]] || \
  fail "estructura de bootlogo.zip no válida"
[[ "$(identify -format '%m %wx%h %[depth]' "$build_dir/logo/2400_900/logo_customer1.bmp")" == \
  "BMP3 ${CANVAS_WIDTH}x${CANVAS_HEIGHT} 8" ]] || fail "BMP incompatible"

frame_dimensions=$(find "$build_dir/animation/part0" -type f -name 'frame-*.png' \
  -exec identify -format '%wx%h\n' {} + | sort -u)
frame_opaque=$(find "$build_dir/animation/part0" -type f -name 'frame-*.png' \
  -exec identify -format '%[opaque]\n' {} + | tr '[:upper:]' '[:lower:]' | sort -u)
[[ "$frame_dimensions" == "${CANVAS_WIDTH}x${CANVAS_HEIGHT}" ]] || \
  fail "dimensiones de fotogramas no válidas: $frame_dimensions"
[[ "$frame_opaque" == "true" ]] || fail "los fotogramas deben ser opacos"
[[ "$(identify -format '%wx%h' "$build_dir/bootlogo.png")" == \
  "${CANVAS_WIDTH}x${CANVAS_HEIGHT}" ]] || fail "preview de logo no válida"
[[ "$(identify -format '%wx%h' "$build_dir/bootanimation.gif[0]")" == \
  "${GIF_WIDTH}x${GIF_HEIGHT}" ]] || fail "preview GIF no válida"

if zipinfo -l "$build_dir/bootanimation.zip" | awk '
  /^-/ && $7 != "stor" { invalid=1 } END { exit invalid }
' && zipinfo -l "$build_dir/bootlogo.zip" | awk '
  /^-/ && $7 != "stor" { invalid=1 } END { exit invalid }
'; then
  :
else
  fail "los ZIP deben usar el método store"
fi

# Copy every candidate first, so an allocation failure cannot leave a mixture of
# old and new outputs. Each final rename is atomic on this filesystem.
for output_name in bootanimation.zip bootlogo.zip bootanimation.gif bootlogo.png; do
  temporary_output="$package_real/.${output_name}${output_suffix}"
  cp -- "$build_dir/$output_name" "$temporary_output"
done
for output_name in bootanimation.zip bootlogo.zip bootanimation.gif bootlogo.png; do
  temporary_output="$package_real/.${output_name}${output_suffix}"
  mv -f -- "$temporary_output" "$package_real/$output_name"
done

echo "Paquete generado: $package_real"
echo "Fondo detectado: #${background_hex^^}"
echo "Cadencia: $fps FPS ($gif_fps FPS en la previsualización)"
echo "Fotogramas: $frame_count"
echo "Tamaño bootanimation.zip: $animation_size bytes"
