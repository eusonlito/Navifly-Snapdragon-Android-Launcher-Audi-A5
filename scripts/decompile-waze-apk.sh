#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${1:-$repo_root/local/waze-5-22-90-123.apk}"
output_path="${2:-$repo_root/local/waze-5-22-90-123-jadx}"
jadx_bin="${JADX_BIN:-$(command -v jadx || true)}"

if [[ -z "$jadx_bin" && -x /opt/jadx/bin/jadx ]]; then
  jadx_bin=/opt/jadx/bin/jadx
fi

if [[ ! -f "$apk_path" ]]; then
  echo "APK not found: $apk_path" >&2
  exit 1
fi

if [[ -z "$jadx_bin" || ! -x "$jadx_bin" ]]; then
  echo "jadx executable not found; set JADX_BIN or install jadx." >&2
  exit 1
fi

if [[ -d "$output_path/sources" && -d "$output_path/resources" ]]; then
  echo "Using existing decompilation: $output_path"
  exit 0
fi

if [[ -e "$output_path" ]]; then
  echo "Output already exists: $output_path" >&2
  echo "Choose a new output directory as the second argument." >&2
  exit 1
fi

mkdir -p "$(dirname "$output_path")"
"$jadx_bin" --deobf --show-bad-code -d "$output_path" "$apk_path"
