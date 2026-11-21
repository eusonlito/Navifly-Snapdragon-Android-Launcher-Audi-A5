# Instructions for coding agents

Work on the branch that was active when the task began. Complete the change,
run the relevant checks and commit locally. Report the result and wait for the
owner's explicit authorization for each publication before pushing commits or
tags, uploading assets, creating releases or dispatching GitHub Actions.

## Publishing an APK

1. From a clean `master`, run `./scripts/release-local.sh prepare`. This runs
   the checks, builds the optimized APK locally and verifies its application ID,
   version, production certificate and SHA-256. Keep the signed artifacts in
   `out/releases/<commit>/` for publication and retries.
2. After authorization for this version, run `./scripts/release-local.sh publish`.
   It pushes the approved commit and tag together, uploads `A5Cockpit.apk` and
   `A5Cockpit.apk.sha256` to a GitHub Release and compares both downloads with
   the prepared files before publishing the draft. Report the resulting URL.
3. GitHub Actions is available for manually requested CI checks. Releases are
   built and published from the workstation.

Gradle and the release script both use `scripts/release-version.py version`.
Each first-parent commit increments the patch version and Android versionCode.
Use a full checkout. Change the minor version only when the owner requests it.

Keep a secure backup of the distribution key and credentials in
`~/.android/a5-cockpit-release/`. Never print or commit them.
