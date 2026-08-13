# Third-party notices

The MIT license in this repository applies to original A5 Launcher code. It
does not relicense third-party dependencies, services, map data, trademarks or
visual assets.

## Software dependencies

- AndroidX and Jetpack Compose components are provided by the Android Open
  Source Project under their respective Apache License 2.0 terms.
- MapLibre Native is provided by the MapLibre project under its published
  open-source license.
- OkHttp is provided by Square under Apache License 2.0.
- gRPC Python is used only by the local emulator replay tooling and is
  provided by the gRPC project under Apache License 2.0.
- Material Design Icons used by vehicle witnesses are provided by
  Pictogrammers/Templarian under Apache License 2.0.

The complete resolved dependency graph can be generated with
`./scripts/check-dependencies.sh`. Dependency distributions include their own license
metadata where required. The repository also includes the full reusable license
texts in [`licenses/`](licenses/): Apache License 2.0 for AndroidX, Compose,
OkHttp and the Material icon set, and the BSD 2-Clause license shipped by
MapLibre Native.

## Maps and services

- Map rendering uses MapLibre Native.
- Map styles may be loaded from OpenFreeMap and contain OpenMapTiles data.
- Map content includes © OpenStreetMap contributors and is subject to the
  Open Database License and applicable attribution requirements.
- Optional OpenAI, Google Gemini, Google Places and Waze integrations are
  governed by their providers' terms and require user-supplied credentials or
  applications.

## Vehicle and product names

Audi and the four rings, Volkswagen Group, Waze, Google, OpenAI,
ChoiceWay and Navifly are trademarks or names of their respective owners. Their
mention identifies compatibility or an integration and does not imply
affiliation, sponsorship or endorsement.

The project-specific visual resources and boot animation included in this
repository were created by the project owner. Third-party trademarks depicted
by those resources remain the property of their respective owners and their
presence does not imply affiliation or endorsement. See also
`docs/reference/THIRD_PARTY_ASSETS.md`.
