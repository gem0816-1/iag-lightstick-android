package com.iag.lightstick

import dev.amenhancer.glass.LiquidMotion

import android.media.AudioManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Apple Music full player (music_player.xml / constraint_player_media_controls.xml):
 * 24dp artwork margins with a 1:1 card (10dp radius, 15dp shadow), 20sp medium title with a
 * 20sp 45%-white subtitle, a 32dp-inset scrubber, a 66dp play button flanked by 68x61dp skip
 * buttons 40dp apart, and a 38dp bottom action row. The vibrant field is pre-blended once so no
 * per-frame blur is needed.
 */
@Suppress("ModifierParameter")
@Composable
private fun PlayerText(text: String, size: TextUnit, weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White, alpha: Float = 1f, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(text, modifier,
        style = TextStyle(fontSize = size, fontWeight = weight, color = color.copy(alpha = alpha)),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PlayerTransport(forward: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(68.dp, 61.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        GlassGlyph(ImageVector.vectorResource(if (forward) R.drawable.am_player_next else R.drawable.am_player_previous),
            if (forward) "前进15秒" else "后退15秒", Modifier.size(46.dp, 39.dp), Color.White.copy(alpha = 0.96f))
    }
}

@Composable
private fun PlayerFootAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.semantics { contentDescription = description }.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlassGlyph(icon, null, Modifier.size(26.dp), Color.White.copy(alpha = 0.92f))
    }
}

private fun amTrackTime(timeMs: Long): String {
    val total = (timeMs.coerceAtLeast(0L) / 1000L)
    val minutes = total / 60L
    val seconds = total % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** AM++'s Apple Music host: vibrant artwork field, title, scrubber, transport and bottom actions. */
@Composable
fun AmExpandedPlayer(projectName: String, colorHex: String,
    project: LightingArrangement, position: () -> Long, playing: Boolean,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit, onStop: () -> Unit, onOpenVideo: () -> Unit, onDismiss: () -> Unit) {
    val led = remember(colorHex) { runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray) }
    val dark = LocalAmColors.current.dark
    // A pre-blended vibrant field reproduces the host's artwork background without a blur pass.
    val top = remember(led, dark) { lerp(if (dark) Color(0xFF292A33) else Color(0xFF454651), led, 0.28f) }
    val bottom = remember(led, dark) { lerp(if (dark) Color(0xFF17181E) else Color(0xFF292A33), led, 0.16f) }
    val field = remember(top, bottom) { Brush.verticalGradient(listOf(top, bottom)) }
    var showTimeline by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    BackHandler(showActions || showTimeline || showVolume) {
        if (showVolume) showVolume = false else if (showActions) showActions = false else showTimeline = false
    }
    val drag = LocalPlayerMotion.current
    val dragModifier = Modifier.draggable(
        rememberDraggableState { delta -> drag?.dragBy(delta) },
        Orientation.Vertical,
        onDragStopped = { velocity -> drag?.end(velocity) { onDismiss() } },
    )
    val coverScale by animateFloatAsState(if (playing) 1f else 0.93f, LiquidMotion.artwork(), label = "playerCover")
    val density = LocalDensity.current
    val backdrop = rememberCanvasBackdrop { drawRect(field) }
    BoxWithConstraints(Modifier.fillMaxSize().background(field)) {
        val cover = PlayerMotion.cover(maxWidth.value, maxHeight.value, density.fontScale).dp
        val compactViewport = maxHeight < 600.dp || density.fontScale > 1.3f
        val viewportScroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().graphicsLayer {
                alpha = drag?.let { PlayerMotion.controlsAlpha(it.progress) } ?: 1f
            }
                .then(if (compactViewport) Modifier.verticalScroll(viewportScroll) else Modifier),
        ) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(dragModifier), contentAlignment = Alignment.Center) {
                Box(Modifier.size(cover).onGloballyPositioned { coordinates ->
                    if (drag != null) {
                        val raw = coordinates.positionInRoot() - Offset(0f, drag.offset)
                        val topLeft = Offset(raw.x.roundToInt().toFloat(), raw.y.roundToInt().toFloat())
                        drag.artworkTarget = androidx.compose.ui.geometry.Rect(topLeft,
                            Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
                    }
                }) {
                AmPlayerArtwork(
                    colorHex,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = coverScale; scaleY = coverScale
                        alpha = if (drag?.sharedArtwork == true && drag.progress < 1f) 0f else 1f
                    }
                        .shadow(15.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    inset = 16.dp,
                )
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).then(dragModifier), verticalAlignment = Alignment.CenterVertically) {
                PlayerText(projectName, 20.sp, FontWeight.Medium, Color.White, 0.94f,
                    Modifier.weight(1f), maxLines = 1)
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { showActions = true }, contentAlignment = Alignment.Center) {
                    GlassGlyph(ImageVector.vectorResource(R.drawable.am_player_more), "更多播放选项", Modifier.size(28.dp), Color.White.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.height(18.dp))
            PlayerProgress(position, project.durationMs, onSeek)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                PlayerTransport(forward = false) { onSeek((position() - 15_000L).coerceAtLeast(0L)) }
                Spacer(Modifier.width(40.dp))
                Box(Modifier.size(66.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                    GlassGlyph(ImageVector.vectorResource(if (playing) R.drawable.am_player_pause else R.drawable.am_player_play),
                        if (playing) "暂停" else "播放", Modifier.size(44.dp), Color.White.copy(alpha = 0.96f))
                }
                Spacer(Modifier.width(40.dp))
                PlayerTransport(forward = true) { onSeek((position() + 15_000L).coerceAtMost(project.durationMs)) }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 18.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerFootAction(Icons.Outlined.OndemandVideo, "打开关联视频", onOpenVideo)
                PlayerFootAction(Icons.Outlined.PowerSettingsNew, "停止并收起", onStop)
                PlayerFootAction(Icons.AutoMirrored.Outlined.QueueMusic, "灯光时间轴") { showTimeline = !showTimeline }
            }
            AnimatedVisibility(showTimeline, enter = fadeIn(LiquidMotion.enter()), exit = fadeOut(LiquidMotion.exit())) {
                PlayerTimeline(project, position, onSeek)
            }
            Spacer(Modifier.height(12.dp))
        }
        AmActionSheet(backdrop, showActions, actions = listOf(
            AmAction("媒体音量") { showActions = false; showVolume = true },
            AmAction("打开关联视频") { showActions = false; onOpenVideo() },
            AmAction("停止并收起", destructive = true) { showActions = false; onStop() },
        ), onDismiss = { showActions = false })
        AnimatedVisibility(showVolume, enter = fadeIn(LiquidMotion.enter()), exit = fadeOut(LiquidMotion.exit()), modifier = Modifier.align(Alignment.BottomCenter)) {
            ReadableGlass(backdrop, Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassLabel("媒体音量", Modifier.weight(1f), color = LocalAmColors.current.ink)
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { showVolume = false }, contentAlignment = Alignment.Center) {
                            GlassGlyph(Icons.Outlined.Close, "关闭", Modifier.size(22.dp), LocalAmColors.current.ink)
                        }
                    }
                    PlayerVolume(LocalAmColors.current.ink)
                }
            }
        }
    }
}

