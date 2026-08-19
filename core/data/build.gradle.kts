plugins {
    id("gawi.android.library")
    id("gawi.room")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.core.data"
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
}
