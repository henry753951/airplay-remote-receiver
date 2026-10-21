// SPDX-License-Identifier: Apache-2.0
// Adapted from AirBridge, revision 12d4fa781b86aaac6135e9279044d8a0f81fce21.
// Local changes: package integration, lifecycle cancellation and input validation.
package io.github.henry753951.airplayremote.audiooutput.protocol

import org.junit.Assert.*
import org.junit.Test

class BplistTest {
    @Test
    fun roundTripSimpleDict() {
        val root = PValue.DictV(
            listOf(
                "eventPort" to PValue.IntV(7000),
                "name" to PValue.StrV("HomePod"),
                "ok" to PValue.True,
                "no" to PValue.False,
                "data" to PValue.DataV(byteArrayOf(1, 2, 3)),
                "list" to PValue.ArrV(listOf(PValue.IntV(1), PValue.IntV(2)))
            )
        )
        val decoded = Bplist.decode(Bplist.encode(root)) as? PValue.DictV
        assertNotNull(decoded)
        val pairs = decoded!!.pairs.toMap()
        assertEquals(7000L, (pairs["eventPort"] as PValue.IntV).v)
        assertEquals("HomePod", (pairs["name"] as PValue.StrV).s)
        assertTrue(pairs["ok"] is PValue.True)
        assertTrue(pairs["no"] is PValue.False)
        assertArrayEquals(byteArrayOf(1, 2, 3), (pairs["data"] as PValue.DataV).b)
        val list = (pairs["list"] as PValue.ArrV).items
        assertEquals(2, list.size)
    }

    @Test
    fun decodeRejectsTruncated() {
        assertNull(Bplist.decode(byteArrayOf(0, 1, 2)))
    }
}