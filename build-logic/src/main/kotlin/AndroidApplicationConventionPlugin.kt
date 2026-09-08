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

                // Read as providers rather than System.getenv, because
                // gradle.properties turns the configuration cache on: a
                // provider is recorded as a build input and a raw read is not,
                // so a cached configuration would keep whichever values the
                // first invocation happened to see.
                val storePath = providers.environmentVariable(ENV_KEYSTORE_PATH)
                val storeSecret = providers.environmentVariable(ENV_KEYSTORE_PASSWORD)
                val alias = providers.environmentVariable(ENV_KEY_ALIAS)
                val keySecret = providers.environmentVariable(ENV_KEY_PASSWORD)

                // Absent unless the path is set, which is the state CI builds
                // in — it holds no key, and `make lint` still has to reach
                // :app:assembleDebug. Absent leaves `release` unsigned instead
                // of failing configuration, which is deliberate but means
                // nothing here can catch a half-set environment: a config that
                // is present and incomplete fails inside `packageRelease`,
                // after R8 has run. `make release` checks all four, and that
                // the path names a file, before it starts anything.
                val releaseSigning = storePath.orNull?.let { path ->
                    signingConfigs.create(RELEASE) {
                        // rootProject and not the module: `file()` here would
                        // resolve against :app, while `make release` tests the
                        // same path from the repository root, so a relative one
                        // would name two different files and pass the guard
                        // before failing the build.
                        storeFile = rootProject.file(path)
                        storePassword = storeSecret.orNull
                        keyAlias = alias.orNull
                        keyPassword = keySecret.orNull
                    }
                }

                buildTypes {
                    getByName(RELEASE) {
                        signingConfig = releaseSigning
                        isMinifyEnabled = true
                        isShrinkResources = true
                        // app/proguard-rules.pro holds the keep rules, each
                        // naming the failure that earned it. The optimising
                        // default is AGP's, and R8 reads both.
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
            }
        }
    }
}

/**
 * The signing and shrinking inputs, named in `.env.example` and exported by the
 * shell before `make release`. A PKCS12 keystore — `keytool`'s default — cannot
 * hold a key password that differs from the store's, so those two carry the
 * same value unless the keystore is a JKS one.
 */
private const val ENV_KEYSTORE_PATH = "GAWI_KEYSTORE_PATH"
private const val ENV_KEYSTORE_PASSWORD = "GAWI_KEYSTORE_PASSWORD"
private const val ENV_KEY_ALIAS = "GAWI_KEY_ALIAS"
private const val ENV_KEY_PASSWORD = "GAWI_KEY_PASSWORD"

/**
 * The build type and the signing config share this name and are reached
 * differently, which is why one call above creates and the other looks up: AGP
 * pre-creates the `release` build type, and pre-creates a signing config only
 * for `debug`. `:app:signingReport` shows it — `Variant: release` reports
 * `Config: none` until the variables below are set. Normalising the lookup into
 * a `create` breaks configuration.
 */
private const val RELEASE = "release"
