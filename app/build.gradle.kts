plugins {
    id("com.android.application")
    kotlin("plugin.compose") version "2.4.0"
}

android {
    namespace = "com.anuajay.splitup"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.anuajay.splitup"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // fixed key so every build installs over the previous one
    signingConfigs.getByName("debug") {
        storeFile = file("splitup-debug.jks")
        storePassword = "splitupdebug"
        keyAlias = "splitup"
        keyPassword = "splitupdebug"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { abortOnError = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.google.mlkit:text-recognition:16.0.1") // reads screenshots on the phone, no internet needed
}
