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
        versionCode = 24
        versionName = "5.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // On-device AI: MediaPipe LLM Inference (reads the downloaded .task model).
    // Same artifact + version the previous hybrid app used (0.10.35).
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
    // EncryptedSharedPreferences for the AI chat store (no plaintext chat logs).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Testing Framework
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
