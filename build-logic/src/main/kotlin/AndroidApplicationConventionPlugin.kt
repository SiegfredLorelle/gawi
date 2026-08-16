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
                namespace = "com.gawi.app"
                defaultConfig {
                    applicationId = "com.gawi.app"
                    targetSdk = catalogVersion("targetSdk").toInt()
                    versionCode = 1
                    versionName = "0.1.0"
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
