package io.github.henry753951.airplayremote.audiooutput

import io.github.henry753951.airplayremote.audiooutput.protocol.AirplayDevice
import io.github.henry753951.airplayremote.audiooutput.protocol.AirplayCrypto
import io.github.henry753951.airplayremote.audiooutput.protocol.Bplist
import io.github.henry753951.airplayremote.audiooutput.protocol.PValue
import io.github.henry753951.airplayremote.audiooutput.protocol.responseBodyLength
import io.github.henry753951.airplayremote.audiooutput.protocol.validIpv4
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.math.abs

class AudioOutputTest {
    @Test fun modifiedEncryptedPacketIsRejected() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = AirplayCrypto.counterNonce8(7)
        val aad = byteArrayOf(1, 0)
        val encrypted = AirplayCrypto.chacha20Poly1305Encrypt(key, nonce, byteArrayOf(1, 2, 3), aad)
        assertArrayEquals(byteArrayOf(1, 2, 3), AirplayCrypto.chacha20Poly1305Decrypt(key, nonce, encrypted, aad))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertNull(AirplayCrypto.chacha20Poly1305Decrypt(key, nonce, encrypted, aad))
    }

    @Test fun targetRejectsUrlsMulticastAndInvalidPorts() {
        assertEquals(7000, AirplayDevice("Living room", "192.168.22.10", 7000).port)
        listOf("", "example.com", "http://192.168.1.2", "1.2.3", "1.2.3.256", "01.2.3.4",
            "127.0.0.1", "224.0.0.251", "255.255.255.255", "::1", "1.2.3.-1").forEach {
            assertFalse(it, validIpv4(it))
        }
        listOf(0, -1, 65536).forEach {
            assertThrows(IllegalArgumentException::class.java) { AirplayDevice("HomePod", "192.168.1.2", it) }
        }
    }

    @Test fun toneIsQuietStereoAndContinuousAcrossBlocks() {
        val generator = TestTone()
        val split = generator.next(441) + generator.next(441)
        assertArrayEquals(TestTone().next(882), split)
        assertTrue(split.any { it != 0.toShort() })
        split.indices.step(2).forEach { i ->
            assertEquals(split[i], split[i + 1])
            assertTrue(abs(split[i].toInt()) <= 1000)
        }
    }

    @Test fun receiverCannotRequestUnboundedOrNegativeBodies() {
        assertEquals(0, responseBodyLength(null))
        assertEquals(1048576, responseBodyLength("1048576"))
        listOf("-1", "1048577", "2147483647", "999999999999999999999", "oops").forEach {
            assertThrows(IOException::class.java) { responseBodyLength(it) }
        }
    }

    @Test fun binaryPlistRejectsNegativeObjectReferencesAndInvalidWidths() {
        val valid = Bplist.encode(PValue.ArrV(listOf(PValue.IntV(1))))
        val negativeRoot = valid.copyOf().apply {
            repeat(8) { this[size - 16 + it] = 0xff.toByte() }
        }
        assertNull(Bplist.decode(negativeRoot))
        assertNull(Bplist.decode(valid.copyOf().apply { this[size - 25] = 0 }))
    }

    @Test fun binaryPlistDecodesHomePodUnicodeName() {
        val bytes = "客廳".toByteArray(Charsets.UTF_16BE)
        val data = "bplist00".toByteArray() + byteArrayOf(0x62) + bytes + byteArrayOf(8) + ByteArray(32).apply {
            this[6] = 1; this[7] = 1; this[15] = 1; this[31] = 13
        }
        assertEquals("客廳", (Bplist.decode(data) as PValue.StrV).s)
    }
}
