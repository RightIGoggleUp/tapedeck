package com.patrick.tapedeck

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Track(val id: Long, val title: String, val artist: String, val durationMs: Long, val uri: Uri, val trackNo: Int = 0, val year: Int = 0)

/** One album = one cassette. Side A is the first half of the tracks, side B the rest. */
data class Cassette(val key: String, val album: String, val artist: String, val tracks: List<Track>) {
    val year: Int get() = tracks.firstOrNull { it.year > 0 }?.year ?: 0
}

fun <T> splitSides(tracks: List<T>): Pair<List<T>, List<T>> {
    val half = (tracks.size + 1) / 2
    return tracks.take(half) to tracks.drop(half)
}

fun loadCassettes(ctx: Context): List<Cassette> {
    val proj = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR
    )
    val rows = mutableListOf<Triple<Long, Track, Int>>() // albumId, track, trackNo
    val albums = mutableMapOf<Long, String>()
    ctx.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val albumId = c.getLong(4)
            albums[albumId] = c.getString(3) ?: "Unknown album"
            val t = Track(
                id, c.getString(1) ?: "Untitled", c.getString(2) ?: "Unknown artist",
                c.getLong(5), ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                c.getInt(6) % 1000, c.getInt(7)
            )
            rows += Triple(albumId, t, c.getInt(6))
        }
    }
    return rows.groupBy { it.first }.map { (albumId, list) ->
        val tracks = list.sortedBy { it.third % 1000 }.map { it.second }
        Cassette("album-$albumId", albums[albumId] ?: "Unknown album", tracks.first().artist, tracks)
    }.sortedBy { it.album.lowercase() }
}
