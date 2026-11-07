package com.iag.lightstick

import dev.amenhancer.glass.LiquidMotion

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.amenhancer.backdrop.VibrantBlur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import dev.amenhancer.glass.NativeButtonInput
import dev.amenhancer.glass.NativeLiquidButton
import dev.amenhancer.glass.GlassBarCollapsePolicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * AM++（AMPLUS/AM-plus-plus）玻璃与 AM 宿主浅色界面。
 *  - 色彩 = AM 语义色：label #000、secondaryLabel 60% #3C3C43、translucent_separator、
 *    secondaryBackground #F2F2F7、pink #FA233B；强调蓝 #0088FF。
 *  - 排版 = HIG Dynamic Type：LargeTitle 34 Bold、Title2 22 Bold、Headline 17 SemiBold、
 *    Body 17、Footnote 13、Caption1 12、Caption2 11。
 *  - 保留 IAG 四页与播放页结构；底栏和迷你条使用 AM++ 原版组件及 56/43dp 几何。
 *  - 图标 = Material Icons（开源图标库），线性 Outlined 为主。
 *  - 页内普通分组表面；悬浮组件由宿主传入实际页面取样源，避免以固定彩斑模拟玻璃。
 */

private val AmBg: Color @Composable get() = LocalAmColors.current.background
private val AmInk: Color @Composable get() = LocalAmColors.current.ink
private val AmSecondary: Color @Composable get() = LocalAmColors.current.secondary
private val AmTertiary: Color @Composable get() = LocalAmColors.current.tertiary
private val AmSeparator: Color @Composable get() = LocalAmColors.current.separator
private val AmFill: Color @Composable get() = LocalAmColors.current.fill
private val AmBlue = Color(0xFF0088FF)
/** Apple Music key color: navigation selection (light #0088FF / dark #0091FF). */
private val AmKey: Color @Composable get() = LocalAmColors.current.key
private val AmRed = Color(0xFFFA233B)
private val AmGreen = Color(0xFF34C759)
private val AmTrackRest: Color @Composable get() = LocalAmColors.current.track
private val GlassNeutral: Color @Composable get() = LocalAmColors.current.glass
private val GlassPanel: Color @Composable get() = LocalAmColors.current.panel.copy(alpha = 0.94f)

private val AmLargeTitleStyle: TextStyle @Composable get() = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = AmInk)
private val AmTitle2: TextStyle @Composable get() = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AmInk)
private val AmHeadline: TextStyle @Composable get() = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AmInk)
private val AmBody: TextStyle @Composable get() = TextStyle(fontSize = 17.sp, color = AmInk)
private val AmRowTitle: TextStyle @Composable get() = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = AmInk)
private val AmFootnote: TextStyle @Composable get() = TextStyle(fontSize = 13.sp, color = AmSecondary, lineHeight = 18.sp)
private val AmCaption1: TextStyle @Composable get() = TextStyle(fontSize = 12.sp, color = AmSecondary)
private val AmCaption: TextStyle @Composable get() = TextStyle(fontSize = 11.sp, color = AmTertiary)

fun DrawScope.drawAmBackdropGlow(background: Color) { drawRect(background) }

// ---------- 基础 ----------

@Composable
private fun AmText(text: String, modifier: Modifier = Modifier, style: TextStyle = AmBody, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AmIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AmInk) {
    androidx.compose.foundation.Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
        modifier = modifier,
    )
}

// ---------- 液态玻璃表面与按钮 ----------

@Composable
private fun AmGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    tint: Color = GlassNeutral,
    blurRadius: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = LocalGlassSettings.current
    val radius = with(LocalDensity.current) { settings.blurDp.dp.toPx() }
    val material = remember(radius) { VibrantBlur(radius) }
    val surface = if (settings.enabled) Modifier.drawBackdrop(
        backdrop = backdrop, shape = { shape },
        effects = {
            material.applyTo(this)
            val refraction = minOf(24.dp.toPx(), size.minDimension * 0.375f)
            lens(refraction, refraction)
        }, highlight = null, shadow = null,
        clipCompositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto,
        onDrawSurface = { drawRect(tint) },
    ) else Modifier.clip(shape).background(tint.copy(alpha = 1f))
    Box(modifier.then(surface), content = content)
}

private fun DrawScope.drawAmTint(tint: Color?, neutral: Color) {
    if (tint != null) {
        drawRect(tint, blendMode = BlendMode.Hue)
        drawRect(tint.copy(alpha = 0.75f))
    } else {
        drawRect(neutral)
    }
}

