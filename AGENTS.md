# Instructions for coding agents

## Change workflow

- Complete the requested change, run the relevant checks, commit it, and push it
  directly to `master`. The repository owner has explicitly established this as
  the preferred workflow for assistant-made changes; follow a different branch
  or PR flow only when the user asks for one.
- Check the pushed GitHub Actions run and resolve failures before finishing.
- Leave the working tree clean. Do not create a release tag for ordinary code
  changes; a push to `master` runs CI but does not publish a release.

## Publishing an APK

- Distribute APKs as assets on **GitHub Releases**. This project does not publish
  to GitHub Packages; do not describe a Release asset as a Package.
- A release is created only when a `vMAJOR.MINOR.PATCH` tag is pushed. Before
  tagging, update `versionName` and `versionCode` in `app/build.gradle.kts`:
  `versionName` must match the tag without its `v` prefix, and `versionCode`
  must exceed the preceding version tag.
- Confirm the version commit is on `master` and CI passes, then tag and push it:

  ```bash
  git tag -a vX.Y.Z -m "Release vX.Y.Z"
  git push origin vX.Y.Z
  ```

- The tag workflow validates the version, builds and signs the release APK,
  publishes `A5Cockpit.apk` and `A5Cockpit.apk.sha256` to the GitHub Release,
  and removes its temporary keystore. Confirm both jobs pass, the Release and
  assets exist, and the downloaded APK checksum verifies before reporting
  publication complete.
- The workflow needs these GitHub Actions secrets:
  `A5_RELEASE_KEYSTORE_BASE64`, `A5_RELEASE_STORE_PASSWORD`,
  `A5_RELEASE_KEY_ALIAS`, and `A5_RELEASE_KEY_PASSWORD`. Never print their
  values or commit them or the keystore.
- Preserve the established signing identity so installed copies can be updated.
  Keep a secure backup of the local keystore and credentials in
  `~/.android/a5-cockpit-release/`. Local builds without release credentials
  may use the machine's debug key; published releases must use the stable key.
