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
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            // Composables are PascalCase by Compose convention.
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
    source.setFrom(
        files(
            "app/src",
            "core/domain/src",
            "core/data/src",
            "build-logic/src",
        ),
    )
}
