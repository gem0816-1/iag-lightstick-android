package com.iag.lightstick

import dev.amenhancer.glass.LiquidMotion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import dev.amenhancer.backdrop.VibrantBlur

private val MenuInk: Color @Composable get() = LocalAmColors.current.ink
private val MenuBlue = Color(0xFF0088FF)

@Composable
internal fun GlassLabel(text: String, modifier: Modifier = Modifier, size: Int = 16, bold: Boolean = false, color: Color = MenuInk) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun GlassGlyph(icon: ImageVector, description: String?, modifier: Modifier = Modifier, color: Color = MenuInk) {
    Image(icon, description, modifier, colorFilter = ColorFilter.tint(color))
}

/** The text stays opaque; the material behind it has a fixed readability floor. */
@Composable
internal fun ReadableGlass(backdrop: Backdrop, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settings = LocalGlassSettings.current
    val radius = with(LocalDensity.current) { settings.blurDp.dp.toPx() }
    val blur = remember(radius) { VibrantBlur(radius) }
    val panel = LocalAmColors.current.panel
    val shape = RoundedCornerShape(24.dp)
    val material = if (settings.enabled) Modifier.drawBackdrop(backdrop, shape = { shape },
        effects = { blur.applyTo(this) }, highlight = null, shadow = null,
        clipCompositingStrategy = CompositingStrategy.Auto,
        onDrawSurface = { drawRect(panel.copy(alpha = 0.94f)) })
    else Modifier.clip(shape).background(panel)
    Box(modifier.then(material), content = content)
}

@Composable
private fun MenuDivider() = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(LocalAmColors.current.separator))

@Composable
private fun MenuAction(title: String, icon: ImageVector, onClick: () -> Unit, trailing: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassGlyph(icon, null, Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        GlassLabel(title, Modifier.weight(1f))
        if (trailing) GlassGlyph(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), LocalAmColors.current.panelSecondary)
    }
}

@Composable
private fun GlassToggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val fraction by animateFloatAsState(if (value) 1f else 0f, LiquidMotion.position(), label = "glassSwitch")
    Row(Modifier.fillMaxWidth().toggleable(value, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassLabel(title, Modifier.weight(1f))
        Box(Modifier.width(50.dp).height(30.dp).clip(CircleShape)
            .background(if (value) MenuBlue else LocalAmColors.current.track)) {
            Box(Modifier.padding(3.dp).offset { IntOffset((20.dp.toPx() * fraction).roundToInt(), 0) }
                .size(24.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun AmMoreOptions(backdrop: Backdrop, visible: Boolean, settings: GlassSettings,
    onSettings: (GlassSettings) -> Unit, onGlassSettings: () -> Unit,
    onSync: () -> Unit, onHelp: () -> Unit, onAbout: () -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(LiquidMotion.enter()), exit = fadeOut(LiquidMotion.exit())) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss)) {
            ReadableGlass(backdrop, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 16.dp)
                .widthIn(max = 300.dp).fillMaxWidth(0.78f)
                .animateEnterExit(enter = scaleIn(LiquidMotion.panel(), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
                    exit = scaleOut(LiquidMotion.exit(), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f)))
                .clickable(interactionSource = null, indication = null) {}) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    GlassToggle("液态玻璃", settings.enabled) { onSettings(settings.copy(enabled = it)) }
                    MenuAction("玻璃参数", Icons.Outlined.Tune, onGlassSettings, trailing = true)
                    MenuDivider()
                    MenuAction("刷新在线曲库", Icons.Outlined.Sync, onSync)
                    MenuAction("使用说明", Icons.AutoMirrored.Outlined.HelpOutline, onHelp)
                    MenuAction("关于本 App", Icons.Outlined.Info, onAbout)
                }
            }
        }
    }
}

@Composable
private fun GlassRange(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassLabel(title, Modifier.weight(1f), size = 15)
            GlassLabel("$value$unit", size = 14, color = LocalAmColors.current.panelSecondary)
        }
        AmSlider(value, range, onChange, Modifier.fillMaxWidth().height(40.dp), description = title)
    }
}

@Composable
fun AmGlassSettingsPanel(backdrop: Backdrop, visible: Boolean, settings: GlassSettings,
    onSettings: (GlassSettings) -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(LiquidMotion.enter()), exit = fadeOut(LiquidMotion.exit())) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss)) {
            ReadableGlass(backdrop, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().statusBarsPadding()
                .padding(12.dp).widthIn(max = 520.dp).fillMaxWidth()
                .animateEnterExit(enter = slideInVertically(LiquidMotion.panel()) { it / 3 },
                    exit = slideOutVertically(LiquidMotion.exit()) { it / 3 })
                .clickable(interactionSource = null, indication = null) {}) {
                Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassLabel("液态玻璃", Modifier.weight(1f), size = 22, bold = true)
                        Box(Modifier.size(44.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                            GlassGlyph(Icons.Outlined.Close, "关闭", Modifier.size(22.dp))
                        }
                    }
                    GlassToggle("液态玻璃", settings.enabled) { onSettings(settings.copy(enabled = it)) }
                    if (settings.enabled) {
                        GlassToggle("随滚动收缩", settings.collapseEnabled) { onSettings(settings.copy(collapseEnabled = it)) }
                        MenuDivider()
                        GlassRange("底部距离", settings.bottomGapDp, 0..48, " dp") { onSettings(settings.copy(bottomGapDp = it)) }
                        GlassRange("背景模糊", settings.blurDp, 0..24, " dp") { onSettings(settings.copy(blurDp = it)) }
                        if (settings.collapseEnabled) {
                            MenuDivider()
                            GlassRange("收缩回弹", settings.collapseRebound, 0..200, "%") { onSettings(settings.copy(collapseRebound = it)) }
                            GlassRange("展开回弹", settings.expandRebound, 0..200, "%") { onSettings(settings.copy(expandRebound = it)) }
                            GlassRange("迷你条回弹", settings.miniRebound, 0..200, "%") { onSettings(settings.copy(miniRebound = it)) }
                            GlassRange("迷你条下沉", settings.miniSinkDp, 0..16, " dp") { onSettings(settings.copy(miniSinkDp = it)) }
                        }
                    }
                    MenuDivider()
                    Box(Modifier.fillMaxWidth().clickable { onSettings(GlassSettings()) }.padding(17.dp), contentAlignment = Alignment.Center) {
                        GlassLabel("恢复默认", color = MenuBlue)
                    }
                }
            }
        }
    }
}
