plugins {
    id("gawi.android.library")
    id("gawi.compose")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.feature.insights"
}

dependencies {
    // implementation, not api: HistoryRoute puts neither a :core:data nor a
    // compose type in its signature, so nothing a consumer compiles against
    // comes from these.
    implementation(project(":core:data"))
    // Declared even though :core:data already exports it. HabitId is named here
    // directly — the ViewModel validates the route's raw string into one — which
    // is the rule the other feature modules declare it under.
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    // hiltViewModel() rather than viewModel(): this is a back-stack destination,
    // so the store owner is the entry rather than the activity, and the screen
    // needs hiltViewModel's creationCallback to be handed the habit it is
    // showing.
    //
    // Deliberately the lifecycle artifact and not hilt-navigation-compose, whose
    // pom drags navigation in. :app owns the graph; the Route here takes a
    // String id and one plain lambda, so nothing in this module knows what a
    // NavController is and no test here needs one.
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
    // into the unit test one. That would put an exported activity in every debug
    // install, reachable by any app that knows its name. Scoping it to the test
    // configuration keeps it where the compose rule needs it and out of the app.
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
