plugins {
    id("gawi.android.library")
    id("gawi.compose")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.feature.settings"
}

dependencies {
    // implementation, not api: SettingsRoute puts neither a :core:data nor a
    // compose type in its signature, so nothing a consumer compiles against
    // comes from these.
    implementation(project(":core:data"))
    // No :core:domain. Unlike the other two feature modules this one calls
    // nothing from it — UserSettings and SettingsSource are :core:data types
    // and the rest is java.time. It would resolve anyway, since :core:data
    // exports it, which is exactly why declaring it would say something untrue.
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.runtime.compose)
    // hiltViewModel() rather than viewModel(): this is a back-stack
    // destination, so the store owner is the entry rather than the activity.
    //
    // Deliberately the lifecycle artifact and not hilt-navigation-compose,
    // whose pom drags navigation in. :app owns the graph; the Route here takes
    // one plain lambda, so nothing in this module knows what a NavController
    // is and no test here needs one.
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
