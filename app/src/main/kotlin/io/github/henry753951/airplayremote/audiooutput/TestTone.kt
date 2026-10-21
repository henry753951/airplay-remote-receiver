package io.github.henry753951.airplayremote.audiooutput

import kotlin.math.PI
import kotlin.math.sin

internal class TestTone {
    private var frame = 0L

    fun next(frames: Int = 441): ShortArray {
        require(frames in 1..44100)
        return ShortArray(frames * 2).also { pcm ->
            repeat(frames) { i ->
                // A quiet 440 Hz tone; both channels carry the same sample.
                val sample = (sin(2 * PI * 440 * frame++ / 44100) * 1000).toInt().toShort()
                pcm[i * 2] = sample
                pcm[i * 2 + 1] = sample
            }
        }
    }
}
