// Shared backend: Kotlin Multiplatform. ONE codebase compiled into both apps.
// Targets: android (used by androidApp), jvm (unit tests), iosArm64 +
// iosSimulatorArm64 (compiled on macOS via `:shared:assembleXCFramework`).

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// Minimal Android config: required because androidTarget() is declared.
// The shared module ships no Android resources — just the KMP classes.
android {
    namespace = "com.tlhf.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm()
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                // CIO engine is multiplatform: works on JVM, Android and iOS,
                // so no expect/actual engine wiring is needed.
                implementation("io.ktor:ktor-client-core:2.3.13")
                implementation("io.ktor:ktor-client-cio:2.3.13")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // Shared iOS source set (both iOS targets). Compiled on macOS only.
        val iosMain by creating {
            dependsOn(getByName("commonMain"))
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
    }
}
