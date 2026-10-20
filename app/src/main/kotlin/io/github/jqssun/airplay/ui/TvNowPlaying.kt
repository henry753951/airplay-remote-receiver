package io.github.jqssun.airplay.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val COVER = 320.dp

// Audio-only casting, laid out like a TV's own music player: the artwork on the left, the song and
// its controls on the right, over a quiet backdrop tinted by the artwork. Nothing loops; the only
// motion is a short entrance when the track changes and the cover easing back while paused.
@Composable
internal fun TvNowPlaying(
    viewModel: MainViewModel,
    onSettings: () -> Unit,
    focusSettings: Boolean
) {
    // Back is handled by the router: it ends the cast, as on the other playing screens
    val track by viewModel.trackInfo.collectAsState()
    val player = viewModel.dacpPlayer

    // the player calls back on the main looper; only the parts this screen shows are mirrored
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var positionMs by remember(player) { mutableLongStateOf(player?.currentPosition ?: 0L) }
    var playerDurationMs by remember(player) { mutableLongStateOf(player?.duration ?: C.TIME_UNSET) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                playing = p.isPlaying
                positionMs = p.currentPosition
                playerDurationMs = p.duration
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    // the clock only ticks while something is playing
    LaunchedEffect(player, playing) {
        while (playing && player != null) {
            positionMs = player.currentPosition
            delay(500)
        }
    }
    val durationMs = playerDurationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: track.durationMs

    val cover = track.coverArt
    val tint = remember(cover) { cover?.let(::averageColor) ?: Color(0xFF2A2A2E) }
    val backdropTint by animateColorAsState(tint, tween(600), label = "tint")

    // entrance per track: artwork settles in, then the words follow
    val trackKey = track.title + "\u0000" + track.artist + "\u0000" + track.album
    val coverIn = remember { Animatable(0f) }
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(trackKey) {
        coverIn.snapTo(0f)
        textIn.snapTo(0f)
        launch { coverIn.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        delay(90)
        textIn.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
    }
    // the cover steps back a little while paused, like a record lifting off
    val coverScale by animateFloatAsState(if (playing) 1f else 0.93f, tween(360, easing = FastOutSlowInEasing), label = "pause")

    val playFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(player) {
        (if (focusSettings || player == null) settingsFocus else playFocus).requestFocusUntilLanded()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvBg)
            // a soft glow of the artwork's colour behind the cover, fading out towards the words
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(backdropTint.copy(alpha = 0.55f), backdropTint.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset((112.dp + COVER / 2).toPx(), size.height / 2),
                        radius = size.width * 0.75f
                    )
                )
            }
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 112.dp, end = 96.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(COVER)
                    .graphicsLayer {
                        val s = (0.94f + 0.06f * coverIn.value) * coverScale
                        scaleX = s
                        scaleY = s
                        alpha = coverIn.value
                    }
                    .shadow(28.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(TvTile),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        cover.asImageBitmap(), stringResource(R.string.cd_cover_art),
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(96.dp), tint = TvMuted)
                }
            }
            Spacer(Modifier.width(72.dp))
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = textIn.value
                        translationX = (1f - textIn.value) * 28.dp.toPx()
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AirPlayMark(16.dp, TvMuted)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.tv_now_playing), color = TvMuted, fontSize = 16.sp, maxLines = 1)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    track.title.ifEmpty { stringResource(R.string.unknown_track) },
                    color = TvText, fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (track.artist.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        track.artist, color = TvText.copy(alpha = 0.82f), fontSize = 22.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (track.album.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(track.album, color = TvMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(36.dp))
                if (durationMs > 0) {
                    TvProgress(positionMs.coerceIn(0L, durationMs), durationMs)
                    Spacer(Modifier.height(32.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvRoundButton(
                        Icons.Default.SkipPrevious, stringResource(R.string.tv_cd_previous), 60.dp,
                        enabled = player != null
                    ) { player?.seekToPrevious() }
                    Spacer(Modifier.width(18.dp))
                    TvRoundButton(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(R.string.cd_play_pause), 76.dp,
                        enabled = player != null,
                        modifier = Modifier.focusRequester(playFocus)
                    ) { player?.let { if (it.isPlaying) it.pause() else it.play() } }
                    Spacer(Modifier.width(18.dp))
                    TvRoundButton(
                        Icons.Default.SkipNext, stringResource(R.string.tv_cd_next), 60.dp,
                        enabled = player != null
                    ) { player?.seekToNext() }
                    Spacer(Modifier.weight(1f))
                    TvRoundButton(
                        Icons.Default.Settings, stringResource(R.string.tv_settings), 52.dp,
                        modifier = Modifier.focusRequester(settingsFocus), onClick = onSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun TvProgress(positionMs: Long, durationMs: Long) {
    val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(TvText))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(positionMs), color = TvMuted, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text("-" + formatClock(durationMs - positionMs), color = TvMuted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TvRoundButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) TvText else Color(0x1FFFFFFF), tween(FOCUS_MS), label = "roundBg")
    val fg by animateColorAsState(if (focused) TvInk else TvText, tween(FOCUS_MS), label = "roundFg")
    val grow by animateFloatAsState(if (focused) 1.08f else 1f, tween(FOCUS_MS), label = "roundGrow")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(bg)
            .clickable(
                remember { MutableInteractionSource() }, indication = null, enabled = enabled,
                role = Role.Button, onClickLabel = description, onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(size * 0.46f), tint = fg.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// one pixel is enough to tint the backdrop; computed once per artwork, never per frame
private fun averageColor(bitmap: Bitmap): Color = try {
    val px = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
    val c = Color(px)
    // keep it dark and calm whatever the artwork is
    Color(c.red * 0.6f, c.green * 0.6f, c.blue * 0.6f)
} catch (_: RuntimeException) {
    Color(0xFF2A2A2E)
}
