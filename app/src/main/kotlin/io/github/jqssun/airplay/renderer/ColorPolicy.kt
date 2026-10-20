package io.github.jqssun.airplay.renderer

internal object ColorPolicy {
    // MediaFormat: full=1, limited=2; omission lets the codec use the bitstream metadata.
    fun range(value: String): Int? = when (value) { "full" -> 1; "limited" -> 2; else -> null }

    // Only 8-bit BT.709 SDR is handled by the explicit full-range conversion.
    fun rawYuv(standard: Int, range: Int, transfer: Int, format: Int): Boolean =
        standard == 1 && range == 1 && transfer == 3 && (format == 19 || format == 21)
}
