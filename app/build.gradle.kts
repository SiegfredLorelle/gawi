plugins {
    id("gawi.android.application")
    id("gawi.compose")
    id("gawi.hilt")
}

dependencies {
    implementation(project(":core:data"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
