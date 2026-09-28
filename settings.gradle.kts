// Trade Like a Hedge Fund — native rebuild.
// Two native UIs (androidApp = Jetpack Compose, iosApp = SwiftUI Xcode project)
// sharing ONE backend (shared = Kotlin Multiplatform module).

// Plugin markers resolve from Google's Maven and Maven Central
// (the Gradle plugin portal is not used).
pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TradeLikeAHedgeFund"
include(":shared")
include(":androidApp")
