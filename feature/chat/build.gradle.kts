plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.app.pustakam.feature.chat"
        // 💬 35, matching every other module: a library must not compile against a HIGHER API than :androidApp
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

    // 💬 no framework block — only the :shared umbrella emits an iOS framework
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // 💬 every type below appears in ChatBridge's public signature, so it must be api()
                //   the whole way down or it vanishes from the generated Swift header
                api(projects.core.common)
                api(projects.core.model)
                api(projects.core.data)
                api(projects.core.database)
                api(projects.core.network)
                api(libs.koin)
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                // 💬 Ktor 2.3.12's Darwin engine has no websocket support, so the socket is
                //   expect/actual rather than shared Ktor: OkHttp here, NSURLSession on iOS.
                implementation(libs.okhttp)
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
