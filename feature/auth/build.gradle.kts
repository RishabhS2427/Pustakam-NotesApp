plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
}

kotlin {
    androidLibrary {
        namespace = "com.app.pustakam.feature.auth"
        // 🔧 30-Jul-2026 02:10 — 35 (was 36): a library must not compile against a HIGHER API than :androidApp (35)
        compileSdk = 35
        minSdk = 24

        withHostTestBuilder {
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // 🔧 30-Jul-2026 02:10 — framework block REMOVED (was baseName "authKit"): only the :shared umbrella emits an iOS framework
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // 🔧 30-Jul-2026 02:10 — every type below appears in a bridge's public signature, so it must be api() the whole way down or it vanishes from the generated Swift header
                api(projects.core.common)
                api(projects.core.model)
                api(projects.core.data)
                api(projects.core.database)
                // 👤 31-Aug-2026 profile: MediaUpload is in AuthBridge.uploadAvatar's signature. It
                //   resolved transitively through :core:data before; naming it is the module's own rule.
                api(projects.core.network)
                // 🔧 30-Jul-2026 02:10 — the bridges are KoinComponents -> supertype -> api
                api(libs.koin)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
            }
        }

        iosMain {
            dependencies {
            }
        }
    }
}
