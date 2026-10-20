package io.github.henry753951.airplayremote.runtime

import android.content.SharedPreferences
import io.github.henry753951.airplayremote.Prefs
import io.github.henry753951.airplayremote.get
import io.github.henry753951.airplayremote.observe
import io.github.henry753951.airplayremote.playback.renderer.AudioConfig
import kotlinx.coroutines.flow.Flow

fun readAudioConfig(p: SharedPreferences) = AudioConfig(
    cushionMs = if (p[Prefs.AUDIO_AUTO_BUFFER]) 0 else p[Prefs.AUDIO_CUSHION_MS],
    percentilePct = Prefs.ADAPTIVE_PERCENTILES[p[Prefs.AUDIO_ADAPTIVE_STEP]],
    oboeBufferFrames = p[Prefs.OBOE_BUFFER_FRAMES],
    forceSwAlac = p[Prefs.FORCE_SW_ALAC],
    forceSwAac = p[Prefs.FORCE_SW_AAC],
    realtimePriority = p[Prefs.KEY_PRIORITY],
    lowLatency = p[Prefs.LOW_LATENCY],
    benchmarkLog = p[Prefs.BENCHMARK_LOG],
)

fun SharedPreferences.audioConfigFlow(): Flow<AudioConfig> = observe(::readAudioConfig)
