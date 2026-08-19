plugins {
    id("gawi.android.library")
    id("gawi.compose")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.feature.today"
}

dependencies {
    // implementation, not api: no :core:data or compose type appears in this
    // module's own surface, since TodayRoute() takes and returns nothing.
    implementation(project(":core:data"))
    // Declared even though :core:data already exports it. Mascot, Mood,
    // Schedule, HabitId, StreakSnapshot and CommandResult are all called here
    // directly rather than reached through the read model — the same rule the
    // compose entries in the catalog are declared under.
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Compose rendered on the JVM. Robolectric supplies the framework the
    // composition needs — a real Looper, resources and a window — so these
    // tests run under `make test` beside the mapper's, rather than becoming
    // the androidTest source set architecture §8 keeps off CI.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    // debugImplementation, not testImplementation: this artifact is only an
    // AndroidManifest declaring the activity the rule launches, and the unit
    // test manifest is merged from the tested variant's dependencies rather
    // than from the test configuration. Safe on a variant configuration because
    // AGP 9 gives a library debug unit tests only — there is no
    // testReleaseUnitTest task to leave without a manifest.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