@Composable
private fun AmGlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Capsule(),
    tint: Color? = null,
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = LocalGlassSettings.current
    val radius = with(LocalDensity.current) { settings.blurDp.dp.toPx() }
    val material = remember(radius) { VibrantBlur(radius) }
    val neutral = GlassNeutral
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.04f else 1f, LiquidMotion.button(), label = "amGlassBtnScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .then(if (settings.enabled) Modifier.drawBackdrop(
                backdrop = backdrop, shape = { shape },
                effects = {
                    material.applyTo(this)
                    val refraction = minOf(24.dp.toPx(), size.minDimension * 0.375f)
                    lens(refraction, refraction)
                },
                highlight = { Highlight.Default.copy(alpha = if (pressed) 0.7f else 0.4f) },
                shadow = null,
                clipCompositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto,
                onDrawSurface = { drawAmTint(tint, neutral) },
            ) else Modifier.clip(shape).background(tint ?: AmFill)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// ---------- 通用控件 ----------

@Composable
private fun AmCard(backdrop: Backdrop, modifier: Modifier = Modifier, corner: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(AmFill), content = content)
}

@Composable
private fun AmCircleHeaderButton(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector) {
    AmGlassButton(backdrop, onClick, modifier.size(46.dp), shape = CircleShape, description = when (icon.name.substringAfterLast('.')) {
        "MoreVert" -> "更多选项"; "PlaylistAdd" -> "导入工程"; "OndemandVideo" -> "打开关联视频"; else -> "更多操作"
    }) {
        AmIcon(icon, Modifier.size(22.dp))
    }
}

@Composable
private fun AmPillButton(
    backdrop: Backdrop,
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AmGlassButton(
        backdrop,
        onClick,
        modifier,
        shape = Capsule(),
        tint = if (primary) AmBlue else null,
        enabled = enabled,
    ) {
        AmText(
            text,
            Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
            AmBody.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (primary) Color.White else AmInk),
        )
    }
}

/** 播放大钮：当前灯色玻璃圆钮（灯灭回落中性玻璃），图标按亮度自适应黑白。 */
@Composable
private fun AmMainButton(backdrop: Backdrop, playing: Boolean, ledColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit, description: String = if (playing) "暂停" else "播放") {
    AmGlassButton(backdrop, onClick, modifier, shape = CircleShape, tint = ledColor, description = description) {
        val iconColor = if (ledColor.copy(alpha = 1f).luminance() > 0.4f) Color.Black else Color.White
        AmIcon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, Modifier.size(32.dp), tint = iconColor)
    }
}

@Composable
private fun AmRoundButton(backdrop: Backdrop, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, icon: ImageVector) {
    AmGlassButton(backdrop, onClick, modifier.semantics { this.selected = selected }, shape = CircleShape, tint = if (selected) AmBlue else null,
        description = when (icon.name.substringAfterLast('.')) {
            "Shuffle" -> "随机灯色"; "PowerSettingsNew" -> "关闭"; "Waves" -> "呼吸灯效"; "Bolt" -> "快闪灯效"
            "OndemandVideo" -> "打开关联视频"; "FastForward" -> "前进15秒"; "FastRewind" -> "后退15秒"; else -> "灯效控制"
        }) {
        AmIcon(icon, Modifier.size(22.dp), tint = if (selected) Color.White else AmInk)
    }
}

@Composable
private fun AmTextButton(text: String, color: Color = AmBlue, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AmText(text, style = AmBody.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color))
    }
}

/** 音量条样式滑条：灰轨道 + 黑色已填充段。 */
@Composable
internal fun AmSlider(value: Int, range: IntRange, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, tint: Color = AmInk, description: String = "亮度") {
    val trackColor = AmTrackRest
    val changeValue = rememberUpdatedState(onValueChange)
    val fraction = ((value - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
    Box(
        modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), (range.last - range.first - 1).coerceAtLeast(0))
                setProgress { changeValue.value(it.roundToInt().coerceIn(range)); true }
            }
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(range) {
                fun update(x: Float) {
                    val progress = (x / size.width).coerceIn(0f, 1f)
                    changeValue.value((range.first + progress * (range.last - range.first)).roundToInt())
                }
                detectTapGestures { update(it.x) }
            }
            .pointerInput(range) {
                fun update(x: Float) {
                    val progress = (x / size.width).coerceIn(0f, 1f)
                    changeValue.value((range.first + progress * (range.last - range.first)).roundToInt())
                }
                detectDragGestures(onDragStart = { update(it.x) }) { change, _ ->
                    update(change.position.x)
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = 3.dp.toPx()
            drawRoundRect(trackColor, cornerRadius = CornerRadius(r, r))
            drawRoundRect(tint, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(r, r))
        }
    }
}

