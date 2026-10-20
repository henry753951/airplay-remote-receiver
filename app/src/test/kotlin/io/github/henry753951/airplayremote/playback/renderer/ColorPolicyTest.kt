package io.github.henry753951.airplayremote.playback.renderer
import org.junit.Assert.*
import org.junit.Test
class ColorPolicyTest {
    @Test fun rawSamplingOnlyHandlesFullRangeEightBit709Sdr() {
        assertTrue(ColorPolicy.rawYuv(1, 1, 2, 19))
        assertTrue(ColorPolicy.rawYuv(1, 1, 2, 21))
        assertTrue(ColorPolicy.rawYuv(1, 1, 3, 19))
        assertTrue(ColorPolicy.rawYuv(1, 1, 3, 21))
        assertFalse(ColorPolicy.rawYuv(1, 2, 2, 21))
        assertFalse(ColorPolicy.rawYuv(1, 1, 1, 21))
        assertFalse(ColorPolicy.rawYuv(1, 2, 3, 21))
        assertFalse(ColorPolicy.rawYuv(6, 1, 6, 21))
        assertFalse(ColorPolicy.rawYuv(1, 1, 7, 21))
        assertFalse(ColorPolicy.rawYuv(1, 1, 3, 54))
        assertFalse(ColorPolicy.rawYuv(0, 0, 0, 0))
    }
    @Test fun sourceRangeIsNotOverriddenByDefault() {
        assertNull(ColorPolicy.range("auto"))
        assertNull(ColorPolicy.range("unknown"))
        assertEquals(1, ColorPolicy.range("full"))
        assertEquals(2, ColorPolicy.range("limited"))
    }
}
