package com.mzanak.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.delay

lateinit var loader: ImageLoader

data class Item(val uri: Uri, val name: String)
class Th(val hue: Float, val glass: Float) {
    val accent: Color get() = Color.hsv(hue, 0.42f, 0.95f)
}

fun load(c: Context, base: Uri): List<Item> {
    val out = ArrayList<Item>()
    c.contentResolver.query(
        base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
        null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    )?.use {
        while (it.moveToNext()) out.add(Item(ContentUris.withAppendedId(base, it.getLong(0)), it.getString(1) ?: ""))
    }
    return out
}

fun Modifier.glass(t: Th, r: Dp = 22.dp): Modifier {
    val s = RoundedCornerShape(r)
    return this.clip(s)
        .background(Brush.linearGradient(listOf(Color.White.copy(0.05f + 0.20f * t.glass), t.accent.copy(0.03f + 0.17f * t.glass))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.04f))), s)
}

fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

class MainActivity : ComponentActivity() {
    private val ctrl = mutableStateOf<MediaController?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loader = ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.build()
        val f = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        f.addListener({ ctrl.value = f.get() }, ContextCompat.getMainExecutor(this))
        setContent { App(ctrl.value) }
    }
}

@Composable
fun Btn(txt: String, onClick: () -> Unit) {
    Text(txt, color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { onClick() }.padding(10.dp))
}

@Composable
fun App(pl: MediaController?) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mz", 0) }
    var hue by remember { mutableFloatStateOf(prefs.getFloat("hue", 210f)) }
    var glass by remember { mutableFloatStateOf(prefs.getFloat("glass", 0.5f)) }
    val t = Th(hue, glass)

    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun ok() = ContextCompat.checkSelfPermission(ctx, perms[0]) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(ok()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = ok() }
    LaunchedEffect(Unit) { if (!granted) ask.launch(perms) }

    var rev by remember { mutableIntStateOf(0) }
    val photos = remember(granted, rev) { if (granted) load(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) else emptyList() }
    val videos = remember(granted, rev) { if (granted) load(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) else emptyList() }
    val songs = remember(granted, rev) { if (granted) load(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) else emptyList() }

    var tab by remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<Item?>(null) }
    var video by remember { mutableStateOf<Item?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(primary = t.accent)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
            Box(Modifier.size(300.dp).offset((-80).dp, (-40).dp).blur(90.dp).background(t.accent.copy(0.35f), CircleShape))
            Box(Modifier.size(260.dp).align(Alignment.BottomEnd).offset(60.dp, 0.dp).blur(90.dp).background(t.accent.copy(0.22f), CircleShape))
            val p = photo
            val v = video
            if (p != null) {
                PhotoScreen(p, t, { photo = null }, { rev++ })
            } else if (v != null) {
                VideoScreen(v, pl, t) { video = null }
            } else {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Text("Mzanak", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(20.dp, 12.dp))
                    Box(Modifier.weight(1f)) {
                        if (!granted && tab != 3) {
                            Text("Allow media access to see your files.", color = Color.White, modifier = Modifier.align(Alignment.Center).clickable { ask.launch(perms) })
                        } else when (tab) {
                            0 -> Grid(photos) { photo = it }
                            1 -> Grid(videos) { video = it }
                            2 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(songs.size) { i ->
                                    Text("♪  " + songs[i].name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().glass(t, 16.dp).clickable {
                                            pl?.let { c ->
                                                c.setMediaItems(songs.map { s -> MediaItem.Builder().setUri(s.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(s.name).build()).build() }, i, 0L)
                                                c.prepare(); c.play()
                                            }
                                        }.padding(16.dp))
                                }
                            }
                            else -> ThemeTab(t, { hue = it; prefs.edit().putFloat("hue", it).apply() }, { glass = it; prefs.edit().putFloat("glass", it).apply() })
                        }
                    }
                    Mini(pl, t)
                    Row(Modifier.padding(12.dp).fillMaxWidth().glass(t, 28.dp).padding(vertical = 6.dp)) {
                        listOf("Photos", "Videos", "Music", "Theme").forEachIndexed { i, n ->
                            Text(n, textAlign = TextAlign.Center, color = if (tab == i) t.accent else Color.White.copy(0.7f),
                                fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f).clickable { tab = i }.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Grid(items: List<Item>, onClick: (Item) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.uri.toString() }) {
            AsyncImage(it.uri, null, imageLoader = loader, contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable { onClick(it) })
        }
    }
}

