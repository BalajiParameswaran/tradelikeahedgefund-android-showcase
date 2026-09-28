// Android UI: Jetpack Compose (Material3), package com.tradelikeahedgefund.app.
// Consumes the shared KMP backend via project(":shared").

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "com.tradelikeahedgefund.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tradelikeahedgefund.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "5.0.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))

    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Firebase Auth. NOTE: the google-services Gradle plugin is intentionally
    // NOT applied so the app compiles without google-services.json.
    // To enable Firebase: add your google-services.json under app/src,
    // apply the plugin, and follow README.md. Until then AccountScreen shows
    // an honest "not configured" state instead of crashing.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
}
