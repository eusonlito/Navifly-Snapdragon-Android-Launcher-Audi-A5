#!/usr/bin/env bash
# Genera un inventario reproducible de actualizaciones disponibles y del grafo
# release realmente resuelto. No actualiza ni modifica ninguna dependencia.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_DIR="$PROJECT_DIR/build/reports/dependencies"

cd "$PROJECT_DIR"
mkdir -p "$REPORT_DIR"

echo "Consultando actualizaciones de publicación..."
./gradlew --no-daemon dependencyUpdates

echo "Guardando el grafo efectivo de releaseRuntimeClasspath..."
./gradlew --no-daemon :app:dependencies \
    --configuration releaseRuntimeClasspath \
    > "$REPORT_DIR/release-runtime-classpath.txt"

echo
echo "Informes generados:"
echo "  $REPORT_DIR/updates.json"
echo "  $REPORT_DIR/release-runtime-classpath.txt"
echo
echo "El informe sólo detecta candidatos. Antes de actualizar hay que validar"
echo "compatibilidad con AGP, Kotlin/Compose, targetSdk y el Navifly real."
