package gawi

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

/**
 * Settings shared by application and library modules. Kotlin's jvmTarget is
 * deliberately not set: AGP 9's built-in Kotlin defaults it from
 * targetCompatibility, and the `kotlin {}` extension is not touched here.
 */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = catalogVersion("compileSdk").toInt()
        defaultConfig.minSdk = catalogVersion("minSdk").toInt()
        val jdk = JavaVersion.toVersion(catalogVersion("jdk"))
        compileOptions.sourceCompatibility = jdk
        compileOptions.targetCompatibility = jdk
        // One lint policy for app and library modules. warningsAsErrors is
        // the actual hard gate (abortOnError is AGP's default, kept
        // explicit). GradleDependency is noise here: Dependabot owns update
        // nudges, and a third-party release must not redden CI.
        lint.abortOnError = true
        lint.warningsAsErrors = true
        lint.disable += "GradleDependency"
        // Same reasoning as GradleDependency, and a harder case: this one fires
        // on gradle/wrapper/, which is generated and must not be hand-edited, so
        // a Gradle release upstream would redden CI with nothing a change here
        // could legitimately do about it.
        lint.disable += "AndroidGradlePluginVersion"
        // Robolectric resolves the merged manifest and resources off the unit
        // test classpath; without this it cannot find them and every test in a
        // module that uses it fails at startup (architecture §8 puts :core:data
        // DAO tests on the JVM).
        testOptions.unitTests.isIncludeAndroidResources = true
        // Without this, every android.jar method a unit test reaches throws
        // "Stub!" instead of doing nothing — including android.util.Log. That
        // turns a log line inside a catch block into a second exception, which
        // is exactly the failure a catch block exists to prevent. Robolectric
        // modules are unaffected; they swap the jar out entirely.
        //
        // The cost, since it applies to every module: an android.jar call in a
        // non-Robolectric test now returns a quiet default rather than failing
        // loudly, so TextUtils.isEmpty("x") reads false and Uri.parse returns
        // null instead of announcing that the framework is a stub. Architecture
        // §8 puts the correctness weight on JVM tests, so a module that starts
        // leaning on framework behaviour wants Robolectric rather than this.
        testOptions.unitTests.isReturnDefaultValues = true
    }
}
