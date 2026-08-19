import androidx.room.gradle.RoomExtension
import gawi.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room for a module that persists. Applies the Room Gradle plugin rather than
 * passing `room.schemaLocation` as a raw KSP argument: the raw argument is an
 * opaque string that wires no task inputs or outputs, and this build runs with
 * the configuration cache enabled and persisted in CI.
 *
 * Schemas are exported and committed. The events table is append-only and is
 * never migrated in place (architecture §3), so a checked-in schema JSON is
 * the one mechanical guard that turns an accidental destructive migration into
 * a diff somebody has to review.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "implementation"(libs.findLibrary("androidx-room-runtime").get())
                "implementation"(libs.findLibrary("androidx-room-ktx").get())
                "ksp"(libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}
