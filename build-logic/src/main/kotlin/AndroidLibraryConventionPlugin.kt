import com.android.build.api.dsl.LibraryExtension
import gawi.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Everything an Android library module shares, which is only [configureAndroid].
 *
 * `namespace` is deliberately **not** set here, even though AGENTS.md gives
 * convention plugins the build configuration. It is module *identity*, not
 * configuration: AGP derives the generated `R` class package from it, so it has
 * to differ per module, and deriving it from the Gradle path would make the
 * package of a module's own `R` a function of where the directory happens to
 * sit. All six modules declare their own, and
 * [AndroidApplicationConventionPlugin] says the same thing about `:app`'s.
 *
 * Recorded here because a reader of this file cannot see that comment, and the
 * omission has been read as an oversight more than once.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(this)
            }
        }
    }
}
