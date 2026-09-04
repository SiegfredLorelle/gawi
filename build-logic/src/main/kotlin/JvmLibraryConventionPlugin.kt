import gawi.catalogVersion
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")
            // A `src/testFixtures` source set, published to consumers as
            // `testFixtures(project(...))`. A pure-JVM module cannot depend on
            // an Android library, so :core:domain's test builders cannot live
            // in :core:testing; this is how they reach it and every other
            // module from one file. Empty for any JVM module that has none.
            pluginManager.apply("java-test-fixtures")
            val jdk = catalogVersion("jdk")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.toVersion(jdk)
                targetCompatibility = JavaVersion.toVersion(jdk)
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(jdk))
                }
            }
        }
    }
}