@Composable
private fun PlayerProgress(position: () -> Long, duration: Long, onSeek: (Long) -> Unit) {
    val value = position()
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        AmScrubber(value, duration, onSeek, Modifier.fillMaxWidth(),
            Color.White.copy(alpha = 0.94f), trackTint = Color.White.copy(alpha = 0.18f), showThumb = false)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            PlayerText(amTrackTime(value), 12.sp, FontWeight.Medium, Color.White, 0.45f, Modifier.weight(1f))
            PlayerText("−${amTrackTime(duration - value)}", 12.sp, FontWeight.Medium, Color.White, 0.45f)
        }
    }
}

@Composable
private fun PlayerVolume(tint: Color) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember(audio) { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    DisposableEffect(context, audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassGlyph(Icons.AutoMirrored.Outlined.VolumeDown, "音量", Modifier.size(18.dp), tint.copy(alpha = 0.65f))
        AmSlider(volume, 0..maxVolume, { next ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        }, Modifier.weight(1f).padding(horizontal = 13.dp), tint.copy(alpha = 0.8f), description = "媒体音量")
        GlassGlyph(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(21.dp), tint.copy(alpha = 0.65f))
    }
}

@Composable
private fun PlayerTimeline(project: LightingArrangement, position: () -> Long, onSeek: (Long) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
        .background(Color.White.copy(alpha = 0.08f)).padding(10.dp)) {
        AmTimeline(project, position(), onSeek)
    }
}
