plugins {
    id("gawi.android.library")
}

android {
    namespace = "com.gawi.core.testing"
}

// Test helpers shared by more than one module's tests (architecture §2, §10).
// The MAIN source set holds them and only test source sets consume it, so
// nothing here ships in the app. A helper lives here when a second module's
// tests need it; a second copy of one is the defect.
dependencies {
    // api, because the builders here put TodaySnapshot, HabitState,
    // HabitRepository and HabitPalette in their signatures, so a consumer
    // compiles against both modules through them.
    api(project(":core:data"))
    api(project(":core:ui"))
    // uuid(), habitId() and the event builders live in :core:domain's own test
    // fixtures because that module is pure JVM and cannot depend on this one.
    // Re-exported so one import path serves every Android module.
    api(testFixtures(project(":core:domain")))
    // TestWatcher and ExternalResource are the rules' supertypes.
    api(libs.junit)
    implementation(libs.kotlinx.coroutines.test)
    // compileOnly, deliberately. AnimationsOffRule names RuntimeEnvironment,
    // but :core:ui's tests are Robolectric-free by policy and must stay so
    // after taking Contrast from here; the modules that use the rule already
    // declare robolectric themselves.
    compileOnly(libs.robolectric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
