package com.patrick.tapedeck

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF121212)
private val Panel = Color(0xFF1E1E1E)
private val Accent = Color(0xFFFF6A00)
private val SilverHi = Color(0xFFD5D9DE)
private val SilverLo = Color(0xFF8E959E)
private val Ink = Color(0xFF22262B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Deck.init(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tapes by remember { mutableStateOf<List<Cassette>>(emptyList()) }
    var granted by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it[audioPerm] == true
    }
    LaunchedEffect(Unit) {
        val perms = mutableListOf(audioPerm)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(perms.toTypedArray())
    }
    LaunchedEffect(granted) {
        if (granted) {
            tapes = withContext(Dispatchers.IO) { loadCassettes(ctx) }
            loaded = true
        }
    }
    LaunchedEffect(Deck.playing) {
        while (Deck.playing) { Deck.tick(); delay(500) }
    }

    // Wide window = phone open (inner screen). Narrow = closed (cover screen) or any normal phone.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var prevWide by remember { mutableStateOf(wide) }
    LaunchedEffect(wide) {
        if (wide != prevWide) { Sfx.clunk(); prevWide = wide }
    }
    var showShelf by remember { mutableStateOf(false) }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Opened file"
            Deck.load(singleTape(name, uri)); showShelf = false
        }
    }

    when {
        !granted -> Centered("TapeDeck needs access to your music files to build your cassettes.") {
            Button(onClick = { launcher.launch(arrayOf(audioPerm)) }) { Text("Allow music access") }
        }
        loaded && tapes.isEmpty() -> Centered("No music found on this phone. Copy some songs over and reopen.") {}
        wide -> Row(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Shelf(tapes, Modifier.weight(0.9f).fillMaxHeight(), onOpen = { opener.launch(arrayOf("audio/*", "video/*")) })
            DeckPanel(Modifier.weight(1.1f).fillMaxHeight(), onShelf = null, compact = false)
        }
        showShelf || Deck.tape == null -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
            Shelf(tapes, Modifier.weight(1f), onPicked = { showShelf = false }, onOpen = { opener.launch(arrayOf("audio/*", "video/*")) })
            if (Deck.tape != null) TextButton(onClick = { showShelf = false }) { Text("Back to player") }
        }
        else -> DeckPanel(Modifier.fillMaxSize().statusBarsPadding().padding(10.dp), onShelf = { showShelf = true }, compact = true)
    }
}

