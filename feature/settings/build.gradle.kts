plugins {
    id("gawi.android.library")
    id("gawi.compose")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.feature.settings"

    // The third-party licence texts, packaged as assets straight from the
    // repository's licenses/ directory rather than copied into src/. Assets
    // rather than res/raw because a resource name cannot carry uppercase
    // letters, and the drawable headers in :core:ui point at these files by
    // their upstream names (docs/ux/settings.md §9). The Licences screen reads
    // them back through AssetManager.
    //
    // Build configuration in a module file, which architecture.md §9 reserves
    // for build-logic. Recorded as the one exception in AGENTS.md and in
    // architecture.md §10's "where a new file goes" table, because a
    // convention plugin for a single module's single directory would be the
    // heavier way to say the same thing. The whole directory ships, with no
    // allow-list — the directory IS the list; anything put in licenses/ is a
    // notice this app owes, and LicenceNotice is where it then gets a name.
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("licenses"))
        }
    }
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

    // rememberLauncherForActivityResult, and nothing else from this artifact.
    // It is the Storage Access Framework seam: the picker belongs to the
    // system, so exporting and importing a file needs no permission, no
    // FileProvider and no <queries>, and the manifest's comment about having
    // none of those stays true.
    //
    // The second module to take it; :app had it alone. The alternative was
    // registering both contracts on MainActivity and threading two callbacks
    // down through the navigation graph, which would put this screen's file
    // handling in a module with no other reason to know about it.
    implementation(libs.androidx.activity.compose)
    // NotificationManagerCompat and ActivityCompat, for the reminder row's
    // "notifications are off" state. Declared rather than left to arrive through
    // hilt-android's fragment dependency: this module names both types directly,
    // and the reminder row is the whole reason it does.
    implementation(libs.androidx.core.ktx)
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
    // Shared test helpers: fixtures, the fake repository and the rules.
    testImplementation(project(":core:testing"))
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
