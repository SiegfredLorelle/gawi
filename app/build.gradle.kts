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
    implementation(project(":feature:today"))

    implementation(libs.androidx.activity.compose)
    // The navigation graph lives here and only here (architecture §2). No
    // feature module depends on navigation, so a screen cannot navigate itself.
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
}
