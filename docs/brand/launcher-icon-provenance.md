# Launcher icon provenance

The current launcher and store icon uses an original three-node network mark on a white field. The small, centered mark uses one slate connector and three steel/slate nodes; it contains no shield, letterform, official NextDNS logo, NextDNS color reference, external image, or font.

The canonical geometry and generator are in `scripts/generate_launcher_assets.py`. Running that script produces `launcher-icon.svg`, the 108 dp adaptive foreground and monochrome vectors, legacy 48/72/96/144/192 px bitmap variants (including round variants), and the 512 px store icon from the same geometry. The application’s in-product network artwork remains separate.

The artwork is original project content and is distributed under the repository’s Apache-2.0 license. The white adaptive background is a solid color layer. Android’s OEM masks and themed-icon treatment still require final device review; this provenance record does not establish trademark clearance or approval by NextDNS. The product remains independent and unofficial.

Platform references: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [Google Play icon design specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
