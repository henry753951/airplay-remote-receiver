package io.github.henry753951.airplayremote.playback.renderer

internal object ColorPolicy {
    // MediaFormat: full=1, limited=2; omission lets the codec use the bitstream metadata.
    fun range(value: String): Int? = when (value) { "full" -> 1; "limited" -> 2; else -> null }

    // AOSP ColorUtils: transfer 2 is sRGB, 3 is SMPTE 170M; both carry encoded SDR values.
    fun rawYuv(standard: Int, range: Int, transfer: Int, format: Int): Boolean =
        standard == 1 && range == 1 && (transfer == 2 || transfer == 3) &&
            (format == 19 || format == 21)
}
