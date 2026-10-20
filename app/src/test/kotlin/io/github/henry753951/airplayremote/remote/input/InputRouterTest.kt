package io.github.henry753951.airplayremote.remote.input

import org.junit.Assert.assertEquals
import org.junit.Test

class InputRouterTest {
    @Test fun autoStopsAfterTheFirstSuccessfulBackend() {
        val calls = mutableListOf<String>()
        InputRouter.route(InjectionMode.AUTO,
            hardware = { calls += "hardware"; false },
            adb = { calls += "adb"; true },
            accessibility = { error("Must not replay an accepted key") })
        assertEquals(listOf("hardware", "adb"), calls)
    }

    @Test fun explicitModeNeverFallsThroughWhenUnavailable() {
        assertEquals("本地 ADB 失败 (端口未连接或已关闭)", InputRouter.route(InjectionMode.ADB,
            hardware = { error("Wrong backend") }, adb = { false },
            accessibility = { error("Explicit ADB mode must not use accessibility") }))
    }

    @Test fun autoCanFallBackToAccessibility() {
        assertEquals("无障碍服务 (Accessibility)", InputRouter.route(InjectionMode.AUTO,
            hardware = { false }, adb = { false }, accessibility = { true }))
    }
}
