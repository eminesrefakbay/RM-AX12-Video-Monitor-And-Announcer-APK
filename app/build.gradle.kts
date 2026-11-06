plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.ax12.announcer"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.ax12.announcer"
        minSdk = 24
        targetSdk = 28          // the AX12 runs Android 9
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// No external libraries on purpose: only the Android framework.
