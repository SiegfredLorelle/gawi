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
    // Deliberately NO :core:ui. Its theme, GawiSpacing and shared composables
    // are androidx.compose.ui types, and a Glance tree cannot consume one — a
    // widget is RemoteViews under the composition, so it has its own Column,
    // its own GlanceModifier and its own GlanceTheme. "Reuse the theme" is the
    // first thing a reviewer asks here and the answer is that it does not
    // compile. A minimal widget (PRD OQ-5, docs/ux/widget.md §2) draws no habit
    // colour, so HabitPalette and parseHabitColor are not wanted either.
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