@Composable
internal fun AmScrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, tint: Color = AmInk, trackTint: Color = AmTrackRest, showThumb: Boolean = true) {
    val trackColor = trackTint
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration).coerceIn(0f, 1f)
    Box(
        modifier
            .semantics {
                contentDescription = "播放进度"
                progressBarRangeInfo = ProgressBarRangeInfo(positionMs.toFloat(), 0f..safeDuration.toFloat())
                setProgress { onSeek(it.toLong().coerceIn(0L, safeDuration)); true }
            }
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(safeDuration) {
                fun seek(x: Float) = onSeek((x.coerceIn(0f, size.width.toFloat()) / size.width * safeDuration).toLong())
                detectTapGestures { seek(it.x) }
            }
            .pointerInput(safeDuration) {
                fun seek(x: Float) = onSeek((x.coerceIn(0f, size.width.toFloat()) / size.width * safeDuration).toLong())
                detectDragGestures(onDragStart = { seek(it.x) }) { change, _ ->
                    seek(change.position.x)
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = 3.dp.toPx()
            drawRoundRect(trackColor, cornerRadius = CornerRadius(r, r))
            drawRoundRect(tint, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(r, r))
            if (showThumb) drawCircle(tint, r * 1.6f, Offset(size.width * fraction, size.height / 2f))
        }
    }
}

// ---------- 骨架 ----------

@Composable
private fun AmPageHeader(backdrop: Backdrop, title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AmText(title, Modifier.padding(end = 12.dp), AmLargeTitleStyle)
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
private fun AmSectionHeader(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        AmText(text, style = AmTitle2.copy(fontSize = 22.sp))
        Spacer(Modifier.weight(1f))
        trailing?.let { AmText(it, style = AmFootnote) }
    }
}

@Composable
private fun AmMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    showSeparator: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(icon, Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            AmText(title, style = AmRowTitle)
            subtitle?.let { AmText(it, style = AmCaption1) }
        }
        trailing?.invoke(this)
    }
    if (showSeparator) {
        Box(Modifier.fillMaxWidth().padding(start = 44.dp).height(0.75.dp).background(AmSeparator))
    }
}

// ---------- 荧光棒视觉：只有头部（心形水晶灯头）发光 ----------

@Composable
fun AmStickHero(colorHex: String, modifier: Modifier = Modifier) {
    val led = amColor(colorHex)
    val bitmap = LocalPlayerArtwork.current
    Box(modifier.fillMaxWidth().height(264.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            val aspect = it.width.toFloat() / it.height.toFloat()
            val heroHeight = 244.dp
            val heroWidth = heroHeight * aspect
            // 辉光仅限头部：心形灯头中心 ≈ 棒体图片高度 30% 处
            Box(
                Modifier
                    .height(244.dp)
                    .width(heroWidth),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height * 0.30f)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(led.copy(alpha = 0.42f), led.copy(alpha = 0.14f), Color.Transparent),
                            center, size.height * 0.26f,
                        ),
                        size.height * 0.26f, center,
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                            center, size.height * 0.10f,
                        ),
                        size.height * 0.10f, center,
                    )
                }
                Image(
                    bitmap = it,
                    contentDescription = "IAG 荧光棒",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ---------- 色轮 ----------

@Composable
private fun AmColorWheel(size: Dp, selectedColor: String, onColor: (String) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap = remember(context) { BitmapFactory.decodeResource(context.resources, R.drawable.iag_color_wheel)?.asImageBitmap() }
    var marker by remember { mutableStateOf(Offset.Unspecified) }
    val sidePx = with(density) { size.toPx() }
    fun select(offset: Offset) {
        val radius = sidePx / 2f
        val dx = offset.x - radius
        val dy = offset.y - radius
        if (dx * dx + dy * dy > radius * radius) return
        marker = offset
        val image = bitmap ?: return
        val x = (offset.x / sidePx * image.width).toInt().coerceIn(0, image.width - 1)
        val y = (offset.y / sidePx * image.height).toInt().coerceIn(0, image.height - 1)
        val pixel = image.asAndroidBitmap().getPixel(x, y)
        if (AndroidColor.alpha(pixel) > 0 && (pixel and 0x00FFFFFF) != 0x00FFFFFF) {
            onColor("#%02X%02X%02X".format(AndroidColor.red(pixel), AndroidColor.green(pixel), AndroidColor.blue(pixel)))
        }
    }
    Box(
        Modifier
            .size(size)
            .pointerInput(bitmap) { detectTapGestures { select(it) } }
            .pointerInput(bitmap) {
                detectDragGestures(onDragStart = { select(it) }) { change, _ ->
                    select(change.position)
                    change.consume()
                }
            },
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        Canvas(Modifier.fillMaxSize()) {
            if (marker.x.isFinite() && marker.y.isFinite()) {
                drawCircle(Color.White, 10.dp.toPx(), marker, style = Stroke(2.5.dp.toPx()))
                drawCircle(amColor(selectedColor), 6.dp.toPx(), marker)
            }
        }
    }
}

// ---------- AM++ 底部导航 ----------

/** 使用 AM++ 原版导航组件，保留 IAG 的四个功能入口。 */
@Composable
fun AmLiquidBottomTabs(
    backdrop: Backdrop,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapseProgress: (() -> Float)? = null,
    onExpandRequest: (() -> Unit)? = null,
) {
    CoolapkBottomNavigation(backdrop, selected, onSelect, modifier, collapseProgress, onExpandRequest)
}

// ---------- 迷你播放条 ----------

private data class AmMiniTransform(val scaleX: Float = 1f, val scaleY: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/** Only observes pointer events; the row and its controls retain click ownership. */
private fun Modifier.forwardNativeButtonInput(input: NativeButtonInput): Modifier = pointerInput(input) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        input.event(MotionEvent.ACTION_DOWN, down.position.x, down.position.y)
        var released = false
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                input.event(
                    if (change.pressed) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_UP,
                    change.position.x,
                    change.position.y,
                )
                released = !change.pressed
            } while (!released)
        } finally {
            if (!released) input.event(MotionEvent.ACTION_CANCEL, 0f, 0f)
        }
    }
}

