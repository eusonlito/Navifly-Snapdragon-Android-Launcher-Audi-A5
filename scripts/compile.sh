#!/usr/bin/env bash
# Genera el APK release optimizado e instalable de A5 Cockpit.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}       A5 COCKPIT - PRODUCTION BUILD         ${NC}"
echo -e "${GREEN}=============================================${NC}"

echo -e "\n${BLUE}[1/3] Verificando Java...${NC}"
if ! command -v java >/dev/null 2>&1; then
    echo -e "${RED}Error: Java no está instalado.${NC}"
    exit 1
fi
echo -e "Java: ${YELLOW}$(java -version 2>&1 | head -n 1)${NC}"

echo -e "\n${BLUE}[2/3] Ejecutando pruebas y compilación release optimizada...${NC}"
chmod +x gradlew
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease

APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
APK_OUTPUT="out/A5Cockpit.apk"
MAPPING_SOURCE="app/build/outputs/mapping/release/mapping.txt"

if [[ ! -f "$APK_SOURCE" ]]; then
    echo -e "${RED}Error: no se ha generado $APK_SOURCE.${NC}"
    exit 1
fi

echo -e "\n${BLUE}[3/3] Preparando artefacto...${NC}"
mkdir -p out
cp "$APK_SOURCE" "$APK_OUTPUT"
sha256sum "$APK_OUTPUT" > "$APK_OUTPUT.sha256"
if [[ -f "$MAPPING_SOURCE" ]]; then
    cp "$MAPPING_SOURCE" "out/A5Cockpit-mapping.txt"
fi

echo
echo -e "${GREEN}APK generado correctamente:${NC}"
echo -e "${YELLOW}$PROJECT_DIR/$APK_OUTPUT${NC}"
