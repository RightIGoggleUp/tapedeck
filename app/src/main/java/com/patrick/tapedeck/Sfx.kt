package com.patrick.tapedeck

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthesized cassette-deck sounds (no recordings). */
object Sfx {
    private const val RATE = 22050
    private val rnd = Random(7)
    private var clickT: AudioTrack? = null
    private var clunkT: AudioTrack? = null
    private var hissT: AudioTrack? = null

    private fun build(pcm: ShortArray): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        t.write(pcm, 0, pcm.size)
        return t
    }

    private fun clickPcm(): ShortArray {
        val n = RATE * 45 / 1000
        return ShortArray(n) { i ->
            val e = exp(-i / (n / 6.0))
            val v = (rnd.nextFloat() * 2 - 1) * 0.7 * e + sin(2 * PI * 180 * i / RATE) * 0.5 * e
            (v * 20000).toInt().coerceIn(-32000, 32000).toShort()
        }
    }

    private fun clunkPcm(): ShortArray {
        val n = RATE * 160 / 1000
        return ShortArray(n) { i ->
            val e = exp(-i / (n / 5.0))
            val v = sin(2 * PI * 90 * i / RATE) * 0.8 * e + (rnd.nextFloat() * 2 - 1) * 0.3 * e
            (v * 22000).toInt().coerceIn(-32000, 32000).toShort()
        }
    }

    private fun hissPcm(): ShortArray {
        val n = RATE * 2
        var last = 0f
        return ShortArray(n) {
            last = last * 0.6f + (rnd.nextFloat() * 2 - 1) * 0.4f
            (last * 2500).toInt().toShort()
        }
    }

    private fun replay(t: AudioTrack) {
        try {
            t.stop(); t.reloadStaticData(); t.play()
        } catch (_: Exception) {
        }
    }

    @Synchronized fun click() {
        val t = clickT ?: build(clickPcm()).also { clickT = it }
        replay(t)
    }

    @Synchronized fun clunk() {
        val t = clunkT ?: build(clunkPcm()).also { clunkT = it }
        replay(t)
    }

    @Synchronized fun hiss(on: Boolean) {
        try {
            if (on) {
                val t = hissT ?: build(hissPcm()).also {
                    it.setLoopPoints(0, RATE * 2, -1); it.setVolume(0.35f); hissT = it
                }
                t.play()
            } else hissT?.pause()
        } catch (_: Exception) {
        }
    }
}
