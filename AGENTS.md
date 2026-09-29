# Instructions for coding agents

## Change workflow

- Complete the requested change, run the relevant checks, commit it, and push it
  directly to `develop`. Do not create working branches or use another branch
  for implementation, commits, or pushes. This owner preference overrides skill
  workflows that propose creating branches.
- Every commit integrated into `develop` must produce a GitHub Release. Check
  both the build and every `publish-release` job and resolve failures before
  finishing. Confirm the APK and checksum are published, then leave the working
  tree clean and synchronized with `origin/develop`.

## Publishing an APK

- Distribute APKs as assets on **GitHub Releases**. This project does not publish
  to GitHub Packages; do not describe a Release asset as a Package.
- Push the source commit to `develop`; GitHub Actions creates its version tag and
  Release automatically. No manual tag or extra version commit is required.
- `scripts/release-version.py` is the version authority, also used by Gradle.
  Each first-parent commit after the automatic-release baseline increments the
  patch version and Android `versionCode` by one: `1.3.0`/4 becomes `1.3.1`/5,
  then `1.3.2`/6. Move to the next minor only when the owner explicitly asks.
  A full Git checkout and Python 3 are required. Check the current version with
  `python3 scripts/release-version.py version`.
- A push containing several commits publishes each separately. A workflow
  retry uses the same version and preserves an already published APK. Releases
  are published after tests and lint pass; the latest download uses the highest
  semantic version even when jobs finish out of order.
- The workflow signs the release APK, publishes `A5Cockpit.apk` and
  `A5Cockpit.apk.sha256`, and removes its temporary keystore. Verify the
  downloaded APK checksum and its version/signing identity before reporting
  publication complete.
- The workflow needs these GitHub Actions secrets:
  `A5_RELEASE_KEYSTORE_BASE64`, `A5_RELEASE_STORE_PASSWORD`,
  `A5_RELEASE_KEY_ALIAS`, and `A5_RELEASE_KEY_PASSWORD`. Never print their
  values or commit them or the keystore.
- Preserve the established signing identity so installed copies can be updated.
  Keep a secure backup of the local keystore and credentials in
  `~/.android/a5-cockpit-release/`. Local builds without release credentials
  may use the machine's debug key; published releases must use the stable key.
