plugins {
    id("gawi.android.library")
}

android {
    namespace = "com.gawi.core.data"
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
