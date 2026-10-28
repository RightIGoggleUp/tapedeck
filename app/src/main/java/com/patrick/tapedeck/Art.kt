package com.patrick.tapedeck

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Album art read from the music files themselves (embedded tags), cached per cassette. */
object Art {
    val cache = mutableStateMapOf<String, ImageBitmap>()
    private val tried = HashSet<String>()

    suspend fun ensure(ctx: Context, c: Cassette) {
        if (cache.containsKey(c.key) || !tried.add(c.key)) return
        val bmp = withContext(Dispatchers.IO) {
            var found: android.graphics.Bitmap? = null
            for (t in c.tracks.take(4)) {
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(ctx, t.uri)
                    val bytes = r.embeddedPicture
                    r.release()
                    if (bytes != null) {
                        val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                        found = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    }
                } catch (_: Exception) {
                }
                if (found == null && Build.VERSION.SDK_INT >= 29) {
                    try {
                        found = ctx.contentResolver.loadThumbnail(t.uri, Size(512, 512), null)
                    } catch (_: Exception) {
                    }
                }
                if (found != null) break
            }
            found
        }
        if (bmp != null) cache[c.key] = bmp.asImageBitmap()
    }
}
