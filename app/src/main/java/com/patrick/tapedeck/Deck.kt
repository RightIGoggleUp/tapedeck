package com.patrick.tapedeck

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** The tape deck. One instance; survives fold/unfold because it lives outside the activity. */
object Deck {
    var tape by mutableStateOf<Cassette?>(null)
    var side by mutableIntStateOf(0)
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    private var mp: MediaPlayer? = null
    private var pfd: ParcelFileDescriptor? = null
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val vlc: LibVLC by lazy { LibVLC(app, arrayListOf("--no-video", "--network-caching=1500")) }

    fun init(ctx: Context) { app = ctx.applicationContext }

    private fun releasePlayer() {
        mp?.let { try { it.setEventListener(null); it.stop(); it.release() } catch (_: Exception) {} }
        mp = null
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
    }

    fun sideTracks(): List<Track> {
        return tape?.tracks ?: emptyList()
    }

    fun current(): Track? = sideTracks().getOrNull(index)

    fun load(c: Cassette) {
        halt()
        tape = c; side = 0; index = 0; positionMs = 0; durationMs = 0
        Sfx.clunk()
    }

    fun eject() {
        halt(); tape = null; Sfx.clunk()
    }

    fun toggle() { if (playing) pause() else play() }

    fun play() {
        val track = current() ?: return
        Sfx.click()
        if (mp == null) {
            try {
                val media = if (track.uri.scheme == "http" || track.uri.scheme == "https") Media(vlc, track.uri)
                else {
                    pfd = app.contentResolver.openFileDescriptor(track.uri, "r")
                    Media(vlc, pfd!!.fileDescriptor)
                }
                val player = MediaPlayer(vlc)
                player.media = media
                media.release()
                player.setEventListener { ev ->
                    when (ev.type) {
                        MediaPlayer.Event.EndReached -> main.post { next(auto = true) }
                        MediaPlayer.Event.LengthChanged -> main.post { durationMs = ev.lengthChanged }
                    }
                }
                mp = player
                if (positionMs > 0) player.time = positionMs
            } catch (e: Exception) {
                releasePlayer(); return
            }
        }
        mp?.play()
        durationMs = if (durationMs > 0) durationMs else track.durationMs
        playing = true
        Sfx.hiss(true)
        ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
    }

    fun pause() {
        Sfx.click()
        mp?.let { positionMs = it.time; it.pause() }
        playing = false
        Sfx.hiss(false)
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    private fun halt() {
        releasePlayer()
        playing = false; positionMs = 0
        Sfx.hiss(false)
        if (::app.isInitialized) app.stopService(Intent(app, PlaybackService::class.java))
    }

    private fun jump(newIndex: Int, resume: Boolean) {
        releasePlayer()
        index = newIndex; positionMs = 0; durationMs = 0
        if (resume) play() else { playing = false; Sfx.hiss(false) }
    }

    fun next(auto: Boolean = false) {
        val n = sideTracks().size
        if (index + 1 < n) { if (!auto) Sfx.click(); jump(index + 1, playing || auto) }
        else if (auto) { halt(); index = 0; Sfx.clunk() }
    }

    fun prev() {
        Sfx.click()
        if (positionMs > 3000 || index == 0) { mp?.time = 0L; positionMs = 0 }
        else jump(index - 1, playing)
    }

    fun flip() {
        // one continuous tape per album: no sides
    }

    var seeking by mutableStateOf(false)

    fun stop() { halt(); Sfx.clunk() }

    fun seekBy(deltaMs: Long) {
        Sfx.click()
        mp?.let {
            val target = (it.time + deltaMs).coerceIn(0L, (it.length - 500).coerceAtLeast(0L))
            it.time = target; positionMs = target
        }
        seeking = true
        main.postDelayed({ seeking = false }, 700)
    }

    fun albumTotalMs(): Long = sideTracks().sumOf { it.durationMs }

    fun elapsedOnSideMs(): Long = sideTracks().take(index).sumOf { it.durationMs } + positionMs

    fun sideFraction(): Float {
        val ts = sideTracks()
        val total = ts.sumOf { it.durationMs }
        if (total <= 0) return if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        return (elapsedOnSideMs().toFloat() / total).coerceIn(0f, 1f)
    }

    fun tick() {
        mp?.let { if (playing) { positionMs = it.time } }
    }
}

/** Wrap any file or stream the user opens as a one-track tape. */
fun singleTape(title: String, uri: android.net.Uri): Cassette =
    Cassette("open-" + uri.hashCode(), title, "Opened file", listOf(Track(0, title, "Opened file", 0, uri)))
