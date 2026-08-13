# Contributing

Thank you for helping improve A5 Launcher.

## Development workflow

1. Create a focused branch from `master`.
2. Keep device dumps, logs, API keys, APKs and proprietary applications outside
   the repository.
3. Make the smallest coherent change and add or update tests where behavior
   changes.
4. Run:

   ```bash
   ./scripts/check-public-repo.sh
   ./gradlew testDebugUnitTest lintRelease assembleDebug
   ```

5. Describe device-specific validation in the pull request when relevant.

## Translations

English resources are the fallback. Spanish translations live in `values-es`.
New languages should add a standard Android `values-<locale>` directory and
must keep the complete string-key set. Do not hard-code user-facing text in
Kotlin.

## Test data and privacy

Use small synthetic fixtures. Do not contribute real journey logs, GPS traces,
installed-application inventories, logcat archives, firmware extracts or API
responses containing credentials or personal information.

## Third-party material

Only contribute assets or data that may legally be redistributed. Include the
source, author, license and any required attribution in
`THIRD_PARTY_NOTICES.md`. Trademarks must not be presented as project ownership
or endorsement.
