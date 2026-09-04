plugins {
    id("gawi.android.library")
    id("gawi.compose")
}

android {
    namespace = "com.gawi.core.ui"

    // The WCAG contrast helper, published so `:widget`'s tests and
    // `:core:testing` can measure with the same formula. Fixtures rather than a
    // file in `:core:testing`, because the colours it measures are this
    // module's — putting it there made every consumer of the shared helpers
    // pull `:core:data` and Room's code generation into a test task graph that
    // only wanted arithmetic over a Color (architecture §2).
    testFixtures { enable = true }
}

dependencies {
    // api, because StreakUi's mapper takes a StreakSnapshot and a Schedule in
    // its signature, so a consumer calling it compiles against both. This is
    // the module graph's newest edge (architecture §2): :core:ui may know the
    // domain, which is pure Kotlin and cannot import Android, so nothing about
    // the non-negotiable direction changes.
    api(project(":core:domain"))

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
    // The test set sees `src/testFixtures` without declaring anything, so the
    // contrast helper costs it no dependency at all.
    testImplementation(libs.junit)
    testFixturesImplementation(libs.junit)
}
