plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
}

kotlin {
    androidLibrary {
        namespace = "com.app.pustakam.core.media"
        compileSdk = 35
        minSdk = 24

        withHostTestBuilder {
        }
    }

    // no binaries.framework here — only :shared emits the iOS framework
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // MediaDownload* appear in the bridge signatures, so these must be api()
                api(projects.core.common)
                api(projects.core.model)
                api(projects.core.network)
                api(projects.core.filesys)
                api(libs.koin)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        iosMain {
            dependencies {
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                // 📥 runTest + a virtual clock, so pause/resume is tested without real time
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
