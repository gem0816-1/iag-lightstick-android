# AM++ glass for IAG

This module contains the local AM++ rendering sources. It replaces the binary
`io.github.kyant0:backdrop:2.0.1` dependency; do not load both, because they define
the same `com.kyant.backdrop` classes.

Root project wiring:

```kotlin
// Root build.gradle.kts plugins, beside the existing application plugin:
id("com.android.library") version "9.3.2" apply false
// settings.gradle.kts:
include(":ampp-glass")
// app/build.gradle.kts dependencies, replacing the Backdrop binary:
implementation(project(":ampp-glass"))
```

The component namespaces match AM++:

- `com.kyant.backdrop.catalog.components.LiquidBottomTabs`, `LiquidBottomTab`
  and `LiquidButton` use the AM++ animations and gesture behavior.
- `dev.amenhancer.glass.NativeLiquidButton` draws the mini-player glass and
  reports its transform to application-owned content. `NativeButtonInput.event`
  accepts Android `MotionEvent` actions and local pixel coordinates; it only
  observes press feedback and does not dispatch the application's click action.
- `dev.amenhancer.glass.BottomScrim` draws the lower blurred fade.
- `dev.amenhancer.glass.GlassPolicy` supplies AM++ dimensions: navigation 56dp,
  mini player 43dp, horizontal 16dp, stack gap 8dp, bottom 16dp, blur 4dp.

Pass an actual content `LayerBackdrop` recorded outside the consumer hierarchy.
For navigation specify `panelHeight = 56.dp` explicitly: the lower-level
`LiquidBottomTabs` catalog default remains 64dp, while the AM++ host wrapper
supplies 56dp. The mini material itself fills its bounds; the application sets
43dp on its layout. Refer to `THIRD_PARTY_NOTICES.md` for source provenance.

The public snapshot includes 61 JVM tests across seven test classes: the
AM++ policy/geometry tests and IAG scroll/interaction regressions. Run
`./gradlew.bat :ampp-glass:testDebugUnitTest` from `app-android` to
check policy and geometry, including zero-width layers, invalid animation
samples, RTL tab boundaries, collapse rebound, interaction phases and settle
cleanup. These tests do not render or
visually verify the glass.
