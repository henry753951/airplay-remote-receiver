package io.github.jqssun.airplay.renderer
import org.junit.Assert.*
import org.junit.Test
class ColorPolicyTest {
    @Test fun sourceRangeIsNotOverriddenByDefault() {
        assertNull(ColorPolicy.range("auto"))
        assertNull(ColorPolicy.range("unknown"))
        assertEquals(1, ColorPolicy.range("full"))
        assertEquals(2, ColorPolicy.range("limited"))
    }
}
