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

    implementation(libs.androidx.lifecycle.runtime.compose)
    // hiltViewModel() rather than viewModel(). Both resolved the same factory
    // while this was the only screen and the store owner was the activity; now
    // that it is a back-stack destination the owner is the entry, and scoping
    // the ViewModel to it is what stops it outliving the screen.
    //
    // The lifecycle artifact, not hilt-navigation-compose, whose pom would put
    // navigation on a feature module's classpath. :app owns the graph.
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Compose rendered on the JVM. Robolectric supplies the framework the
    // composition needs — a real Looper, resources and a window — so these
    // tests run under `make test` beside the mapper's, rather than becoming
    // the androidTest source set architecture §8 keeps off CI.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    // testImplementation, deliberately not debugImplementation. This artifact is
    // only an AndroidManifest, declaring the ComponentActivity the rule launches
    // — but that activity is android:exported="true", and on a variant
    // configuration it merges into :app's packaged debug manifest as well as
    // into the unit test one. That would put a second exported activity in every
    // debug install, next to the one app/src/debug guards with DUMP for reasons
    // it spells out at length. Scoping it to the test configuration keeps it
    // where the compose rule needs it and out of the app.
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
