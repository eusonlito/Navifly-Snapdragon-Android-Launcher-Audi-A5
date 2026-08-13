---
title: "feat: Extend the cockpit map and refine OEM controls"
created_at: 2026-07-29
type: feat
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-plan-bootstrap
execution: code
---

# feat: Extend the cockpit map and refine OEM controls

## Goal Capsule

- **Objective:** Make the vector map the full-width background of the central cockpit while keeping both gauges above it, replace the generic MMI car symbol with Audi rings, and increase the legibility of unreached gauge values.
- **Authority:** The three visual outcomes requested by the user are fixed; existing telemetry, map behavior, gauge geometry, click actions, header/footer dimensions, and asynchronous startup must remain unchanged.
- **Execution profile:** A bounded Compose UI change verified through the existing unit/lint/release pipeline and a 2400x896 emulator screenshot.
- **Stop condition:** Stop if achieving the full-width layer requires changing MapLibre camera/location behavior or if the Audi mark cannot remain inside the existing MMI button hit target.

## Product Contract

### Summary

The cockpit should treat the vector map as the continuous background of the entire central region. The opaque speed and RPM gauges remain foreground instruments. The header should communicate Audi identity at the MMI action, and inactive numeric scale values should remain highly legible.

### Problem Frame

The current map is constrained to 56 percent of the available width, so its visible surface ends between the gauges instead of continuing underneath them. The MMI action uses a generic car silhouette, and unreached speed/RPM labels use a muted gray that is less legible than requested.

### Requirements

- R1. The map fills the complete width and height of the central content region between the header and footer, inside the application's existing outer 5 dp inset.
- R2. The speed and RPM gauges render above the map without changing their size, centers, values, progression, gear position, or opaque circular treatment.
- R3. The MMI action displays four interlocking Audi rings instead of the generic car silhouette while preserving its button dimensions, click behavior, and accessibility meaning.
- R4. Numeric speed and RPM scale labels whose threshold has not been reached render as white at 0.9 opacity.
- R5. Reached scale labels remain cyan and bold; minor/major tick colors and all telemetry behavior remain unchanged.
- R6. The map remains asynchronous and a map/network/GPS failure cannot block the rest of the cockpit.
- R7. Current design and map documentation describe the resulting full-width vector-map composition and label colors.

### Scope Boundaries

- No changes to MapLibre styles, camera, zoom, marker, cache, GPS filtering, networking, or lifecycle.
- No changes to header/footer height, gauge placement, alert icons, telemetry values, or navigation intents.
- No change to tick-mark colors; R4 applies only to numeric scale labels.
- No new raster asset is required: the Audi rings should use the existing scalable Compose Canvas approach.

### Acceptance Examples

- AE1. At 2400x896, the map reaches both horizontal edges of the central content area and is visible around and between the two foreground gauges.
- AE2. At zero speed and RPM, every numeric scale label is white at 0.9 opacity; when replay advances, reached values turn cyan/bold while unreached values remain white.
- AE3. Pressing the Audi-rings button invokes the same MMI callback previously attached to the car icon.
- AE4. With no map coverage, the header, footer, gauges, alerts, and telemetry remain visible and responsive.

## Planning Contract

### Key Technical Decisions

- KTD1. **Use existing sibling z-order for the full-width map** `(session-settled: user-directed — chosen over retaining the 56% center strip: the map must occupy the full central width)`. `CockpitMap` remains the first child of the central `Box`; the gauge `Row` remains the later foreground child. Governs R1, R2, R6.
- KTD2. **Remove only the central 12 dp horizontal inset** `(session-settled: user-directed — chosen over keeping side gaps: “todo lo ancho” requires the map to reach the central area's edges)`. The application's outer 5 dp inset and bar boundaries remain intact. Governs R1.
- KTD3. **Draw four stroked rings in the existing MMI Canvas** `(session-settled: user-directed — chosen over the generic car silhouette: the control should carry Audi identity)`. This preserves optical scaling, hit target, tint, and callback without adding a raster dependency. Governs R3.
- KTD4. **Change only unreached numeric-label paint** `(session-settled: user-directed — chosen over muted titanium gray: white at 0.9 opacity provides the requested legibility)`. Reached labels and tick marks retain existing state styling. Governs R4, R5.

### Assumptions

- The launcher is a private, device-specific Audi installation, so use of the Audi rings is an accepted product-branding decision.
- The existing opaque `DialMapShield` and circular gauge backgrounds provide sufficient contrast over light OpenFreeMap styles.
- Visual smoke testing is the primary behavioral proof because the repository has no Compose screenshot-test infrastructure and the requested changes do not alter domain logic.

