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
    // Flow is in HabitRepository's own signature, so every consumer needs it
    // on the compile classpath — same reason :core:domain is api here.
    api(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
}
