import com.android.build.api.dsl.ApplicationExtension
import gawi.catalogVersion
import gawi.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                // App identity (namespace, applicationId, versionCode/Name)
                // belongs in the module's own build file.
                defaultConfig {
                    targetSdk = catalogVersion("targetSdk").toInt()
                }
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false
                    }
                }
                lint {
                    abortOnError = true
                }
            }
        }
    }
}
