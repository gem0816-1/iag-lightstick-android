# AM++ glass sources

This Android library vendors the rendering and animation code from the local
`AMPLUS/AM-plus-plus` checkout at commit `86762b5`.

- AM++: https://github.com/Zennmn/AM-plus-plus, GNU GPL version 3.
  The full license is packaged in `src/main/assets/licenses/AM-plus-plus-GPL-3.0.txt`.
- AndroidLiquidGlass / Backdrop 2.0.1 by Kyant:
  https://github.com/Kyant0/AndroidLiquidGlass, pinned upstream commit
  `65ab177e90e5c1d8c62e70cf7755841982da65f6`, Apache License 2.0.
  The full license is packaged in
  `src/main/assets/licenses/AndroidLiquidGlass-Apache-2.0.txt`.

The AM++ additions and changes include the navigation free-drag behavior,
thumb-centered press highlight, scroll-collapse geometry and rendering options,
native mini-player material, bottom scrim, backdrop allocation floor, clipping
strategy and CPU tracing. Existing source headers have been preserved.

IAG adaptations include the Android-only build, merged platform `expect`/`actual`
pairs, AndroidX Compose coordinates, ImageVector navigation content, cached
rendering resources and host configuration defaults. Later revisions add shared
motion roles, frame-coalesced drag input and cancellable press/release cleanup.
Do not assume all gesture or spring behavior is byte-identical to AM++.
`source-provenance.json` records the copied sources and adaptations.

The five JUnit test classes in `src/test/java/dev/amenhancer/glass` are copied
without modification from AM++: `GlassGeometryTest`, `GlassTabGeometryTest`,
`GlassPressGeometryTest`, `GlassPolicyTest` and `GlassBarCollapsePolicyTest`.
Their 41 tests exercise the existing policy and geometry, including unmeasured
layers, invalid numbers, RTL navigation, selection boundaries and collapse
interpolation/rebound. Their original file bytes and source paths are included
in the same provenance manifest. No host-injection or tablet-layout test
dependencies have been introduced.

The adapted `GlassNavigation` is included. Apple Music host hooks, version
checks, `GlassHostView` and Xposed dependencies are not included. The IAG
application supplies its content/actions and a live backdrop through public
Compose APIs. This module's license notices do not establish rights to the
application's separately sourced artwork or player icons. The publisher must
also satisfy the GPL corresponding-source obligations when distributing the app.

IAG v0.9.12 additionally caches BottomScrim's identical gradient brushes,
Paint and VibrantBlur prefix instead of recreating them while drawing. The
upstream shader, mask, blur radius and colors are unchanged; both the source
and adapted hashes and the adaptation are recorded in source-provenance.json.
