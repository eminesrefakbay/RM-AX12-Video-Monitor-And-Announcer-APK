// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

// JNI glue between Kotlin (dev.ax12.announcer.AxSdkJni) and bridge_core.
#include <jni.h>

#include <android/log.h>

#include <string>
#include <vector>

#include "bridge_core.h"
#include "rmAxSdkApi.h"

#ifdef AX12_SERIAL_LOCK_PATCH
namespace ax12 { void patchSerialLock(); }
#endif

#define LOG_TAG "ax12bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace QtAndroidPrivate {
// Exported by libQt6Core. The SDK creates a QCoreApplication, which needs the JavaVM cached here.
// The result can be ignored: it fails later on missing Qt Android classes the SDK does not use.
extern bool initJNI(JavaVM* vm, JNIEnv* env);
}

// Timeline lines go to logcat under their own tag: adb logcat -s ax12timing
static void logTiming(const char* line) {
    __android_log_print(ANDROID_LOG_INFO, "ax12timing", "%s", line);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    const bool ok = QtAndroidPrivate::initJNI(vm, env);
    LOGI("QtAndroidPrivate::initJNI -> %s", ok ? "ok" : "failed (ignored)");
    ax12::setLogHook(&logTiming);
#ifdef AX12_SERIAL_LOCK_PATCH
    ax12::patchSerialLock();
#endif
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_ax12_announcer_AxSdkJni_nativeVersion(JNIEnv* env, jclass) {
    const std::string v(rmaxSdk::version());
    return env->NewStringUTF(v.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_ax12_announcer_AxSdkJni_nativeStart(JNIEnv* env, jclass, jstring appDataPath, jboolean requestDeviceInfo, jint configRetryMs) {
    const char* chars = env->GetStringUTFChars(appDataPath, nullptr);
    const std::string path(chars != nullptr ? chars : "");
    if (chars != nullptr) env->ReleaseStringUTFChars(appDataPath, chars);
    ax12::StartOptions options;
    options.requestDeviceInfo = requestDeviceInfo == JNI_TRUE;
    options.configRetryMs = configRetryMs;
    const int result = ax12::start(path, options);
    LOGI("nativeStart: path=%s deviceInfoFirst=%d retryMs=%d result=%d (0 ok, 1 already running, 2 thread failed, 3 communication failed)",
         path.c_str(), options.requestDeviceInfo ? 1 : 0, options.configRetryMs, result);
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_ax12_announcer_AxSdkJni_nativeTimeline(JNIEnv* env, jclass) {
    const ax12::Timeline t = ax12::timeline();
    jdoubleArray out = env->NewDoubleArray(ax12::kTlSize);
    if (out != nullptr) env->SetDoubleArrayRegion(out, 0, ax12::kTlSize, t.t);
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_ax12_announcer_AxSdkJni_nativeStop(JNIEnv*, jclass) {
    ax12::stop();
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_ax12_announcer_AxSdkJni_nativeSnapshot(JNIEnv* env, jclass) {
    const ax12::Snapshot s = ax12::snapshot();
    jdoubleArray out = env->NewDoubleArray(ax12::kSnapSize);
    if (out != nullptr) env->SetDoubleArrayRegion(out, 0, ax12::kSnapSize, s.v);
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_ax12_announcer_AxSdkJni_nativePopCrsf(JNIEnv* env, jclass) {
    std::vector<std::uint8_t> frame;
    if (!ax12::popCrsf(frame)) return nullptr;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(frame.size()));
    if (out != nullptr) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(frame.size()),
                                reinterpret_cast<const jbyte*>(frame.data()));
    }
    return out;
}
