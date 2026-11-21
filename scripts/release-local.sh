#!/usr/bin/env bash
# Prepare a signed release locally. Only the explicit publish command writes to GitHub.
set -euo pipefail

die() { echo "$*" >&2; exit 1; }

verify_apk() {
    local apk=$1 version=$2 code=$3 certificate package
    # SHA-256 of the public signing certificate on the existing v1.3.50 release.
    certificate=$(apksigner verify --print-certs "$apk" | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p')
    [[ $certificate == 356642b6d5e12cef2db3679f9cf41a9383fc9ae320fcd5f3ec770d11a68e3bd1 ]] ||
        die "El certificado del APK no coincide con la firma de distribución."
    package=$(aapt dump badging "$apk" | sed -n '1p')
    [[ $package == "package: name='com.lito.a5launcher' versionCode='$code' versionName='$version'"* ]] ||
        die "El paquete o la versión del APK no corresponde a $version ($code)."
}

verify_assets() (
    cd "$1"
    sha256sum A5Cockpit.apk | cmp - A5Cockpit.apk.sha256
    verify_apk A5Cockpit.apk "$2" "$3"
)

prepare() {
    local commit=$1 version=$2 code=$3 dir=$4 temp=$5
    if [[ ! -d $dir ]]; then
        ./scripts/check-public-repo.sh
        ./scripts/test-check-public-repo.sh
        ./scripts/test-prepare-replay-apk.sh
        python3 scripts/test-release-version.py
        python3 scripts/test-replay-emulator-gps.py
        ./boot/scripts/test-generate.sh
        (
            set -a
            if [[ -z ${A5_RELEASE_KEYSTORE_PATH:-} ]]; then
                source "$HOME/.android/a5-cockpit-release/credentials.txt"
            fi
            set +a
            A5_REQUIRE_RELEASE_SIGNING=true ./scripts/compile.sh
        )
        cp out/A5Cockpit.apk "$temp/"
        (cd "$temp" && sha256sum A5Cockpit.apk > A5Cockpit.apk.sha256)
        verify_assets "$temp" "$version" "$code"
        [[ $(git rev-parse HEAD) == "$commit" && -z $(git status --porcelain) ]] ||
            die "El código cambió durante la preparación."
        mv -T "$temp" "$dir"
    else
        verify_assets "$dir" "$version" "$code"
    fi
    echo "v$version ($code) preparada en $dir"
}

publish() {
    local commit=$1 tag=$2 dir=$3 download=$4 repo draft
    repo=$(gh repo view "$(git remote get-url --push origin)" --json nameWithOwner --jq .nameWithOwner)
    git push --atomic origin "$commit:refs/heads/master" "$commit:refs/tags/$tag"

    if ! draft=$(gh release view "$tag" --repo "$repo" --json isDraft --jq .isDraft); then
        gh release create "$tag" --repo "$repo" --verify-tag --draft --title "$tag" --generate-notes
        draft=true
    fi
    if [[ $draft == true ]]; then
        gh release upload "$tag" "$dir/A5Cockpit.apk" "$dir/A5Cockpit.apk.sha256" --repo "$repo" --clobber
    fi
    # Read back both assets before exposing a draft, and on retries of an already public release.
    gh release download "$tag" --repo "$repo" --dir "$download" -p A5Cockpit.apk -p A5Cockpit.apk.sha256
    cmp "$dir/A5Cockpit.apk" "$download/A5Cockpit.apk"
    cmp "$dir/A5Cockpit.apk.sha256" "$download/A5Cockpit.apk.sha256"
    if [[ $draft == true ]]; then
        gh release edit "$tag" --repo "$repo" --draft=false
    fi
    gh release view "$tag" --repo "$repo" --json url --jq .url
}

main() {
    [[ $# == 1 && ( $1 == prepare || $1 == publish ) ]] || die "Uso: $0 prepare|publish"
    cd "$(dirname "${BASH_SOURCE[0]}")/.."
    [[ $(git branch --show-current) == master && -z $(git status --porcelain) ]] ||
        die "La release necesita un commit limpio en master."
    local commit version_info version code dir
    commit=$(git rev-parse HEAD)
    version_info=$(python3 scripts/release-version.py version)
    read -r version code <<< "$version_info"
    dir="out/releases/$commit"
    mkdir -p out/releases
    exec 9>out/releases/.lock
    flock 9
    release_temp=$(mktemp -d out/releases/.tmp.XXXXXXXX)
    trap 'rm -rf -- "$release_temp"' EXIT
    case "$1" in
        prepare) prepare "$commit" "$version" "$code" "$dir" "$release_temp" ;;
        publish)
            [[ -d $dir ]] || die "Prepara primero la release v$version de este commit."
            verify_assets "$dir" "$version" "$code"
            publish "$commit" "v$version" "$dir" "$release_temp"
            ;;
    esac
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    main "$@"
fi
