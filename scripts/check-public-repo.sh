#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

fail() {
    printf 'public-repo check failed: %s\n' "$*" >&2
    exit 1
}

for required in \
    gradlew \
    gradle/wrapper/gradle-wrapper.jar \
    LICENSE \
    README.md \
    SECURITY.md \
    scripts/compile.sh \
    scripts/emulator.sh \
    scripts/check-dependencies.sh; do
    [[ -f $required ]] || fail "missing required file: $required"
    git ls-files --error-unmatch -- "$required" >/dev/null 2>&1 || \
        fail "required file is not tracked: $required"
done

for executable in \
    gradlew \
    scripts/compile.sh \
    scripts/emulator.sh \
    scripts/check-dependencies.sh; do
    [[ -x $executable ]] || fail "required command is not executable: $executable"
done

git check-ignore --no-index -q .env || fail ".env must be ignored"
git check-ignore --no-index -q debug/private-trip.jsonl || fail "debug logs must be ignored"
git check-ignore --no-index -q boot/local-variant/bootanimation.zip || fail "local boot variants must be ignored"

for published in gradle/wrapper/gradle-wrapper.jar boot/default/bootanimation.zip; do
    if git check-ignore --no-index -q "$published"; then
        fail "$published must not be ignored"
    fi
done

mapfile -d '' candidates < <(git ls-files --cached --others --exclude-standard -z)

for path in "${candidates[@]}"; do
    normalized_path=${path,,}
    case "$normalized_path" in
        local/*|debug/*|dropbox/*|scripts-local/*|update-race-radars.sh|*/update-race-radars.sh|*race-spain*)
            fail "private workspace or provider-specific data would be published: $path"
            ;;
        .env|.env.*|*.jks|*.keystore|keystore.properties|*.jsonl|*.apk|*.aab|*.aar|*.so)
            fail "sensitive or generated file would be published: $path"
            ;;
    esac
    if [[ -L $path ]]; then
        target=$(realpath "$path")
        [[ $target == "$ROOT_DIR"/* ]] || fail "external symlink would be published: $path"
    fi
    if [[ -f $path ]] && (( $(stat -c %s "$path") > 100 * 1024 * 1024 )); then
        fail "file exceeds GitHub's 100 MiB limit: $path"
    fi
done

text_candidates=()
for path in "${candidates[@]}"; do
    [[ $path == .gitignore || $path == scripts/check-public-repo.sh ]] && continue
    [[ -f $path ]] && grep -Iq . "$path" && text_candidates+=("$path")
done
if ((${#text_candidates[@]})) && grep -En '(/home/[^/]+|Dropbox|dropbox/)' "${text_candidates[@]}"; then
    fail "a public text file contains a personal path or Dropbox dependency"
fi

secret_pattern='(sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)'
if ((${#text_candidates[@]})) && grep -EIn "$secret_pattern" "${text_candidates[@]}"; then
    fail "a likely credential is present in the public tree"
fi

printf 'Public repository policy: OK\n'