@Composable
fun AmNowPlayingBar(
    backdrop: Backdrop,
    title: String,
    subtitle: String,
    colorHex: String,
    playing: Boolean?,
    onToggle: () -> Unit,
    onForward: (() -> Unit)?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onArtworkBounds: (Rect) -> Unit = {},
) {
    val settings = LocalGlassSettings.current
    val layerSize = LocalMiniLayerSize.current
    val input = remember { NativeButtonInput() }
    val transform = remember { mutableStateOf(AmMiniTransform()) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(43.dp).then(modifier)
            .then(if (settings.enabled) Modifier.forwardNativeButtonInput(input) else Modifier)
            .then(if (!settings.enabled) Modifier.clip(Capsule()).background(AmFill) else Modifier)
            .semantics { contentDescription = "展开播放器" }
            .clickable(onClick = onOpen),
    ) {
        if (settings.enabled) NativeLiquidButton(
            backdrop = backdrop,
            input = input,
            panelBlur = settings.blurDp.dp,
            miniHeightDp = 43,
            autoClip = true,
            minLayerSize = layerSize,
            transformContent = { scaleX, scaleY, x, y ->
                transform.value = AmMiniTransform(scaleX, scaleY, x, y)
            },
        )
        Row(
            Modifier.fillMaxSize().graphicsLayer {
                val value = if (settings.enabled) transform.value else AmMiniTransform()
                scaleX = value.scaleX
                scaleY = value.scaleY
                translationX = value.x
                translationY = value.y
            }.padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).onGloballyPositioned { onArtworkBounds(it.boundsInRoot()) }, contentAlignment = Alignment.Center) {
                AmPlayerArtwork(colorHex, Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // The reference caps the mini at 1.5x; keep the full title in accessibility.
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(
                    density.density, density.fontScale.coerceAtMost(1.5f))) {
                    AmText(title, style = TextStyle(fontSize = 13.sp, lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium, color = AmInk), maxLines = 1)
                    if (subtitle.isNotBlank()) AmText(subtitle, style = TextStyle(fontSize = 12.sp,
                        lineHeight = 12.sp, color = AmSecondary), maxLines = 1)
                }
            }
            if (playing != null) {
                Box(
                    Modifier.semantics { contentDescription = if (playing == true) "暂停" else "播放" }.size(40.dp).clip(CircleShape).clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    AmIcon(ImageVector.vectorResource(if (playing) R.drawable.am_mini_pause else R.drawable.am_mini_play), Modifier.size(32.dp))
                }
                if (onForward != null) {
                    Box(
                        Modifier.semantics { contentDescription = "前进15秒" }.size(40.dp).clip(CircleShape).clickable(onClick = onForward),
                        contentAlignment = Alignment.Center,
                    ) {
                        AmIcon(ImageVector.vectorResource(R.drawable.am_mini_next), Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

// ---------- 多轨时间轴 ----------

@Composable
internal fun AmTimeline(project: LightingArrangement, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val separator = AmSeparator
    val trackColor = AmTrackRest
    val ink = AmInk
    val trackIndexes = project.tracks.mapIndexed { index, track -> track.id to index }.toMap()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerInput(project) {
                fun seekAt(x: Float) = onSeek((x.coerceIn(0f, size.width.toFloat()) / size.width * project.durationMs).toLong())
                detectTapGestures { seekAt(it.x) }
            }
            .pointerInput(project) {
                fun seekAt(x: Float) = onSeek((x.coerceIn(0f, size.width.toFloat()) / size.width * project.durationMs).toLong())
                detectDragGestures(onDragStart = { seekAt(it.x) }) { change, _ ->
                    seekAt(change.position.x)
                    change.consume()
                }
            },
    ) {
        val laneHeight = size.height / project.tracks.size.coerceAtLeast(1)
        repeat(project.tracks.size) { lane ->
            drawLine(separator, Offset(0f, lane * laneHeight), Offset(size.width, lane * laneHeight), 1.dp.toPx())
        }
        drawLine(trackColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        project.events.forEach { event ->
            val left = event.startMs.toFloat() / project.durationMs * size.width
            val right = event.endMs.toFloat() / project.durationMs * size.width
            val lane = trackIndexes[event.trackId] ?: 0
            val top = lane * laneHeight + 3.dp.toPx()
            val bottom = (lane + 1) * laneHeight - 3.dp.toPx()
            drawRoundRect(
                color = amColor(if (event.effect == ArrangementEffect.OFF) "#A9A9B0" else event.color).copy(alpha = 0.92f),
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(2.dp.toPx()), (bottom - top).coerceAtLeast(4.dp.toPx())),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        val playheadX = positionMs.coerceIn(0L, project.durationMs).toFloat() / project.durationMs * size.width
        drawLine(ink, Offset(playheadX, 0f), Offset(playheadX, size.height), 2.dp.toPx())
    }
}

// ---------- 弹层（液态玻璃） ----------

data class AmAction(val title: String, val destructive: Boolean = false, val action: () -> Unit)

/** 跟随状态点（JIHUA §1 状态卡）：只有色点 + 一个词，不加说明。 */
data class AmFollowStatus(val label: String, val color: Color)

@Composable
fun AmActionSheet(backdrop: Backdrop, visible: Boolean, title: String? = null, message: String? = null, actions: List<AmAction>, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(LiquidMotion.enter()), exit = fadeOut(LiquidMotion.exit())) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)).clickable(onClick = onDismiss)) {
            val cardInteraction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = cardInteraction, indication = null) {},
            ) {
                AmGlassSurface(backdrop, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tint = GlassPanel) {
                    Column(Modifier.fillMaxWidth()) {
                        if (title != null || message != null) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                title?.let { AmText(it, style = AmCaption1.copy(fontWeight = FontWeight.SemiBold)) }
                                message?.let {
                                    AmText(it, style = AmCaption1, align = TextAlign.Center)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(AmSeparator))
                        }
                        actions.forEach { item ->
                            Box(
                                Modifier.fillMaxWidth().clickable { item.action() }.padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AmText(
                                    item.title,
                                    style = AmBody.copy(color = if (item.destructive) AmRed else AmInk),
                                )
                            }
                            if (item !== actions.last()) Box(Modifier.fillMaxWidth().height(0.5.dp).background(AmSeparator))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                AmGlassSurface(
                    backdrop,
                    Modifier.fillMaxWidth().clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(20.dp),
                    tint = GlassPanel,
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        AmText("取消", style = AmBody.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
fun AmDialog(backdrop: Backdrop, visible: Boolean, title: String, message: String, confirmText: String, confirmDestructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(LiquidMotion.enter()), exit = fadeOut(LiquidMotion.exit())) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            val cardInteraction = remember { MutableInteractionSource() }
            AmGlassSurface(
                backdrop,
                Modifier
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = cardInteraction, indication = null) {},
                shape = RoundedCornerShape(20.dp),
                tint = GlassPanel,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AmText(title, style = AmHeadline, align = TextAlign.Center)
                        Spacer(Modifier.height(5.dp))
                        AmText(message, style = AmFootnote, align = TextAlign.Center)
                    }
                    Row(Modifier.fillMaxWidth().height(46.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                            AmText("取消", style = AmBody.copy(color = AmSecondary))
                        }
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(AmSeparator))
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onConfirm), contentAlignment = Alignment.Center) {
                            AmText(
                                confirmText,
                                style = AmBody.copy(fontWeight = FontWeight.SemiBold, color = if (confirmDestructive) AmRed else AmBlue),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- 页面：控制 ----------

@Composable
fun AmControlPage(
    backdrop: Backdrop,
    connected: Boolean,
    musicActive: Boolean,
    mode: Int,
    previewColor: String,
    baseColor: String,
    brightness: Int,
    colors: List<Triple<String, String, String>>,
    onColor: (String) -> Unit,
    onBrightness: (Int) -> Unit,
    onMode: (Int) -> Unit,
    onMainToggle: () -> Unit,
    onMenu: () -> Unit,
    onOpenDevice: () -> Unit,
    bottomPadding: () -> Dp,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        AmPageHeader(backdrop, "控制") {
            Box(
                Modifier
                    .size(34.dp)
                    .clickable(onClick = onOpenDevice),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) AmGreen else AmTertiary))
            }
            Spacer(Modifier.width(8.dp))
            AmCircleHeaderButton(backdrop, onMenu, icon = Icons.Outlined.MoreVert)
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AmStickHero(previewColor, Modifier.fillMaxWidth())
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            AmText(effectAmTitle(mode), style = AmTitle2)
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AmColorWheel(150.dp, baseColor, onColor)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            colors.forEach { (name, hex, _) ->
                val selected = baseColor.equals(hex, true)
                Box(
                    Modifier.semantics { contentDescription = name; this.selected = selected; role = Role.Button }.size(38.dp).clip(CircleShape).background(if (selected) LocalAmColors.current.selection else AmFill).clickable { onColor(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(amColor(hex)).border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmRoundButton(backdrop, mode == 2, Modifier.size(46.dp), onClick = { onMode(2) }, icon = Icons.Outlined.Shuffle)
            AmRoundButton(backdrop, mode == 0, Modifier.size(46.dp), onClick = { onMode(0) }, icon = Icons.Outlined.PowerSettingsNew)
            AmMainButton(backdrop, mode != 0, amColor(previewColor), Modifier.size(72.dp), onClick = onMainToggle, description = if (mode != 0) "关灯" else "开灯")
            AmRoundButton(backdrop, mode == 5, Modifier.size(46.dp), onClick = { onMode(5) }, icon = Icons.Outlined.Waves)
            AmRoundButton(backdrop, mode == 3, Modifier.size(46.dp), onClick = { onMode(3) }, icon = Icons.Outlined.Bolt)
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4 to "眨眼", 6 to "聚会", 7 to "七彩", 8 to "星空").forEach { (effect, label) ->
                AmGlassButton(backdrop, { onMode(effect) }, Modifier.weight(1f), shape = Capsule(), tint = if (mode == effect) AmBlue else null) {
                    AmText(label, Modifier.padding(vertical = 10.dp), AmBody.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (mode == effect) Color.White else AmInk))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AmIcon(Icons.Outlined.BrightnessLow, Modifier.size(16.dp), tint = AmTertiary)
            AmSlider(brightness, 15..100, onBrightness, Modifier.weight(1f).padding(horizontal = 10.dp))
            AmIcon(Icons.Outlined.BrightnessHigh, Modifier.size(19.dp), tint = AmSecondary)
        }
        AmBottomSpacer(bottomPadding)
    }
}

// ---------- 页面：编排 ----------

@Composable
fun AmLibraryPage(
    backdrop: Backdrop,
    projects: List<StoredArrangement>,
    selectedId: String?,
    positionMs: Long,
    playing: Boolean,
    connected: Boolean,
    previewColor: String,
    message: String?,
    followActive: Boolean,
    followStatus: AmFollowStatus?,
    followOffsetMs: Long,
    onFollowToggle: () -> Unit,
    onFollowOffset: (Long) -> Unit,
    onImport: () -> Unit,
    onSelect: (String) -> Unit,
    onRowMenu: (StoredArrangement) -> Unit,
    onOpenVideo: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    bottomPadding: () -> Dp,
) {
    val selected = projects.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        AmPageHeader(backdrop, "编排") {
            AmCircleHeaderButton(backdrop, onImport, icon = Icons.AutoMirrored.Outlined.PlaylistAdd)
        }
        message?.let {
            AmCard(backdrop, Modifier.fillMaxWidth().padding(top = 4.dp), corner = 12.dp) {
                AmText(it, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), AmFootnote.copy(color = AmBlue))
            }
            Spacer(Modifier.height(6.dp))
        }

        if (projects.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            AmCard(backdrop, Modifier.fillMaxWidth(), corner = 14.dp) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AmIcon(Icons.Outlined.LibraryMusic, Modifier.size(34.dp), tint = AmTertiary)
                    Spacer(Modifier.height(10.dp))
                    AmText("还没有灯光工程", style = AmHeadline)
                }
            }
        } else {
            projects.forEach { stored ->
                val active = stored.id == selectedId
                val project = stored.arrangement
                AmMenuRow(
                    icon = Icons.Outlined.LibraryMusic,
                    title = project.name,
                    subtitle = formatAmTime(project.durationMs),
                    isSelected = active,
                    showSeparator = stored !== projects.last(),
                    onClick = { onSelect(stored.id) },
                ) {
                    if (active) {
                        AmIcon(Icons.Outlined.Check, Modifier.size(20.dp), tint = AmBlue)
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable { onRowMenu(stored) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AmIcon(Icons.Outlined.MoreVert, Modifier.size(18.dp), tint = AmSecondary)
                    }
                }
            }

            if (selected != null) {
                val project = selected.arrangement
                Spacer(Modifier.height(16.dp))
                AmCard(backdrop, Modifier.fillMaxWidth(), corner = 18.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                AmText(project.name, style = AmTitle2.copy(fontSize = 18.sp), maxLines = 1)
                            }
                            Box(Modifier.size(26.dp).clip(CircleShape).background(amColor(previewColor)).border(1.5.dp, Color.White, CircleShape))
                        }
                        Spacer(Modifier.height(10.dp))
                        AmScrubber(positionMs, project.durationMs, onSeek)
                        Row(Modifier.fillMaxWidth()) {
                            AmText(formatAmTime(positionMs), style = AmCaption.copy(color = AmSecondary))
                            Spacer(Modifier.weight(1f))
                            AmText("-${formatAmTime(project.durationMs - positionMs)}", style = AmCaption.copy(color = AmSecondary))
                        }
                        Spacer(Modifier.height(6.dp))
                        AmTimeline(project, positionMs, onSeek)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AmGlassButton(
                                backdrop,
                                onFollowToggle,
                                Modifier.weight(1f).height(38.dp),
                                shape = Capsule(),
                                tint = if (followActive) AmBlue else null,
                            ) {
                                AmText(
                                    "跟随视频",
                                    Modifier.padding(horizontal = 10.dp),
                                    AmBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (followActive) Color.White else AmInk),
                                )
                            }
                            followStatus?.let { status ->
                                Box(Modifier.size(8.dp).clip(CircleShape).background(status.color))
                                AmText(status.label, style = AmFootnote.copy(fontSize = 12.sp))
                            }
                            if (followActive) {
                                AmGlassButton(backdrop, { onFollowOffset(-500L) }, Modifier.size(38.dp), shape = CircleShape) {
                                    AmText("−", style = AmBody.copy(fontWeight = FontWeight.Bold))
                                }
                                if (followOffsetMs != 0L) {
                                    AmText(
                                        "${if (followOffsetMs > 0) "+" else ""}${"%.1f".format(followOffsetMs / 1000.0)}s",
                                        style = AmFootnote.copy(fontSize = 12.sp),
                                    )
                                }
                                AmGlassButton(backdrop, { onFollowOffset(500L) }, Modifier.size(38.dp), shape = CircleShape) {
                                    AmText("+", style = AmBody.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AmRoundButton(backdrop, false, Modifier.size(44.dp), onClick = onOpenVideo, icon = Icons.Outlined.OndemandVideo)
                            AmRoundButton(backdrop, false, Modifier.size(44.dp), onClick = { onSeek((positionMs - 15_000L).coerceAtLeast(0L)) }, icon = Icons.Outlined.FastRewind)
                            AmMainButton(backdrop, playing, amColor(previewColor), Modifier.size(62.dp), onClick = if (playing) onPause else onPlay)
                            AmRoundButton(backdrop, false, Modifier.size(44.dp), onClick = { onSeek((positionMs + 15_000L).coerceAtMost(project.durationMs)) }, icon = Icons.Outlined.FastForward)
                            AmRoundButton(backdrop, false, Modifier.size(44.dp), onClick = onStop, icon = Icons.Outlined.PowerSettingsNew)
                        }
                    }
                }
            }
        }

        AmBottomSpacer(bottomPadding)
    }
}

// ---------- 页面：律动 ----------

@Composable
fun AmVibePage(
    backdrop: Backdrop,
    level: Int,
    brightness: Int,
    active: Boolean,
    onBrightness: (Int) -> Unit,
    onToggle: () -> Unit,
    bottomPadding: () -> Dp,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        AmPageHeader(backdrop, "律动")
        Spacer(Modifier.height(14.dp))
        AmCard(backdrop, Modifier.fillMaxWidth(), corner = 18.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                    val idle = AmTertiary
                    val trackColor = AmTrackRest
                    Canvas(Modifier.size(204.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val strength = level.coerceIn(0, 200) / 200f
                        val accent = if (active) AmBlue else idle
                        drawCircle(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.10f + strength * 0.18f), Color.Transparent),
                                center, size.width * 0.5f,
                            ),
                            size.width * 0.5f, center,
                        )
                        drawCircle(trackColor, 64.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                        drawCircle(accent.copy(alpha = 0.35f + strength * 0.5f), (52f + strength * 9f).dp.toPx(), center, style = Stroke(3.dp.toPx()))
                        val inner = 32.dp.toPx()
                        val outer = 42.dp.toPx() + strength * 26.dp.toPx()
                        repeat(40) { index ->
                            val angle = (index / 40f * 2f * PI - PI / 2f).toFloat()
                            val variable = 0.28f + 0.72f * kotlin.math.abs(sin(index * 3.71f + strength * 8f))
                            val end = inner + (outer - inner) * variable * (0.35f + strength * 0.65f)
                            drawLine(
                                color = accent.copy(alpha = if (active) 0.5f + strength * 0.5f else 0.4f),
                                start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                                end = Offset(center.x + cos(angle) * end, center.y + sin(angle) * end),
                                strokeWidth = 2.2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AmIcon(Icons.Outlined.Radio, Modifier.size(38.dp), tint = if (active) AmBlue else AmTertiary)
                        AmText(if (active) "正在聆听" else "等待播放", style = AmFootnote.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.weight(1f))
                    AmText("${level.coerceIn(0, 200) / 2}%", style = AmTitle2.copy(color = if (active) AmBlue else AmSecondary))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AmIcon(Icons.Outlined.BrightnessLow, Modifier.size(16.dp), tint = AmTertiary)
            AmSlider(brightness, 10..100, onBrightness, Modifier.weight(1f).padding(horizontal = 10.dp))
            AmIcon(Icons.Outlined.BrightnessHigh, Modifier.size(19.dp), tint = AmSecondary)
        }
        Spacer(Modifier.height(8.dp))
        AmPillButton(backdrop, if (active) "停止律动" else "开始律动", Modifier.fillMaxWidth().height(52.dp), primary = true, onClick = onToggle)
        AmBottomSpacer(bottomPadding)
    }
}

// ---------- 页面：设备 ----------

@Composable
fun AmDevicePage(
    backdrop: Backdrop,
    state: LightStickController.State,
    deviceName: String,
    devices: List<StickCandidate>,
    logs: String,
    onScan: () -> Unit,
    onConnect: (StickCandidate) -> Unit,
    onDisconnect: () -> Unit,
    bottomPadding: () -> Dp,
) {
    val connected = state == LightStickController.State.CONNECTED
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        AmPageHeader(backdrop, "设备")
        AmCard(backdrop, Modifier.fillMaxWidth(), corner = 14.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(AmFill), contentAlignment = Alignment.Center) {
                    AmIcon(Icons.Outlined.FlashlightOn, Modifier.size(24.dp), tint = if (connected) AmGreen else AmSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AmText(
                        when (state) {
                            LightStickController.State.IDLE -> "未连接"
                            LightStickController.State.SCANNING -> "正在搜索荧光棒…"
                            LightStickController.State.CONNECTING -> "正在建立连接…"
                            LightStickController.State.CONNECTED -> "已连接"
                            LightStickController.State.DISCONNECTED -> "连接已中断"
                        },
                        style = AmHeadline,
                    )
                    if (connected) {
                        AmText(deviceName.ifBlank { LightStickController.TARGET_NAME }, style = AmCaption1, maxLines = 1)
                    }
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) AmGreen else AmTertiary))
            }
        }
        Spacer(Modifier.height(14.dp))
        AmPillButton(
            backdrop,
            if (state == LightStickController.State.SCANNING) "正在搜索…" else "搜索 IAG 荧光棒",
            Modifier.fillMaxWidth().height(52.dp),
            primary = true,
            enabled = state != LightStickController.State.SCANNING,
            onClick = onScan,
        )
        Spacer(Modifier.height(8.dp))
        if (connected) {
            AmPillButton(backdrop, "断开连接", Modifier.fillMaxWidth().height(48.dp), primary = false, onClick = onDisconnect)
        }
        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            devices.forEach { candidate ->
                Row(
                    Modifier.fillMaxWidth().clickable { onConnect(candidate) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AmIcon(Icons.Outlined.Bluetooth, Modifier.size(26.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        AmText(candidate.name, style = AmRowTitle.copy(fontSize = 16.sp))
                        AmText("${candidate.address} · ${candidate.rssi} dBm${if (candidate.isKnownIag) " · IAG" else ""}", style = AmCaption1)
                    }
                    AmTextButton("连接") { onConnect(candidate) }
                }
                Box(Modifier.fillMaxWidth().padding(start = 42.dp).height(0.75.dp).background(AmSeparator))
            }
        }
        AmSectionHeader("连接记录")
        AmCard(backdrop, Modifier.fillMaxWidth(), corner = 12.dp) {
            AmText(logs, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), AmFootnote.copy(fontSize = 11.sp, lineHeight = 16.sp))
        }
        AmBottomSpacer(bottomPadding)
    }
}

// ---------- 工具 ----------

private fun amColor(hex: String): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.Black)

fun formatAmTime(timeMs: Long): String {
    val seconds = timeMs.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

/** 官方灯效名（HCI 实采按钮组）：黑场/常亮/随机/快闪/眨眼/呼吸/聚会/七彩/星空。 */
fun effectAmTitle(mode: Int): String = when (mode) {
    0 -> "黑场"
    1 -> "常亮"
    2 -> "随机"
    3 -> "快闪"
    4 -> "眨眼"
    5 -> "呼吸"
    6 -> "聚会"
    7 -> "七彩"
    8 -> "星空"
    99 -> "音乐律动"
    else -> "常亮"
}

@Composable
internal fun AmBottomSpacer(padding: () -> Dp) {
    Spacer(Modifier.fillMaxWidth().layout { _, constraints ->
        layout(constraints.maxWidth, padding().roundToPx().coerceAtLeast(0)) {}
    })
}
