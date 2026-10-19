package io.github.jqssun.airplay.service
import org.junit.Assert.*
import org.junit.Test
class PowerPolicyTest {
    @Test fun standbyModesAndStopReleaseTheCpu() {
        for (reachable in listOf(false, true)) for (clients in listOf(0, 1)) for (remote in listOf(false, true)) {
            assertFalse(PowerPolicy.holdCpu(false, reachable, clients, remote))
        }
        assertTrue(PowerPolicy.holdCpu(true, true, 0, false))
        assertFalse(PowerPolicy.holdCpu(true, false, 0, false))
        assertTrue(PowerPolicy.holdCpu(true, false, 1, false))
        assertTrue(PowerPolicy.holdCpu(true, false, 0, true))
    }
}
