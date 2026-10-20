package io.github.jqssun.airplay
import org.junit.Assert.*
import org.junit.Test
class PrefsTest {
    @Test fun renameUiAndStorageAcceptTheSameNames() {
        for (name in listOf("TV", "客廳電視", "", " ", "TV\n", "a".repeat(63), "a".repeat(64), "電".repeat(22))) {
            assertEquals(name, Prefs.SERVER_NAME.valid(name), io.github.jqssun.airplay.ui.tvNameError(name) == null)
        }
    }
    @Test fun sharedNameFitsOneBonjourLabel() {
        assertTrue(Prefs.SERVER_NAME.valid("Living Room"))
        assertTrue(Prefs.SERVER_NAME.valid("客廳電視"))
        assertTrue(Prefs.SERVER_NAME.valid("a".repeat(63)))
        assertFalse(Prefs.SERVER_NAME.valid("a".repeat(64)))
        assertFalse(Prefs.SERVER_NAME.valid("電".repeat(22)))
        assertFalse(Prefs.SERVER_NAME.valid(" "))
        assertFalse(Prefs.SERVER_NAME.valid("TV\n"))
    }
}
