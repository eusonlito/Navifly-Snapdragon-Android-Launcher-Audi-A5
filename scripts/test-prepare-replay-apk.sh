#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
source "$SCRIPT_DIR/prepare-replay-apk.sh"

TEST_DIR=$(mktemp -d)
ORIGINAL_PATH=$PATH
cleanup_test() {
    rm -rf "$TEST_DIR"
}
trap cleanup_test EXIT INT TERM

make_tool() {
    local path=$1
    mkdir -p "$(dirname "$path")"
    printf '#!/usr/bin/env bash\nexit 0\n' > "$path"
    chmod +x "$path"
}

make_tool_pair() {
    local directory=$1
    make_tool "$directory/zipalign"
    make_tool "$directory/apksigner"
}

assert_candidate_pair() {
    local expected_dir=$1
    local path_value=$2
    shift 2
    local result
    local -a tools
    result=$(resolve_build_tools_from_candidates "$path_value" "$@")
    mapfile -t tools <<< "$result"
    [[ ${tools[0]} == "$expected_dir/zipalign" ]]
    [[ ${tools[1]} == "$expected_dir/apksigner" ]]
}

# A complete pair on PATH has priority over every configured SDK.
path_tools="$TEST_DIR/path-tools"
sdk_tools="$TEST_DIR/sdk-path/build-tools/40.0.0"
make_tool_pair "$path_tools"
make_tool_pair "$sdk_tools"
assert_candidate_pair "$path_tools" "$path_tools:$ORIGINAL_PATH" "$TEST_DIR/sdk-path"

# The newest incomplete version is skipped, but both tools still come from
# the newest complete version in the first valid SDK root.
fallback_sdk="$TEST_DIR/sdk-fallback"
make_tool "$fallback_sdk/build-tools/36.0.0/zipalign"
make_tool "$fallback_sdk/build-tools/35.0.2/apksigner"
make_tool_pair "$fallback_sdk/build-tools/35.0.1"
make_tool_pair "$fallback_sdk/build-tools/debian"
assert_candidate_pair "$fallback_sdk/build-tools/35.0.1" "$TEST_DIR/empty-path" "$fallback_sdk"

# SDK roots retain their configured priority even when a later root contains a
# newer complete version.
first_sdk="$TEST_DIR/sdk-first"
second_sdk="$TEST_DIR/sdk-second"
make_tool_pair "$first_sdk/build-tools/34.0.0"
make_tool_pair "$second_sdk/build-tools/40.0.0"
assert_candidate_pair "$first_sdk/build-tools/34.0.0" \
    "$TEST_DIR/empty-path" "$first_sdk" "$second_sdk"

# sdk.dir is considered after the environment roots and before HOME defaults.
project_with_sdk="$TEST_DIR/project-sdk-dir"
sdk_from_properties="$TEST_DIR/sdk-properties"
mkdir -p "$project_with_sdk"
printf 'sdk.dir=%s\n' "$sdk_from_properties" > "$project_with_sdk/local.properties"
make_tool_pair "$sdk_from_properties/build-tools/34.0.0"
local_sdk=$(read_local_sdk_dir "$project_with_sdk")
[[ $local_sdk == "$sdk_from_properties" ]]
assert_candidate_pair "$sdk_from_properties/build-tools/34.0.0" \
    "$TEST_DIR/empty-path" "$local_sdk" "$TEST_DIR/home-empty/Android/Sdk"

# A normal Android Studio SDK under HOME works without exported SDK variables.
home_sdk="$TEST_DIR/home-default/Android/Sdk/build-tools/33.0.2"
make_tool_pair "$home_sdk"
assert_candidate_pair "$home_sdk" "$TEST_DIR/empty-path" \
    "$TEST_DIR/home-default/Android/Sdk"

# No complete pair must fail before any APK work or temporary work directory.
failure_output="$TEST_DIR/failure-output"
if resolve_build_tools_from_candidates "$TEST_DIR/empty-path" "$TEST_DIR/sdk-incomplete" \
    >"$failure_output" 2>&1; then
    echo "La resolución aceptó un entorno sin Build Tools" >&2
    exit 1
fi
grep -q "No se encuentra una versión completa" "$failure_output"

echo "Test de resolución de Android Build Tools: OK"
