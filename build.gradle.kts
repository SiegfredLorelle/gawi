plugins {
    // Third-party plugins the convention plugins in build-logic apply by id.
    // `apply false` puts them on the build classpath without applying them
    // to the root project (build-logic declares them compileOnly).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false

    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// Formatting and static analysis live here at the root — one config site,
// whole-tree targets, build-logic included. Modules stay unaware of them.
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        // Spotless does not pass ktlint_* properties from .editorconfig to
        // ktlint, so this override must mirror the same setting there (kept
        // for IDE ktlint integrations). Change both together.
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
        )
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("config/detekt/detekt.yml"))
    // Derived from the project tree so a new module can never silently
    // escape analysis. build-logic is an included build, added by hand.
    source.setFrom(
        files(subprojects.map { it.projectDir.resolve("src") } + file("build-logic/src")),
    )
}
