plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The RadioMaster SDK files are not stored in this repository (their licence is not stated).
// When they are present (the CI workflow copies them in, see README) the native bridge is built;
// without them the app still builds and runs on the test sliders.
val sdkLib = file("src/main/jniLibs/arm64-v8a/librmAxSdk1.so")
val withSdk = sdkLib.exists()
// -PaxLockPatch=true turns on the optional serial-lock patch (see serial_lock_patch.cpp)
val lockPatch = (project.findProperty("axLockPatch") ?: "false") == "true"

android {
    namespace = "dev.ax12.announcer"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.ax12.announcer"
        minSdk = 24
        targetSdk = 28          // the AX12 runs Android 9
        versionCode = 15
        versionName = "0.6.4"

        if (withSdk) {
            ndk { abiFilters += listOf("arm64-v8a") }       // the SDK ships arm64-v8a only
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++20"
                    // same C++ runtime as the SDK and Qt, so std::string can cross the boundary safely
                    arguments += "-DANDROID_STL=c++_shared"
                    if (lockPatch) arguments += "-DAX12_SERIAL_LOCK_PATCH=ON"
                }
            }
        }
    }

    if (withSdk) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }   // the SDK needs all its .so files extracted at install
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
