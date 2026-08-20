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
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:today"))

    implementation(libs.androidx.activity.compose)
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
}
