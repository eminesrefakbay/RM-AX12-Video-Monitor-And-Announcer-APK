// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

// OPTIONAL, OFF BY DEFAULT (build with -PaxLockPatch=true).
//
// Why this exists: RadioMaster's own demo app needs it. Qt's QSerialPort refuses to open the port
// when it cannot create a lock file, and an Android app has no writable lock directory, so the SDK
// cannot open the radio's serial link. The demo works around this by turning two branch
// instructions in the bundled libQt6SerialPort_arm64-v8a.so into NOPs at run time.
//
// This is a runtime patch of third-party machine code at fixed offsets, valid only for the exact
// library shipped in the SDK. Before changing anything it checks that BOTH instructions are exactly
// what is expected and does nothing at all otherwise. Ask RadioMaster for an official way to open
// the port, and drop this file when they provide one.
#include <android/log.h>

#include <cerrno>
#include <cstdint>
#include <cstring>
#include <dlfcn.h>
#include <link.h>
#include <sys/mman.h>

#define LOG_TAG "ax12bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace ax12 {

void patchSerialLock() {
    if (dlopen("libQt6SerialPort_arm64-v8a.so", RTLD_NOW | RTLD_GLOBAL) == nullptr) {
        LOGE("serial patch: cannot load the Qt serial library: %s", dlerror());
        return;
    }
    std::uintptr_t base = 0;
    dl_iterate_phdr(
        [](struct dl_phdr_info* info, std::size_t, void* data) -> int {
            if (info->dlpi_name != nullptr && std::strstr(info->dlpi_name, "libQt6SerialPort") != nullptr) {
                *static_cast<std::uintptr_t*>(data) = static_cast<std::uintptr_t>(info->dlpi_addr);
                return 1;
            }
            return 0;
        },
        &base);
    if (base == 0) {
        LOGE("serial patch: library base address not found");
        return;
    }

    struct Site { std::size_t offset; std::uint32_t expect; };
    static constexpr Site kSites[] = {
        {0x15628, 0xb4000b88u},   // cbz  : lock directory empty
        {0x15648, 0x360005a0u},   // tbz  : lock file could not be created
    };
    constexpr std::uint32_t kNop = 0xd503201Fu;

    // Check every instruction first, so the library is never left half patched.
    for (const Site& s : kSites) {
        const auto actual = *reinterpret_cast<volatile std::uint32_t*>(base + s.offset);
        if (actual != s.expect) {
            LOGE("serial patch: unexpected instruction at +0x%zx (got 0x%08x, want 0x%08x); nothing changed",
                 s.offset, actual, s.expect);
            return;
        }
    }
    for (const Site& s : kSites) {
        auto* insn = reinterpret_cast<volatile std::uint32_t*>(base + s.offset);
        const std::uintptr_t page = reinterpret_cast<std::uintptr_t>(insn) & ~static_cast<std::uintptr_t>(0xFFF);
        if (mprotect(reinterpret_cast<void*>(page), 0x1000, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
            LOGE("serial patch: mprotect failed at +0x%zx: %s", s.offset, std::strerror(errno));
            return;
        }
        *insn = kNop;
        mprotect(reinterpret_cast<void*>(page), 0x1000, PROT_READ | PROT_EXEC);
        char* p = reinterpret_cast<char*>(const_cast<std::uint32_t*>(insn));
        __builtin___clear_cache(p, p + 4);
    }
    LOGI("serial patch: applied");
}

}  // namespace ax12
