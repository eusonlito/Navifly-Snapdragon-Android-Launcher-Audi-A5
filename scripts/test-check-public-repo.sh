#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CHECKER="$ROOT_DIR/scripts/check-public-repo.sh"
TEST_ROOT=$(mktemp -d)

cleanup() {
    rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT INT TERM

create_repository() {
    local repository=$1
    mkdir -p "$repository/scripts" "$repository/gradle/wrapper" "$repository/boot/default"
    cp -- "$CHECKER" "$repository/scripts/check-public-repo.sh"
    chmod +x "$repository/scripts/check-public-repo.sh"
    touch "$repository/scripts/compile.sh" "$repository/scripts/emulator.sh" \
        "$repository/scripts/check-dependencies.sh"
    touch "$repository/gradlew" "$repository/gradle/wrapper/gradle-wrapper.jar" \
        "$repository/LICENSE" "$repository/README.md" "$repository/SECURITY.md" \
        "$repository/boot/default/bootanimation.zip"
    chmod +x "$repository/gradlew" "$repository/scripts/compile.sh" \
        "$repository/scripts/emulator.sh" "$repository/scripts/check-dependencies.sh"
    printf '%s\n' \
        '.env' \
        'debug/private-trip.jsonl' \
        'boot/local-variant/bootanimation.zip' > "$repository/.gitignore"
    git -C "$repository" init -q
    git -C "$repository" add .
}

assert_required_rejected() {
    local label=$1
    local relative_path=$2
    local mode=$3
    local expected=$4
    local repository="$TEST_ROOT/required-$label"
    local output

    create_repository "$repository"
    case "$mode" in
        missing)
            rm -- "$repository/$relative_path"
            git -C "$repository" add -u -- "$relative_path"
            ;;
        untracked)
            git -C "$repository" rm -q --cached -- "$relative_path"
            ;;
        non-executable)
            chmod -x "$repository/$relative_path"
            git -C "$repository" add --chmod=-x -- "$relative_path"
            ;;
        *)
            printf 'Unknown required-file test mode: %s\n' "$mode" >&2
            exit 1
            ;;
    esac

    if output=$(cd "$repository" && ./scripts/check-public-repo.sh 2>&1); then
        printf 'Expected required-file rejection for %s (%s)\n' "$label" "$relative_path" >&2
        exit 1
    fi
    grep -Fq "$expected" <<<"$output" || {
        printf 'Unexpected rejection for %s: %s\n' "$label" "$output" >&2
        exit 1
    }
}

assert_rejected() {
    local label=$1
    local relative_path=$2
    local fixture_type=${3:-text}
    local repository="$TEST_ROOT/rejected-$label"
    local output

    create_repository "$repository"
    mkdir -p "$(dirname "$repository/$relative_path")"
    if [[ $fixture_type == binary ]]; then
        printf '\000\377\001\376' > "$repository/$relative_path"
    else
        printf 'private fixture\n' > "$repository/$relative_path"
    fi
    git -C "$repository" add "$relative_path"

    if output=$(cd "$repository" && ./scripts/check-public-repo.sh 2>&1); then
        printf 'Expected rejection for %s (%s)\n' "$label" "$relative_path" >&2
        exit 1
    fi
    grep -Fq "private workspace or provider-specific data would be published: $relative_path" <<<"$output" || {
        printf 'Unexpected rejection for %s: %s\n' "$label" "$output" >&2
        exit 1
    }
}

assert_allowed_near_matches() {
    local repository="$TEST_ROOT/allowed"
    local -a allowed_paths=(
        'localization/readme.txt'
        'debugging/guide.txt'
        'dropboxer/component.txt'
        'scripts-localized/helper.sh'
        'docs/update-race-radars.sh.md'
        'data/race-portugal.geojson'
    )
    local path

    create_repository "$repository"
    for path in "${allowed_paths[@]}"; do
        mkdir -p "$(dirname "$repository/$path")"
        printf 'public fixture\n' > "$repository/$path"
    done
    git -C "$repository" add .
    (cd "$repository" && ./scripts/check-public-repo.sh >/dev/null)
}

assert_rejected local-root 'LoCaL/private.bin'
assert_rejected debug-root 'DeBuG/private.bin'
assert_rejected binary-dropbox 'DROPBOX/private.bin' binary
assert_rejected scripts-local-root 'Scripts-Local/private.bin'
assert_rejected update-race-root 'Update-Race-Radars.SH'
assert_rejected update-race-nested 'tools/Update-Race-Radars.SH'
assert_rejected race-spain 'catalog/RACE-SPAIN.geojson'
assert_allowed_near_matches
assert_required_rejected missing-compile 'scripts/compile.sh' missing \
    'missing required file: scripts/compile.sh'
assert_required_rejected untracked-emulator 'scripts/emulator.sh' untracked \
    'required file is not tracked: scripts/emulator.sh'
assert_required_rejected non-executable-dependencies 'scripts/check-dependencies.sh' non-executable \
    'required command is not executable: scripts/check-dependencies.sh'

printf 'Public repository checker tests: OK\n'
