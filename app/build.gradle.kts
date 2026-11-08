// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

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

// A stable signing key lets every build update the one before it (Android refuses an update signed with a different
// key). CI provides the key from GitHub secrets; without it the build uses a throw-away debug key.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_FILE")
val keystorePassword: String? = System.getenv("SIGNING_PASSWORD")
// The version code must grow with every build, or Android treats a newer APK as a downgrade.
val ciRun: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
// The version people read. Nightly builds get a suffix from CI ("-nightly.45"), stable builds do not.
// Change baseVersion before you make a stable release.
val baseVersion = "1.2.0"
val versionSuffix: String = System.getenv("VERSION_SUFFIX") ?: ""

android {
    namespace = "dev.ax12.announcer"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.ax12.announcer"
        minSdk = 24
        targetSdk = 28          // the AX12 runs Android 9
        versionCode = if (ciRun != null) 1000 + ciRun else 16
        versionName = baseVersion + versionSuffix

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

    signingConfigs {
        if (keystorePath != null && keystorePassword != null && file(keystorePath).exists()) {
            create("stable") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "ax-hud"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// No external libraries on purpose: only the Android framework.