@Composable
fun Centered(msg: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun Shelf(tapes: List<Cassette>, modifier: Modifier = Modifier, onPicked: () -> Unit = {}, onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    Column(modifier) {
        Text("Your tapes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("Tap one to load it into the deck", fontSize = 13.sp, color = Color.Gray)
        TextButton(onClick = onOpen) { Text("+ Open any audio file", color = Accent) }
        Text("Open source (Apache-2.0). Playback by libVLC (LGPL 2.1), videolan.org", fontSize = 10.sp, color = Color.DarkGray)
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tapes, key = { it.key }) { c ->
                LaunchedEffect(c.key) { Art.ensure(ctx, c) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
                        .clickable { Deck.load(c); onPicked() }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CassetteView(c, false, Modifier.width(120.dp).height(76.dp), 0f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.album, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.tracks.size} tracks" + (if (c.year > 0) "  -  ${c.year}" else ""), color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun DeckPanel(modifier: Modifier, onShelf: (() -> Unit)?, compact: Boolean) {
    val tape = Deck.tape
    val ctx = LocalContext.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (tape == null) {
            Text("No tape loaded", color = Color.Gray, fontSize = 18.sp)
            return@Column
        }
        LaunchedEffect(tape.key) { Art.ensure(ctx, tape) }
        val track = Deck.current()
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(SilverHi, SilverLo)))
                .padding(if (compact) 8.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TAPEDECK", color = Ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (Deck.playing) Color(0xFFFF3B30) else Color(0xFF5A3030)))
                Text("STEREO CASSETTE", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            // the tape, with the keys directly under the reels
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF15181B)).padding(8.dp)) {
                CassetteView(tape, Deck.playing, Modifier.fillMaxWidth().aspectRatio(1.58f), Deck.sideFraction())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Key("REW", Modifier.weight(1f)) { Deck.seekBy(-15000) }
                    Key(if (Deck.playing) "PAUSE" else "PLAY", Modifier.weight(1.3f), hot = true) { Deck.toggle() }
                    Key("FF", Modifier.weight(1f)) { Deck.seekBy(15000) }
                    Key("STOP", Modifier.weight(1f)) { Deck.stop() }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Key("|<", Modifier.weight(1f)) { Deck.prev() }
                    Key(">|", Modifier.weight(1f)) { Deck.next() }
                    Key("EJECT", Modifier.weight(1.2f)) { Deck.eject() }
                    if (onShelf != null) Key("TAPES", Modifier.weight(1.2f)) { onShelf() }
                }
            }
            Spacer(Modifier.height(8.dp))
            // one ticker: this song, plus the whole album when there is more than one song
            val multi = tape.tracks.size > 1
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B2A1E)).padding(10.dp)) {
                Text(track?.title ?: "", color = Color(0xFF9DFFB0), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(track?.artist, tape.album, tape.year.takeIf { it > 0 }?.toString()).joinToString(" - "), color = Color(0xFF6FCF83), fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val song = "${fmt(Deck.positionMs)} / ${fmt(Deck.durationMs)}"
                val album = if (multi) "   |   ${fmt(Deck.elapsedOnSideMs())} / ${fmt(Deck.albumTotalMs())}" else ""
                Text(song + album, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                if (multi) Text("song ${Deck.index + 1} of ${tape.tracks.size}", color = Color(0xFF6FCF83), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun Key(label: String, modifier: Modifier, hot: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(6.dp))
            .background(if (hot) Brush.verticalGradient(listOf(Color(0xFFFF9A4D), Accent)) else Brush.verticalGradient(listOf(Color(0xFFEEF0F2), Color(0xFFAEB4BB))))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (hot) Color.White else Ink, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun CassetteView(c: Cassette, spinning: Boolean, modifier: Modifier, progress: Float, vertical: Boolean = false) {
    val hue = (c.key.hashCode().mod(360)).toFloat()
    val body = Color.hsv(hue, 0.35f, 0.30f)
    val paper = Color(0xFFF2EBDD)
    val art = Art.cache[c.key]
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spinning) {
        var last = 0L
        while (spinning) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    angle = (angle + dt * 140f * (if (Deck.seeking) 5f else 1f)) % 3600f
                }
                last = now
            }
        }
    }
    val tm = rememberTextMeasurer()
    val track = Deck.current()
    val title = if (Deck.tape?.key == c.key) track?.title ?: c.album else c.album
    Canvas(modifier) {
        val drawLandscape: DrawScope.(Float, Float) -> Unit = { w, h ->
            drawRoundRect(body, Offset.Zero, Size(w, h), CornerRadius(h * 0.07f))
            // label paper
            val lx = w * 0.07f; val ly = h * 0.07f; val lw = w * 0.86f; val lh = h * 0.47f
            drawRoundRect(paper, Offset(lx, ly), Size(lw, lh), CornerRadius(h * 0.03f))
            drawRect(Accent, Offset(lx, ly), Size(lw, lh * 0.12f))
            var textX = lx + lw * 0.04f
            var textW = lw * 0.92f
            if (art != null) {
                val side = lh * 0.74f
                val sz = minOf(art.width, art.height)
                drawImage(
                    art, srcOffset = IntOffset((art.width - sz) / 2, (art.height - sz) / 2), srcSize = IntSize(sz, sz),
                    dstOffset = IntOffset((lx + lw * 0.03f).toInt(), (ly + lh * 0.2f).toInt()), dstSize = IntSize(side.toInt(), side.toInt())
                )
                textX = lx + lw * 0.03f + side + lw * 0.03f
                textW = lw - (textX - lx) - lw * 0.03f
            }
            val big = TextStyle(color = Ink, fontSize = (h * 0.075f).toSp(), fontWeight = FontWeight.Bold)
            val small = TextStyle(color = Color(0xFF555555), fontSize = (h * 0.055f).toSp())
            drawText(tm, title, Offset(textX, ly + lh * 0.24f), big, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, size = Size(textW, lh))
            drawText(tm, c.artist, Offset(textX, ly + lh * 0.50f), small, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, size = Size(textW, lh))
            drawText(tm, c.album, Offset(textX, ly + lh * 0.68f), small, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, size = Size(textW, lh))
            // window
            val wx = w * 0.17f; val wy = h * 0.58f; val ww = w * 0.66f; val wh = h * 0.33f
            drawRoundRect(Color(0xFF0E0E0E), Offset(wx, wy), Size(ww, wh), CornerRadius(wh * 0.5f))
            val cy = wy + wh / 2
            val hubR = wh * 0.20f
            val maxR = wh * 0.47f
            val p = progress.coerceIn(0f, 1f)
            val rL = hubR + (maxR - hubR) * (1f - p)
            val rR = hubR + (maxR - hubR) * p
            val cxL = w * 0.33f; val cxR = w * 0.67f
            drawCircle(Color(0xFF3B2A20), rL, Offset(cxL, cy))
            drawCircle(Color(0xFF3B2A20), rR, Offset(cxR, cy))
            drawLine(Color(0xFF3B2A20), Offset(cxL, cy + rL), Offset(cxR, cy + rR), strokeWidth = h * 0.012f)
            for ((cx, r) in listOf(cxL to rL, cxR to rR)) {
                val a = angle * (maxR / r).coerceAtMost(2.2f)
                drawCircle(Color(0xFFEDEDED), hubR, Offset(cx, cy))
                drawCircle(Color(0xFF111111), hubR * 0.62f, Offset(cx, cy))
                rotate(a, Offset(cx, cy)) {
                    for (i in 0 until 6) {
                        rotate(i * 60f, Offset(cx, cy)) {
                            drawLine(Color(0xFFEDEDED), Offset(cx, cy - hubR * 0.55f), Offset(cx, cy - hubR * 0.95f), strokeWidth = hubR * 0.28f)
                        }
                    }
                }
            }
            drawRoundRect(Color(0x22FFFFFF), Offset(wx, wy), Size(ww, wh * 0.4f), CornerRadius(wh * 0.5f))
            // screws
            for (sx in listOf(0.03f, 0.97f)) for (sy in listOf(0.06f, 0.94f)) drawCircle(Color(0xFF888888), h * 0.018f, Offset(w * sx, h * sy))
        }
        if (vertical) {
            withTransform({ translate(left = size.width, top = 0f); rotate(90f, Offset.Zero) }) { drawLandscape(size.height, size.width) }
        } else drawLandscape(size.width, size.height)
    }
}
