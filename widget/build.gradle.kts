plugins {
    id("gawi.android.library")
    id("gawi.compose")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.widget"
}

dependencies {
    // implementation, not api: nothing outside this module compiles against a
    // type from here. The receiver is reached through the manifest and the
    // ProjectionListener binding through Hilt, so :app names neither.
    implementation(project(":core:data"))
    // Declared rather than left to :core:data's api. HabitId and Schedule are
    // named here — the same rule feature/settings/build.gradle.kts states in
    // reverse, where not calling anything from :core:domain is why it is absent.
    implementation(project(":core:domain"))

    // Glance, not Compose UI. gawi.compose is applied for the compose compiler
    // plugin that Glance's @Composable tree needs; the compose BOM it also
    // brings governs nothing here, because Glance is not in it.
    //
    // :core:ui for FOUR things, and the list is the whole rule:
    //
    //  1. R.font.outfit, which BitmapText rasterises because a host drops a
    //     bundled font resource (visual-identity §2).
    //  2. Momo's geometry — drawMomo, MomoFrame and MomoDesignSize — which
    //     MomoBitmap rasterises at the resting frame because a host cannot run
    //     the animation (momo.md §4).
    //  3. GawiRole and gawiRole, which WidgetPalette derives its day/night
    //     ColorProviders from. Added 2026-08-29 with the streak widget, which
    //     needs `tertiary` as well as `primary` and so would have added two
    //     more untethered hexes to the three visual-identity §7.4 already
    //     named as this set's debt. Deriving removes the copy instead of
    //     guarding it; the accessor's KDoc carries the argument.
    //  4. StreakUi and StreakSnapshot.toUi, which the streak widget maps its
    //     rows through. Added 2026-08-29, same commit as item 3, and legal for a
    //     different reason: StreakUi is a sealed interface over Int and Boolean
    //     with no Compose type anywhere in it, so the barrier here was policy
    //     rather than the compiler. Copied instead, the widget would reinvent
    //     "days versus weeks versus broken" - and StreakUi.kt's own KDoc says
    //     that rule has to be one rule or the two surfaces will drift, which is
    //     exactly what this module did to the palette for two phases.
    //
    // Nothing else may cross this edge. Note what items 3 and 4 are NOT: the theme,
    // GawiSpacing and the shared composables are androidx.compose.ui types, and
    // a Glance tree cannot consume one — a widget is RemoteViews under the
    // composition, so it has its own Column and its own GlanceModifier. "Reuse
    // the theme" is the first thing a reviewer asks here and the answer is
    // still that it does not compile. A plain Color is not a theme, and a sealed
    // interface over Int is not a composable, which is the distinction that
    // makes items 3 and 4 legal and GawiTheme not. :core:ui
    // exposes compose and material3 as `api`, so the compiler will not stop a
    // fifth import; treat any other com.gawi.core.ui.* import in this module
    // as a defect.
    //
    // The 2026-08-28 test-source carve-out for gawiWindowBackground is gone
    // with the hexes it guarded: WidgetPaletteTest no longer needs to pin a
    // copy that no longer exists.
    //
    // A minimal widget (PRD OQ-5, docs/ux/widget.md §2) draws no habit colour,
    // so HabitPalette and parseHabitColor are not wanted either.
    implementation(project(":core:ui"))
    // Glance brings WorkManager with it, and it is NOT optional: androidx.glance
    // runs its composition session in SessionWorker, a CoroutineWorker, which
    // GlanceAppWidget's constructor reaches through SessionManagerImpl. Excluding
    // androidx.work compiles cleanly and then dies at runtime with
    // NoClassDefFoundError on androidx/work/CoroutineWorker the first time a
    // widget object is constructed. Measured on a device, and every Glance
    // version through 1.3.0-alpha02 declares the same dependency.
    //
    // The consequence is a permission decision, not a build detail — see
    // docs/ux/widget.md §5. WorkManager contributes WAKE_LOCK,
    // RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE and ACCESS_NETWORK_STATE to the
    // merged manifest.
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
    // Shared test helpers: fixtures, the fake repository and the rules.
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    // Glance's own unit-test harness, plus the framework it needs. This is the
    // first test here that renders rather than deciding: everything else asserts
    // on `body()`, which is why a widget drawing black text on a dark background
    // stayed green for a whole phase.
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.robolectric)
}
