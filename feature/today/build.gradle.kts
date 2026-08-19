plugins {
    id("gawi.android.library")
    id("gawi.compose")
    id("gawi.hilt")
}

android {
    namespace = "com.gawi.feature.today"
}

dependencies {
    // implementation, not api: no :core:data or compose type appears in this
    // module's own surface, since TodayRoute() takes and returns nothing.
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
