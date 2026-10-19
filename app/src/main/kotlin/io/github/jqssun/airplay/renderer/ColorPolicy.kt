package io.github.jqssun.airplay.renderer

internal object ColorPolicy {
    // MediaFormat: full=1, limited=2; omission lets the codec use the bitstream metadata.
    fun range(value: String): Int? = when (value) { "full" -> 1; "limited" -> 2; else -> null }
}
