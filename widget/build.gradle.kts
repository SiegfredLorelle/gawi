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
    // :core:ui for TWO things, both rasterised here because RemoteViews can
    // carry neither: R.font.outfit, which BitmapText draws because a host drops
    // a bundled font resource (visual-identity §2), and Momo's geometry —
    // drawMomo, MomoFrame and MomoDesignSize — which MomoBitmap draws at the
    // resting frame because a host cannot run the animation (momo.md §4).
    // Nothing else may cross this edge. The theme, GawiSpacing and the shared
    // composables are androidx.compose.ui types, and a Glance tree cannot
    // consume one — a widget is RemoteViews under the composition, so it has
    // its own Column, its own GlanceModifier and its own GlanceTheme. "Reuse
    // the theme" is the first thing a reviewer asks here and the answer is that
    // it does not compile. :core:ui exposes compose and material3 as `api`, so
    // the compiler will not stop a third import; treat any other
    // com.gawi.core.ui.* import in this module as a defect.
    //
    // One carve-out, added 2026-08-28 so the rule and the code do not drift
    // apart: WidgetPaletteTest imports gawiWindowBackground, in the TEST source
    // set only. WidgetPalette hand-copies the app's hexes because a Glance tree
    // cannot consume the scheme, and :core:ui publishes that one accessor
    // precisely so a module reproducing the surface can be pinned to it — the
    // same guard :app's XML copy has in WindowBackgroundTest. The production
    // edge is still the two things above, and a third import in `main` is still
    // a defect.
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
    testImplementation(libs.kotlinx.coroutines.test)
    // Glance's own unit-test harness, plus the framework it needs. This is the
    // first test here that renders rather than deciding: everything else asserts
    // on `body()`, which is why a widget drawing black text on a dark background
    // stayed green for a whole phase.
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.robolectric)
}
