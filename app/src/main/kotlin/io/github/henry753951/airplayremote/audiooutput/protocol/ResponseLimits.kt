package io.github.henry753951.airplayremote.audiooutput.protocol

import java.io.IOException

internal fun responseBodyLength(value: String?): Int {
    if (value == null) return 0
    val size = value.toIntOrNull()
    if (size == null || size !in 0..1_048_576) throw IOException("Invalid AirPlay response length")
    return size
}
