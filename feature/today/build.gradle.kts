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
}
