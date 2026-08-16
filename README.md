# A5 Launcher

A5 Launcher is an Android home-screen replacement built for one specific
NaviFly head unit installed in an Audi A5. The installed application is named
**A5 Cockpit** and brings the instrument cluster, vehicle telemetry, map, trip
data, applications and an optional AI Assistant into one interface.

> This is an independent hobby project. It is not affiliated with or endorsed
> by Audi, Volkswagen Group, Waze, Google, OpenAI, ChoiceWay, NaviFly or any
> other vehicle or software manufacturer.

[Leer en castellano](README.es.md)

## Compatibility: Read This Before Installing

This application is **not a universal Android launcher**. It is designed,
calibrated and validated exclusively for this combination:

- [NaviFly Snapdragon 685 Newest Android System 8+256G 2K for Audi A4/A5](https://www.alibaba.com/product-detail/NaviFly-Snapdragon-685-Newest-Android-System_11000030157012.html).
- Android 14 on the ARM64 architecture.
- An ultra-wide display with an exact 2400×896 visible area at 320 dpi.
- The proprietary ChoiceWay/NaviFly applications, particularly `EventCenter`,
  `Settings` and `FatSet`.
- CAN communication and original-MMI switching through that firmware's private
  interfaces.
- Development configuration: 2015 Audi A5 2.0 TDI 150 PS manual, MMI 3G Basic
  and the device's `3G` CAN protocol.

The seller's page advertises physical compatibility with the 2008–2014 Audi A4
and 2008–2016 Audi A5, but this **does not guarantee that A5 Cockpit works with
every one of those combinations**. A different firmware, CAN box, protocol,
resolution or aspect ratio may prevent telemetry, misalign the interface or
disable MMI actions.

Do not install this build expecting compatibility with:

- another Android head unit, even if it also uses a Snapdragon 685;
- Android Auto or CarPlay as a conventional application;
- another resolution, density or aspect ratio;
- another vehicle, engine, transmission, CAN box or firmware;
- a unit without the proprietary ChoiceWay applications.

## Main Features

- Speed, engine RPM, estimated gear and vehicle warning indicators.
- Time, trip, estimated consumption, distance since refuelling, range, fuel and
  odometer values.
- MapLibre vector map with light/dark modes, local cache, following, rotation
  and touch controls.
- Importable GeoJSON points of interest with configurable categories, icons and
  pulses.
- Speed-camera POI markers supplied through the user's local files.
- Reorderable top actions and trip blocks using a long press.
- Application launcher, app switcher, settings and original Audi MMI access.
- Optional OpenAI or Gemini AI Assistant, with Google Places for nearby searches
  and Waze navigation.
- In-app APK update through Android's document picker.
- Map and AI Assistant diagnostic log export.

Consumption, range and gear are estimates built from the data exposed by the
firmware. They do not replace the vehicle's original instruments.

## Screenshots

![A5 Launcher dashboard](docs/screenshots/dashboard.png)

| Applications | Map and POI Settings |
|---|---|
| ![Applications](docs/screenshots/applications.png) | ![Map and POI Settings](docs/screenshots/settings-map.png) |

| AI Assistant Settings | System Settings |
|---|---|
| ![AI Assistant Settings](docs/screenshots/settings-ai.png) | ![System Settings](docs/screenshots/settings-system.png) |

## Installation for Users

### 1. Get the APK

Download `A5Cockpit.apk` from the corresponding GitHub release when that release
provides an APK. If only source code is available, build the APK by following
the [Build](#build) section.

Only install an APK obtained from this repository or a person you trust. Android
can update an existing installation only when the APK has the same application
identifier and signing identity.

### 2. Transfer the APK to the NaviFly

Any of these methods can be used:

- download the APK directly in the device's browser;
- copy it to `Downloads` from a USB drive;
- place it in a shared Dropbox folder and download it with the official Dropbox
  application;
- transfer it from a computer using ADB.

Dropbox is only a practical suggestion for sharing updates; A5 Cockpit does not
depend on it. If Dropbox is not listed in the document picker, download the file
to `Downloads` first.

### 3. Allow the Initial Installation

1. Open the APK from Dropbox, the browser or the file manager.
2. If Android blocks the installation, select **Settings** in the warning.
3. Enable **Allow From This Source** for the application that opened the APK.
4. Go back, confirm **Install**, then open A5 Cockpit.

There is no need to enable unknown sources globally or disable an Android
security feature.

### 4. Select A5 Cockpit as the Launcher

1. Press the NaviFly's physical or touch **Home** button.
2. Select **A5 Cockpit** from the available home applications.
3. Choose **Always**, not **Just Once**.

If Android does not show that prompt, open the device settings and look for a
path similar to **Apps > Default Apps > Home App**. The exact wording may differ
between firmware versions.

Press Home several times and reboot the NaviFly to confirm that it always
returns to A5 Cockpit. Do not uninstall or disable Quickstep, the original
launcher or system applications: they are part of the recovery path.

## Required Permissions and Settings

Android asks for each permission when the associated feature is used for the
first time. General storage access is not required: APK, POI and log operations
use Android's secure document picker.

| Permission or Setting | When It Is Needed | How to Enable It |
|---|---|---|
| **Precise Location** | Map following and rotation, GPS status and nearby searches | Accept the prompt when opening the dashboard and allow precise location while using the application. |
| **Microphone** | AI Assistant only | Select a provider and press the AI Assistant icon; Android will show the permission prompt. |
| **“A5 Cockpit Recent Apps” Accessibility Service** | Native Android recent-apps view only | Press the app-switch button once to open Accessibility Settings, then enable only this service. |
| **Install Unknown Apps for A5 Cockpit** | Updating from Launcher Settings only | It is requested automatically during the first in-app update. |
| **Default Home Application** | Home and device startup should use A5 Cockpit | Select A5 Cockpit as the home application and confirm **Always**. |

Internet access and the telemetry foreground service are declared by the
application and do not show a runtime permission dialog. Telemetry depends on
`com.szchoiceway.eventcenter`; no manual permission can replace that application
or make another firmware compatible.

If the firmware includes a battery manager and stops telemetry while Waze is in
the foreground, set A5 Cockpit's battery use to **Unrestricted**. Do this only if
the problem is observed; it is not required on the reference configuration.

## First-Time Configuration

1. Open **Launcher Settings > Map** and select the map colour, style and maximum
   cache size.
2. Check that Map, Network and GPS report a valid state. The dashboard remains
   usable when the map has no connectivity.
3. Open **Points of Interest** to import:
   - one or more `.geojson` catalogues;
   - `categories.json`, which defines each category's icon, pulse and colour;
   - 64×64-pixel PNG icons, the recommended visual size.
4. Under **AI Assistant**, select Disabled, OpenAI or Google Gemini. OpenAI,
   Gemini and Google Places keys are stored and validated separately. The AI
   Assistant icon is hidden from the dashboard while the feature is disabled.
5. Long-press a top action or bottom trip block to reorder it.

API keys are optional and are not bundled with the repository.

## Updating Through the Launcher

The most convenient workflow is a shared Dropbox folder containing the latest
`A5Cockpit.apk`:

1. Copy the new APK to Dropbox on a computer and wait for it to synchronise.
2. In the car, open **Launcher Settings > System > Update**.
3. Press **Update**.
4. Open Dropbox in Android's document picker and choose the APK. An APK already
   downloaded to `Downloads` can be selected instead.
5. On the first update, Android asks whether A5 Cockpit may install
   applications. Enable **Allow From This Source**, go back and continue.
6. Confirm the installation in Android's installer.

The operation can be cancelled without selecting a file by pressing **Back**.
The application checks that the APK belongs to A5 Cockpit, and Android also
verifies its signature. A regular update preserves settings, keys, cache and
local data.

If Android reports **App Not Installed** or a package conflict, the most common
cause is a different signing key. Do not uninstall the working version without
keeping a recovery path: uninstalling also deletes the application's local data.

## Boot Logo and Boot Animation

The device imports two independent packages:

- `bootanimation.zip`: the animation shown while Android starts;
- `bootlogo.zip`: the static image shown before the animation.

The ready-to-import example lives in [`boot/default`](boot/default). Both
packages target 2400×896 and share exactly the same first frame.

### Recommended Import Order

1. Keep a copy of the original packages and confirm that the recovery/FatSet
   tool remains accessible.
2. Copy both ZIP files to storage visible to the firmware importer. Dropbox can
   transfer them, but some importers expose only local storage or USB drives.
3. Open the NaviFly's **FatSet** administration application.
4. Import `bootanimation.zip` first, then reboot and verify it.
5. Only after Android starts successfully, import `bootlogo.zip`, select the new
   logo and reboot again.
6. Never power off the unit or cut vehicle power during an import.

An invalid animation normally leaves that phase black, but an invalid logo acts
earlier and carries more risk. This repository **does not contain or modify the
real bootloader, MCU or boot partitions**. Never flash a file from this project
as a bootloader. The correct name of the second package is `bootlogo.zip`.

To create custom packages from a video, read the complete
[Boot Logo and Animation](docs/feature-boot/BOOT_ANIMATION.md) guide. The
generator preserves the source proportions, creates both ZIP files and produces
GIF and PNG previews.

## Troubleshooting

### Telemetry Is Missing

- Confirm that the exact device and firmware described under Compatibility are
  in use.
- Check that `EventCenter` remains installed and that the factory settings use
  the correct CAN protocol for the vehicle.
- Reboot the device. Additional Android permissions cannot replace ChoiceWay's
  proprietary communication.

### The Map Does Not Follow the Position

- Grant precise location and enable the device's GPS/location service.
- Press the recenter button after panning or zooming the map.
- Without Internet, previously cached areas remain available; an area that has
  never been loaded requires connectivity.

### The Recent Apps Button Does Not Work

Enable **A5 Cockpit Recent Apps** in Accessibility Settings. No other
accessibility service is required.

### The AI Assistant Icon Is Missing

Open **Launcher Settings > AI Assistant**, select a provider, save a valid key
and grant microphone access. Google Places is only needed for requests such as
“the nearest fuel station”.

### The Device Starts Another Launcher

Press Home, choose A5 Cockpit again and confirm **Always**. Also check the home
application in Android settings. Do not disable Quickstep or firmware
applications to force startup.

## Build

Developer requirements:

- JDK 21;
- Android SDK Platform 37;
- Android SDK Build Tools compatible with Android Gradle Plugin 9.3.1;
- a 64-bit build system.

The Gradle wrapper is included:

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
./scripts/compile.sh
```

`scripts/compile.sh` runs tests, Lint and the optimized release build. It writes
the APK, its SHA-256 checksum and the R8 mapping, when present, to `out/`.

The public build currently signs release artifacts with Android's standard
debug key for local installation. Anyone distributing updates must use a stable
private key, keep it outside the repository and sign every update with the same
identity.

### Emulator and Replay

```bash
python3 -m pip install -r requirements-emulator.txt
./scripts/emulator.sh --replay /path/to/can_bus_log.jsonl
```

To synchronise files available to Android's document picker:

```bash
./scripts/emulator.sh \
  --replay /path/to/can_bus_log.jsonl \
  --files /path/to/files
```

They are stored under `Downloads/A5-Cockpit`, and later file changes are
synchronised through ADB while the script keeps running.

## Security, Documentation and Contributing

No API key is bundled with the project. Never publish `.env`, signing keys,
device dumps, private APKs or diagnostic files. Read [SECURITY.md](SECURITY.md)
before distributing an APK.

The technical documentation index is in [docs/INDEX.md](docs/INDEX.md). Read
[CONTRIBUTING.md](CONTRIBUTING.md) before contributing, especially the rules for
translations, assets and test data.

## Licence

Original project code is released under the [MIT Licence](LICENSE).
Dependencies, map data, trademarks and third-party visual assets remain under
their respective terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