@Composable
fun Mini(p: MediaController?, t: Th) {
    if (p == null) return
    var playing by remember { mutableStateOf(p.isPlaying) }
    var title by remember { mutableStateOf(p.mediaMetadata.title?.toString() ?: "") }
    DisposableEffect(p) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaMetadataChanged(m: MediaMetadata) { title = m.title?.toString() ?: "" }
        }
        p.addListener(l)
        onDispose { p.removeListener(l) }
    }
    if (title.isEmpty()) return
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().glass(t, 22.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Btn("⏮") { p.seekToPreviousMediaItem() }
        Btn(if (playing) "⏸" else "▶") { if (p.isPlaying) p.pause() else p.play() }
        Btn("⏭") { p.seekToNextMediaItem() }
    }
}

@Composable
fun VideoScreen(v: Item, p: MediaController?, t: Th, onBack: () -> Unit) {
    BackHandler { p?.pause(); onBack() }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(1L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(p, v) { p?.apply { setMediaItem(MediaItem.Builder().setUri(v.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(v.name).build()).build()); prepare(); play() } }
    LaunchedEffect(p) {
        while (true) {
            p?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(1L); playing = it.isPlaying }
            delay(400)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .clickable(remember { MutableInteractionSource() }, null) { show = !show }) {
        AndroidView({ c -> PlayerView(c).apply { useController = false; player = p } }, modifier = Modifier.fillMaxSize(), update = { it.player = p })
        if (show) Column(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp).fillMaxWidth().glass(t).padding(12.dp)) {
            Text(v.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(pos), color = Color.White, fontSize = 12.sp)
                Slider(pos.toFloat(), { pos = it.toLong(); p?.seekTo(it.toLong()) }, Modifier.weight(1f), valueRange = 0f..dur.toFloat())
                Text(fmt(dur), color = Color.White, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Btn("⏪10") { p?.seekTo((pos - 10000).coerceAtLeast(0)) }
                Btn(if (playing) "⏸" else "▶") { p?.let { if (it.isPlaying) it.pause() else it.play() } }
                Btn("10⏩") { p?.seekTo(pos + 10000) }
                Btn("${speed}x") { speed = if (speed >= 2f) 0.5f else speed + 0.5f; p?.setPlaybackSpeed(speed) }
                Btn("✕") { p?.pause(); onBack() }
            }
        }
    }
}

fun render(src: Bitmap, rot: Float, flip: Boolean, b: Float): Bitmap {
    val m = Matrix()
    if (flip) m.setScale(-1f, 1f)
    m.postRotate(rot)
    val r = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    val out = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, b, 0f, 1f, 0f, 0f, b, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f)))
    }
    android.graphics.Canvas(out).drawBitmap(r, 0f, 0f, paint)
    return out
}

fun saveCopy(c: Context, b: Bitmap) {
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Mzanak_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mzanak")
    }
    c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { u ->
        c.contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }
}

@Composable
fun PhotoScreen(ph: Item, t: Th, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val src = remember(ph) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, ph.uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.setTargetSampleSize(maxOf(1, maxOf(info.size.width, info.size.height) / 2000))
            }
        }.getOrNull()
    }
    var rot by remember { mutableFloatStateOf(0f) }
    var flip by remember { mutableStateOf(false) }
    var bright by remember { mutableFloatStateOf(0f) }
    val shown = remember(src, rot, flip, bright) { src?.let { render(it, rot, flip, bright) } }
    val del = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) { onChanged(); onBack() }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown != null) Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else AsyncImage(ph.uri, null, imageLoader = loader, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.padding(12.dp).fillMaxWidth().glass(t).padding(8.dp)) {
            Slider(bright, { bright = it }, valueRange = -80f..80f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Btn("⟲") { rot = (rot - 90f) % 360f }
                Btn("⟳") { rot = (rot + 90f) % 360f }
                Btn("⇋") { flip = !flip }
                Btn("Save") { shown?.let { saveCopy(ctx, it); Toast.makeText(ctx, "Saved to Pictures/Mzanak", Toast.LENGTH_SHORT).show(); onChanged() } }
                Btn("Delete") { del.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(ph.uri)).intentSender).build()) }
                Btn("✕") { onBack() }
            }
        }
    }
}

@Composable
fun ThemeTab(t: Th, onHue: (Float) -> Unit, onGlass: (Float) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.fillMaxWidth().glass(t).padding(20.dp)) {
            Text("Accent color", color = Color.White)
            Slider(t.hue, onHue, valueRange = 0f..360f)
            Text("Glass intensity", color = Color.White)
            Slider(t.glass, onGlass, valueRange = 0f..1f)
        }
        Spacer(Modifier.weight(1f))
        Text("Developed by Shaikh Mzanak", color = Color.White.copy(0.6f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}
