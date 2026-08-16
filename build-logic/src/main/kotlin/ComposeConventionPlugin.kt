import com.android.build.api.dsl.CommonExtension
import gawi.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // Deferred so plugin declaration order in the module file cannot
            // break configuration: the "android" extension only exists once
            // an Android plugin (application or library) has been applied.
            pluginManager.withPlugin("com.android.base") {
                val android = extensions.getByName("android") as CommonExtension
                android.buildFeatures.compose = true
            }

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                "implementation"(platform(bom))
                "androidTestImplementation"(platform(bom))
                "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
