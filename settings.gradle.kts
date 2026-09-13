enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Pustakam"
include(":androidApp")
include(":shared")
// 🔧 29-Jul-2026 01:52 — :core is a container dir (no build.gradle.kts), real modules live under it
include(":core:common")
include(":core:filesys")
include(":core:database")
include(":core:model")
include(":core:richtext")
include(":core:data")
include(":core:network")
include(":feature:auth")
include(":feature:notes")
include(":feature:chat")

