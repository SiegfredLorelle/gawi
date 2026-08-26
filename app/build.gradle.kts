plugins {
    id("gawi.android.application")
    id("gawi.compose")
    id("gawi.hilt")
    // Type-safe navigation routes are @Serializable classes, so the compiler
    // plugin is needed here the same way :core:domain needs it for wire DTOs.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gawi.app"
    defaultConfig {
        applicationId = "com.gawi.app"
        versionCode = 1
        versionName = "0.1.0"
        // The only module with an androidTest source set (architecture §8).
        // configureAndroid sets no runner, because until now nothing needed one.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:today"))
    // The widget module. :app names nothing from it — the receiver is reached
    // through the merged manifest and GlanceProjectionListener through Hilt —
    // but the dependency is what puts both in the app, and what closes the
    // ProjectionListener binding :core:data deliberately leaves open.
    implementation(project(":widget"))

    implementation(libs.androidx.activity.compose)
    // collectAsStateWithLifecycle, for the one flow :app itself collects: the
    // theme (docs/ux/settings.md §7). Every feature module already takes this
    // artifact for the same function; the app did not, because until the theme
    // it composed no state of its own.
    implementation(libs.androidx.lifecycle.runtime.compose)
    // WorkManager, for the reminder and rollover workers (docs/ux/reminder.md).
    //
    // NOT a new dependency in the app — Glance has required it since the widget
    // shipped, and architecture §7 records why it cannot be excluded. What is
    // new is that :app *compiles* against it. :widget takes Glance on
    // `implementation`, so work-runtime reaches this module's runtime classpath
    // and never its compile one; a CoroutineWorker here does not build without
    // this line.
    //
    // Declaring it also pins the version, which was Glance's 2.7.1 by default.
    // See the catalog comment: the risk a bump carries is the merged manifest,
    // not the build, and ManifestPermissionTest is what measures it.
    implementation(libs.androidx.work.runtime)
    // NotificationCompat and the channel. Declared rather than left to arrive
    // through hilt-android's fragment dependency, which is the same accident
    // ApplicationProvider and ActivityScenarioRule were.
    implementation(libs.androidx.core.ktx)
    // The navigation graph lives here and only here (architecture §2). No
    // feature module depends on navigation, so a screen cannot navigate itself.
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // The journey tests. Robolectric supplies the framework, Hilt supplies the
    // real graph, and the compose rule drives the real MainActivity — which is
    // the only way in, since every feature screen and ViewModel is internal to
    // its own module and hiltViewModel() needs an @AndroidEntryPoint host.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    // WorkManager does not initialise itself under Robolectric — its
    // androidx.startup provider does not run, so WorkManager.getInstance throws
    // IllegalStateException. Measured, not assumed. This supplies
    // WorkManagerTestInitHelper, which is the only way the reminder's scheduling
    // is assertable without a device.
    testImplementation(libs.androidx.work.testing)

    // Instrumented tests. Deliberately NOT Hilt-testing: these drive the real
    // installed app with the real graph and the real database, which is the
    // whole point — it is the one place where Room's invalidation actually
    // delivers, so a write journey can be asserted here and cannot be under
    // Robolectric (architecture §8).
    //
    // Not run by `make test` (`./gradlew test` is the unit-test umbrella), so
    // CI is untouched. `make itest` runs them on an attached device.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Overrides ui-test-junit4's transitive 3.5.0; see the catalog comment.
    androidTestImplementation(libs.androidx.test.espresso.core)
}
