plugins {
    id("gawi.android.application")
    id("gawi.compose")
    id("gawi.hilt")
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
    implementation(project(":feature:today"))

    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}
