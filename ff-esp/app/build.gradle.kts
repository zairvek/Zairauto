plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.esp.ff"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.esp.ff"
        minSdk        = 28
        targetSdk     = 33
        versionCode   = 1
        versionName   = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false; isShrinkResources = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
