// 🔧 30-Jul-2026 02:10 — :shared is now an UMBRELLA: it owns only the Koin composition root and the
//   iOS KoinHelper. Its job is to re-export every module into ONE framework still called "shared",
//   so all 86 Swift files keep `import shared` unchanged.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    tasks.register("testClasses")
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
            linkerOpts.add("-lsqlite3")
            freeCompilerArgs += listOf("-Xmemory-model=experimental")

            // 🔧 30-Jul-2026 02:10 — export() puts each module's public API into shared.h for Swift.
            //   Omit one and its types silently disappear from Swift — the failure shows up in Xcode
            //   as "cannot find type 'Note' in scope", never as a Gradle error.
            //   export() ONLY works on an api() dependency (see commonMain below).
            export(projects.core.common)
            export(projects.core.model)
            export(projects.core.richtext)
            export(projects.core.database)
            export(projects.core.network)
            export(projects.core.data)
            export(projects.core.filesys)
            export(projects.feature.auth)
            export(projects.feature.notes)
            export(projects.feature.chat)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 🔧 30-Jul-2026 02:10 — api(), NOT implementation(): export() above requires an api dependency
            api(projects.core.common)
            api(projects.core.model)
            api(projects.core.richtext)
            api(projects.core.database)
            api(projects.core.network)
            api(projects.core.data)
            api(projects.core.filesys)
            api(projects.feature.auth)
            api(projects.feature.notes)
            api(projects.feature.chat)
            // 🔧 30-Jul-2026 02:10 — KoinAppDeclaration is in initKoin()'s signature, which Swift calls
            api(libs.koin)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            // 🔧 30-Jul-2026 02:10 — kept as api(): :androidApp depends on :shared alone and picks these up transitively
            api(libs.bundles.koinAndroid)
            api(libs.bundles.media3)
        }
        iosMain.dependencies {
        }
    }
}

android {
    namespace = "com.app.pustakam"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