### Product Contract Preservation

Product Contract created from the user's request; no scope change introduced during planning.

## Implementation Units

### U1. Full-width map background

- **Goal:** Expand MapLibre across the central cockpit while retaining foreground gauge composition.
- **Requirements:** R1, R2, R6; AE1, AE4.
- **Dependencies:** None.
- **Files:** `app/src/main/java/com/lito/a5launcher/ui/components/DashboardScreen.kt`.
- **Approach:** Remove the fixed map-width calculation and central horizontal inset, give `CockpitMap` the full available central size, and preserve the current map-first/gauges-second sibling order.
- **Patterns to follow:** Existing `Box` z-order and `CockpitMap` modifier-driven sizing.
- **Test scenarios:**
  - Covers AE1. Render at 2400x896 and confirm the map reaches both horizontal bounds behind the opaque gauge discs.
  - Covers AE4. Launch before map tiles load and confirm the complete non-map cockpit renders independently.
  - Confirm map attribution and fixed location marker remain in the unobstructed center region.
- **Verification:** Emulator screenshot shows a continuous map with unchanged gauge geometry, and logcat contains no MapLibre/native crash.

### U2. Audi MMI icon and gauge-label contrast

- **Goal:** Apply the requested OEM identity and inactive-label appearance without changing behavior.
- **Requirements:** R3, R4, R5; AE2, AE3.
- **Dependencies:** None.
- **Files:** `app/src/main/java/com/lito/a5launcher/ui/components/DashboardScreen.kt`, `app/src/main/java/com/lito/a5launcher/ui/components/ProgressRingIndicator.kt`.
- **Approach:** Replace the car-body Canvas paths with four evenly spaced interlocking ring strokes inside the same button shell, and set only unreached numeric label paint to white with 0.9 opacity.
- **Patterns to follow:** Existing Canvas-based custom icon, shared active-color state in `ProgressRingIndicator`, and optical sizing supplied by `TopCommandBar`.
- **Test scenarios:**
  - Covers AE2. At zero, confirm all speed and RPM numbers use white at 0.9 opacity.
  - Covers AE2. During replay, confirm reached labels remain cyan/bold and unreached labels remain white; tick styling must not change.
  - Covers AE3. Tap the rings control and confirm the existing `onMmi` path is invoked.
  - Confirm four rings fit inside the button without clipping or inconsistent stroke weight.
- **Verification:** Visual comparison at 2400x896 confirms consistent rings and label contrast; compilation reports no Compose/Canvas errors.

### U3. Documentation and release validation

- **Goal:** Keep the current design contract aligned and produce an installable Navifly release.
- **Requirements:** R7 and all acceptance examples.
- **Dependencies:** U1, U2.
- **Files:** `docs/design/DESIGN.md`, `docs/feature-map/MAPS.md`, `out/A5Cockpit.apk`, `captura local full-width-vector-map-audi-rings-2400x896.png`.
- **Approach:** Replace obsolete 56-percent-map and titanium-inactive-label statements, run the canonical production pipeline, then capture and inspect the emulator at the physical device profile.
- **Execution note:** This is visual/configuration work; use release build gates and runtime smoke evidence rather than introducing a new screenshot-test framework.
- **Test scenarios:**
  - Run existing telemetry unit tests unchanged to prove UI work did not disturb production decoding.
  - Run release lint and R8 assembly.
  - Cold-launch with network/map loading delayed and inspect process stability.
- **Verification:** Documentation matches the screenshot, the ARM64 APK is written to `out/`, its checksum is recorded, and the process remains stable without fatal logs.

## Verification Contract

| Gate | Applies to | Done signal |
|---|---|---|
| `./gradlew testDebugUnitTest` | U1, U2 | Existing production-code tests pass |
| `./gradlew lintRelease` | U1, U2 | No release lint errors |
| `./scripts/compile.sh` | U3 | Optimized ARM64 APK generated and written to `out/` |
| 2400x896 emulator smoke test | U1, U2, U3 | Map loads full width, gauges remain foreground, Audi rings and 0.9-white labels render correctly |
| Runtime log inspection | U1, U3 | Stable PID and no fatal Android/MapLibre errors |

## Definition of Done

- R1-R7 and AE1-AE4 are visibly satisfied.
- Map, telemetry, navigation actions, gear display, alerts, header, and footer retain their previous behavior.
- Current documentation no longer describes the map as a 56-percent center strip or unreached labels as titanium gray.
- The production ARM64 APK and validation screenshot are generated as local, unversioned artifacts.
- Successful compilation is followed by a descriptive Git commit.
