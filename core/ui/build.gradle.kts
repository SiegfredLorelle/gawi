plugins {
    id("gawi.android.library")
    id("gawi.compose")
}

android {
    namespace = "com.gawi.core.ui"
}

dependencies {
    // The BOM again, as api this time. gawi.compose puts it on implementation,
    // which reaches runtimeElements but not apiElements, so the versionless
    // material3 below would leave a consumer's compile classpath unconstrained.
    api(platform(libs.androidx.compose.bom))
    // Nobody can use GawiTheme without calling material3 inside it, and the
    // next shared composable puts a ColorScheme in a signature. Same reason
    // :core:data exports Flow rather than hiding it.
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)

    // isSystemInDarkTheme only; nothing here puts a foundation type in a
    // signature.
    implementation(libs.androidx.compose.foundation)

    // Plain JVM, no Robolectric: the colour rules here are arithmetic over a
    // string, which is why parseHabitColor is hand-rolled instead of calling
    // android.graphics.Color and dragging a framework into this module's tests.
    testImplementation(libs.junit)
}
