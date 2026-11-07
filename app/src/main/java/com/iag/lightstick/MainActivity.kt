package com.iag.lightstick

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipData
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.amenhancer.glass.BottomScrim
import dev.amenhancer.glass.GlassPolicy
import dev.amenhancer.glass.GlassBarCollapsePolicy
import dev.amenhancer.glass.GlassBarCollapseSpec
import dev.amenhancer.glass.GlassBarExpandSpec
import dev.amenhancer.glass.GlassScrollCollapseAccumulator
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private lateinit var controller: LightStickController

    private var activeTab by mutableStateOf(0)
    private var deviceState by mutableStateOf(LightStickController.State.IDLE)
    private var deviceName by mutableStateOf("")
    private var discoveredDevices by mutableStateOf<List<StickCandidate>>(emptyList())
    private var previewColor by mutableStateOf("#34CFFF")
    private var selectedBaseColor by mutableStateOf("#34CFFF")
    private var currentMode by mutableStateOf(1)
    private var brightness by mutableStateOf(100)
    private var musicBrightness by mutableStateOf(100)
    private var musicActive by mutableStateOf(false)
    private var latestMusicLevel by mutableStateOf(0)
    private var logText by mutableStateOf("等待操作…")
    private var showHelp by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showMenu by mutableStateOf(false)
    private var showGlassSettings by mutableStateOf(false)
    private var glassSettings by mutableStateOf(GlassSettings())
    private var showFollowDialog by mutableStateOf(false)
    private var nowPlayingExpanded by mutableStateOf(false)
    private var rowMenu by mutableStateOf<StoredArrangement?>(null)
    private var confirmDeleteId by mutableStateOf<String?>(null)
    private var arrangementMessage by mutableStateOf<String?>(null)
    private lateinit var arrangementStorage: ArrangementStorage
    private lateinit var importArrangementLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exportArrangementLauncher: ActivityResultLauncher<String>
    private var pendingExportId: String? = null
    private data class PendingImport(val uri: Uri, val requestId: String)
    private val pendingImports = ArrayDeque<PendingImport>()
    private var incomingImportJob: Job? = null
    private val resolveOutput = ResolveOutputGate()
    private var arrangements by mutableStateOf<List<StoredArrangement>>(emptyList())
    private var selectedArrangementId by mutableStateOf<String?>(null)
    private var arrangementPositionMs by mutableStateOf(0L)
    private var arrangementPlaying by mutableStateOf(false)
    private var arrangementEffectLabel by mutableStateOf<String?>(null)
    private var arrangementAnchorPositionMs = 0L
    private var arrangementAnchorElapsedMs = 0L
    private var activeArrangementEventKey: String? = null
    private var followActive by mutableStateOf(false)
    private var followState by mutableStateOf<FollowState?>(null)
    private var followManual by mutableStateOf(false)
    private var followOffsetMs by mutableStateOf(0L)
    private var followEngine: FollowEngine? = null
    private var pendingFollowRefId = ""
    private var pendingManualFollow = false
    private var recordingRefId by mutableStateOf<String?>(null)
    private var pendingRecordRefId = ""

    private val uiHandler = Handler(Looper.getMainLooper())
    private val effectHandler = Handler(Looper.getMainLooper())
    private val musicHandler = Handler(Looper.getMainLooper())
    private val logLines = ArrayDeque<String>()
    private var pendingPermissionAction: (() -> Unit)? = null
    private var bluetoothActionAfterEnable: (() -> Unit)? = null
    private var refreshJob: Job? = null
    private var documentJob: Job? = null
    private var libraryJob: Job? = null
    private var captureSessionId: String? = null
    private var captureAuthorizationPending = false
    private var pendingCaptureMode = CaptureService.MODE_MUSIC
    private var effectTick = 0
    private var musicPaletteIndex = -1

    private val arrangementTicker = object : Runnable {
        override fun run() {
            if (!arrangementPlaying) return
            if (followActive) return
            val project = selectedArrangement() ?: run {
                pauseArrangement()
                return
            }
            val position = arrangementAnchorPositionMs + (SystemClock.elapsedRealtime() - arrangementAnchorElapsedMs)
            if (position >= project.durationMs) {
                arrangementPositionMs = project.durationMs
                arrangementPlaying = false
                stopArrangementForegroundService()
                activeArrangementEventKey = null
                renderArrangementPosition(project, project.durationMs, forceOutput = true)
                return
            }
            arrangementPositionMs = position
            renderArrangementPosition(project, position)
            uiHandler.postDelayed(this, selectedStored()?.resolve?.nextDelayMs(position) ?: ARRANGEMENT_TICK_MS)
        }
    }

    private val musicPalette = listOf(
        Triple(255, 0, 0), Triple(0, 255, 0), Triple(0, 0, 255),
        Triple(255, 0, 63), Triple(160, 255, 0), Triple(0, 135, 194),
        Triple(255, 13, 83), Triple(60, 255, 20), Triple(30, 90, 255),
        Triple(255, 56, 0), Triple(26, 255, 58), Triple(174, 64, 255),
        Triple(255, 204, 0), Triple(0, 255, 80), Triple(203, 0, 255),
    )
    private val colors = listOf(
        Triple("冰蓝", "#34CFFF", "ICE"),
        Triple("粉色", "#FF3D8D", "PINK"),
        Triple("紫色", "#9B6BFF", "VIOLET"),
        Triple("绿色", "#28E29B", "GREEN"),
        Triple("金色", "#FFC857", "GOLD"),
        Triple("白色", "#F4F6FF", "WHITE"),
    )

    /** 官方七彩循环色表（palette.vue rainbowColors，1:1）。 */
    private val rainbowColors = listOf("FF0000", "FF7F00", "FFFF00", "00FF00", "00FFFF", "0000FF", "8B00FF")

    private val effectTicker = object : Runnable {
        override fun run() {
            if (currentMode != 2 && currentMode !in 6..8) return
            val base = scaleColor(selectedBaseColor, brightness / 100f)
            val color = renderEffect(currentMode, effectTick++, base)
            setCurrentPreviewColor(color)
            if (::controller.isInitialized && controller.state == LightStickController.State.CONNECTED) {
                controller.updateKeepAliveColor(colorComponent(color, 0), colorComponent(color, 2), colorComponent(color, 4))
            }
            // 分模式周期：随机/七彩 = 官方 loop_interval 500ms，聚会 = 快速循环，星空 = 闪烁间隔
            val period = when (currentMode) {
                2 -> 500L
                6 -> 150L
                7 -> 500L
                8 -> 300L
                else -> 100L
            }
            effectHandler.postDelayed(this, period)
        }
    }

    private fun renderMusicFrame() {
        if (!musicActive || musicPaletteIndex !in musicPalette.indices) return
        val (red, green, blue) = musicPalette[musicPaletteIndex]
        val gainSteps = latestMusicLevel.coerceIn(0, 200) * 120 / 200
        val gain = gainSteps / 120f * musicBrightness / 100f
        val rgb = Triple((red * gain).toInt(), (green * gain).toInt(), (blue * gain).toInt())
        controller.updateKeepAliveColor(rgb.first, rgb.second, rgb.third)
        setCurrentPreviewColor("#%02X%02X%02X".format(rgb.first, rgb.second, rgb.third))
    }

    private val musicColorTicker = object : Runnable {
        override fun run() {
            if (!musicActive) return
            musicPaletteIndex = (musicPaletteIndex + 1) % musicPalette.size
            renderMusicFrame()
            // The palette retains its original 400ms cadence. Levels render on arrival.
            musicHandler.postDelayed(this, 400L)
        }
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sessionId = intent?.getStringExtra(CaptureService.EXTRA_SESSION_ID)
            if (captureSessionId == null || sessionId != captureSessionId) return
            val position = intent?.getLongExtra(CaptureService.EXTRA_POSITION_MS, Long.MIN_VALUE) ?: Long.MIN_VALUE
            if (position != Long.MIN_VALUE) {
                followEngine?.onSample(position)
                return
            }
            val level = intent?.getIntExtra(CaptureService.EXTRA_LEVEL, -1) ?: -1
            if (level >= 0) {
                latestMusicLevel = level.coerceIn(0, 200)
                renderMusicFrame()
                followEngine?.onLevel(level)
                return
            }
            val message = intent?.getStringExtra(CaptureService.EXTRA_MSG) ?: return
            log(message)
            if (message.contains("参考录制完成")) {
                recordingRefId = null
                return
            }
            if (message == "音频跟随已停止" && followActive) {
                stopFollowCapture()
                return
            }
            if (message.startsWith("捕获失败") || message.startsWith("❌") || message == "系统播放音频调光已停止") {
                recordingRefId = null
                if (followActive) stopFollowCapture() else stopMusicCapture(false)
            }
        }
    }

    private val realignReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ArrangementPlaybackService.ACTION_STATUS) {
                intent.getStringExtra(ArrangementPlaybackService.EXTRA_ERROR)?.let(::log)
                return
            }
            followEngine?.forceRelock()
            log("跟随：等待重新咬合…")
        }
    }

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Follow system appearance; the host and AM++ glass observe the same configuration.
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        super.onCreate(savedInstanceState)
        glassSettings = GlassSettings.load(this)
        savedInstanceState?.let {
            activeTab = it.getInt("tab", 0).coerceIn(0, 3)
            selectedArrangementId = it.getString("projectId")
            arrangementPositionMs = it.getLong("position", 0L).coerceAtLeast(0L)
            selectedBaseColor = it.getString("baseColor")?.takeIf { color -> color.matches(Regex("#[0-9a-fA-F]{6}")) } ?: selectedBaseColor
            brightness = it.getInt("brightness", 100).coerceIn(15, 100)
            pendingCaptureMode = it.getString("captureMode") ?: CaptureService.MODE_MUSIC
            pendingFollowRefId = it.getString("followRef").orEmpty()
            pendingRecordRefId = it.getString("recordRef").orEmpty()
            pendingManualFollow = it.getBoolean("manualFollow", false)
            captureAuthorizationPending = it.getBoolean("capturePending", false)
            pendingExportId = it.getString("exportId")
            val uris = it.getStringArrayList("incomingUris").orEmpty()
            val requests = it.getStringArrayList("incomingIds").orEmpty()
            uris.zip(requests).take(10).forEach { (uri, request) ->
                val parsed = Uri.parse(uri)
                if (parsed.scheme == "content" && request.matches(Regex("[0-9a-fA-F-]{36}"))) pendingImports.addLast(PendingImport(parsed, request))
            }
        }
        arrangementStorage = ArrangementStorage(applicationContext)
        refreshArrangements()
        importArrangementLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importArrangement(uri)
        }
        exportArrangementLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val targetId = pendingExportId
            pendingExportId = null
            if (uri != null) exportSelectedArrangement(uri, targetId)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val lightBars = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }

        controller = LightStickController(
            applicationContext,
            ::log,
            { state, name ->
                runOnUiThread {
                    deviceState = state
                    deviceName = name
                    if (state == LightStickController.State.CONNECTED && arrangementPlaying) {
                        uiHandler.post {
                            startArrangementForegroundService()
                            selectedArrangement()?.let { renderArrangementPosition(it, arrangementPositionMs, forceOutput = true) }
                        }
                    } else if (state == LightStickController.State.CONNECTED && currentMode in 0..8 && currentMode != 1) {
                        uiHandler.post {
                            if (controller.state == LightStickController.State.CONNECTED && !musicActive) applyEffect(currentMode)
                        }
                    }
                    if (state == LightStickController.State.DISCONNECTED && musicActive) stopMusicCapture(false)
                }
            },
            { devices -> runOnUiThread { discoveredDevices = devices } },
        )

        ContextCompat.registerReceiver(this, captureReceiver, IntentFilter(CaptureService.ACTION_RESULT), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, realignReceiver, IntentFilter(ArrangementPlaybackService.ACTION_REALIGN).apply {
            addAction(ArrangementPlaybackService.ACTION_STATUS)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (savedInstanceState == null) requestLibrarySync()

        setContent {
            LaunchedEffect(glassSettings) {
                delay(250)
                glassSettings.save(applicationContext)
            }
            AmTheme { CompositionLocalProvider(LocalGlassSettings provides glassSettings) { AppContent() } }
        }
        log("IAG 光棒 App 已启动")
        if (savedInstanceState == null) acceptSharedArrangement(intent) else processIncomingImports()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedArrangement(intent)
    }

    @Suppress("DEPRECATION")
    private fun acceptSharedArrangement(source: Intent) {
        if (source.action != Intent.ACTION_VIEW && source.action != Intent.ACTION_SEND) return
        val uri = runCatching {
            if (source.action == Intent.ACTION_SEND) {
                (if (Build.VERSION.SDK_INT >= 33) source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                    ?: source.data ?: source.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            } else source.data
        }.getOrNull()
        if (uri?.scheme != "content") { log("请选择本机编排文件，暂不支持链接导入"); return }
        if (pendingImports.size >= 10) { log("待导入文件较多，请稍候再分享"); return }
        pendingImports.addLast(PendingImport(uri, UUID.randomUUID().toString()))
        processIncomingImports()
    }

    private fun processIncomingImports() {
        if (incomingImportJob?.isActive == true || pendingImports.isEmpty()) return
        incomingImportJob = lifecycleScope.launch {
            while (pendingImports.isNotEmpty()) {
                documentJob?.join()
                val request = pendingImports.first()
                importArrangement(request.uri, request.requestId)?.join()
                pendingImports.removeFirst()
            }
        }
    }

    @Composable
    private fun AppContent() {
        val palette = LocalAmColors.current
        LaunchedEffect(nowPlayingExpanded, palette.dark) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !nowPlayingExpanded && !palette.dark
                isAppearanceLightNavigationBars = !nowPlayingExpanded && !palette.dark
            }
        }
        var playerCovered by remember { mutableStateOf(false) }
        var miniBounds by remember { mutableStateOf(Rect.Zero) }
        val miniOrigin = remember { { miniBounds } }
        var miniArtworkBounds by remember { mutableStateOf(Rect.Zero) }
        val artworkOrigin = remember { { miniArtworkBounds } }
        val playerMotion = rememberAmPlayerMotion()
        val playerExpansion = remember(playerMotion) { { playerMotion.progress } }
        val selectedProject = selectedArrangement()
        val artworkColor = remember(selectedProject, selectedBaseColor) {
            selectedProject?.events?.firstOrNull { it.effect != ArrangementEffect.OFF }?.color ?: selectedBaseColor
        }
        val pageState = rememberSaveableStateHolder()
        val connected = deviceState == LightStickController.State.CONNECTED
        val hasProject = selectedProject != null
        val barVisible = connected || hasProject || musicActive
        val density = LocalDensity.current
        val windowSize = LocalWindowInfo.current.containerSize
        val collapseMotion = remember { Animatable(0f) }
        var dockCollapsed by remember(activeTab, windowSize) { mutableStateOf(false) }
        val collapseProgress = remember(glassSettings.enabled, glassSettings.collapseEnabled) {
            { if (glassSettings.enabled && glassSettings.collapseEnabled) collapseMotion.value else 0f }
        }
        val positionProvider = remember { { arrangementPositionMs } }
        val scrollIntent = remember(density, activeTab, windowSize) {
            GlassScrollCollapseAccumulator(with(density) { 6.dp.toPx() })
        }
        val pageScroll = remember(scrollIntent) {
            object : NestedScrollConnection {
                private var fractionalTravel = 0f

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // Only committed page travel drives the dock. Sliders and dock gestures
                    // never enter this nested-scroll subtree; layout/inset changes are ignored.
                    if (glassSettings.enabled && glassSettings.collapseEnabled && source == NestedScrollSource.UserInput && consumed.y.isFinite()) {
                        fractionalTravel += consumed.y
                        val delta = fractionalTravel.toInt()
                        fractionalTravel -= delta
                        scrollIntent.accumulate(delta)?.let { dockCollapsed = it }
                    }
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(dockCollapsed, glassSettings.enabled, glassSettings.collapseEnabled) {
            if (!glassSettings.enabled || !glassSettings.collapseEnabled) {
                dockCollapsed = false
                scrollIntent.reset()
            }
            collapseMotion.animateTo(if (dockCollapsed) 1f else 0f,
                if (dockCollapsed) GlassBarCollapseSpec else GlassBarExpandSpec)
        }
        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val expandedStack = GlassPolicy.NAV_HEIGHT_DP +
            if (barVisible) GlassPolicy.MINI_HEIGHT_DP + GlassPolicy.GAP_DP else 0
        // Read the spring in child measurement, not in the root composition.
        val bottomPadding = remember(expandedStack, navigationInset, glassSettings.bottomGapDp, collapseProgress) {
            { (GlassBarCollapsePolicy.lerp(expandedStack.toFloat(), GlassPolicy.MINI_HEIGHT_DP.toFloat(),
                GlassBarCollapsePolicy.progress(collapseProgress())) + glassSettings.bottomGapDp).dp + navigationInset + 16.dp }
        }
        // Inline controls sample the base scene; floating glass samples the recorded page.
        // Keeping these separate avoids a component recording and sampling itself.
        val backdrop = rememberCanvasBackdrop { drawAmBackdropGlow(palette.background) }
        val pageBackdrop = rememberLayerBackdrop()
        BackHandler(
            enabled = showGlassSettings || nowPlayingExpanded || showMenu || rowMenu != null || confirmDeleteId != null || showHelp || showSettings || showFollowDialog,
        ) {
            when {
                showGlassSettings -> showGlassSettings = false
                nowPlayingExpanded -> nowPlayingExpanded = false
                showMenu -> showMenu = false
                rowMenu != null -> rowMenu = null
                confirmDeleteId != null -> confirmDeleteId = null
                showFollowDialog -> showFollowDialog = false
                showHelp -> showHelp = false
                showSettings -> showSettings = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            // Keep page composition/scroll state alive while skipping all covered glass drawing.
            Box(Modifier.fillMaxSize().drawWithContent {
                if (!playerCovered || !nowPlayingExpanded) drawContent()
            }) {
            pageState.SaveableStateProvider(activeTab) {
            Box(Modifier.fillMaxSize().then(if (glassSettings.enabled) Modifier.layerBackdrop(pageBackdrop) else Modifier).nestedScroll(pageScroll)) {
                // 可见背景层（与玻璃取样源同源绘制）
                Canvas(Modifier.fillMaxSize()) { drawAmBackdropGlow(palette.background) }
                // 内容层：玻璃组件内嵌其中，采样上方背景
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    when (activeTab) {
                        0 -> AmControlPage(
                            backdrop = backdrop,
                            connected = connected,
                            musicActive = musicActive,
                            mode = currentMode,
                            previewColor = if (playerCovered) artworkColor else previewColor,
                            baseColor = selectedBaseColor,
                            brightness = brightness,
                            colors = colors,
                            onColor = ::setBaseColor,
                            onBrightness = ::updateColorBrightness,
                            onMode = ::chooseEffect,
                            onMainToggle = ::onMainToggle,
                            onMenu = { showMenu = true },
                            onOpenDevice = { activeTab = 3 },
                            bottomPadding = bottomPadding,
                        )
                        1 -> AmLibraryPage(
                            backdrop = backdrop,
                            projects = arrangements,
                            selectedId = selectedArrangementId,
                            positionMs = if (playerCovered) 0L else arrangementPositionMs,
                            playing = arrangementPlaying,
                            connected = connected,
                            previewColor = if (playerCovered) artworkColor else previewColor,
                            message = arrangementMessage,
                            followActive = followActive,
                            followStatus = followStatusUi(followState, followManual),
                            followOffsetMs = followOffsetMs,
                            onFollowToggle = ::onFollowToggle,
                            onFollowOffset = { delta -> followEngine?.let { followOffsetMs = it.adjustOffset(delta) } },
                            onImport = { importArrangementLauncher.launch(arrayOf("*/*")) },
                            onSelect = ::selectArrangement,
                            onRowMenu = { rowMenu = it },
                            onOpenVideo = ::openArrangementVideo,
                            onPlay = ::playArrangement,
                            onPause = ::pauseArrangement,
                            onStop = ::stopArrangement,
                            onSeek = ::seekArrangement,
                            bottomPadding = bottomPadding,
                        )
                        2 -> AmVibePage(
                            backdrop = backdrop,
                            level = if (playerCovered) 0 else latestMusicLevel,
                            brightness = musicBrightness,
                            active = musicActive,
                            onBrightness = { musicBrightness = it },
                            onToggle = { if (musicActive) stopMusicCapture(true) else startMusicCapture() },
                            bottomPadding = bottomPadding,
                        )
                        else -> AmDevicePage(
                            backdrop = backdrop,
                            state = deviceState,
                            deviceName = deviceName,
                            devices = discoveredDevices,
                            logs = logText,
                            onScan = ::searchDevices,
                            onConnect = ::connect,
                            onDisconnect = ::disconnectDevice,
                            bottomPadding = bottomPadding,
                        )
                    }
                }
            }

            // AM++ smoothstep fade: blur the real content only behind the bottom controls.
            if (glassSettings.enabled) AmDockScrim(pageBackdrop, bottomPadding, Modifier.align(Alignment.BottomCenter))

            // AM++ host geometry: 43dp mini + 8dp gap + 56dp tabs, 16dp margins.
            AmGlassDock(
                backdrop = pageBackdrop,
                selected = activeTab,
                onSelect = { activeTab = it; dockCollapsed = false; scrollIntent.reset() },
                miniVisible = barVisible,
                collapseProgress = collapseProgress,
                playerExpansion = playerExpansion,
                onExpandRequest = { dockCollapsed = false; scrollIntent.reset() },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                if (barVisible) {
                    AmNowPlayingBar(
                        backdrop = pageBackdrop,
                        title = when {
                            hasProject -> selectedProject.name
                            musicActive -> "音乐律动"
                            else -> effectAmTitle(currentMode)
                        },
                        subtitle = if (hasProject) arrangementEffectLabel.orEmpty() else "",
                        colorHex = artworkColor,
                        modifier = Modifier.onGloballyPositioned { miniBounds = it.boundsInRoot() },
                        onArtworkBounds = { miniArtworkBounds = it },
                        playing = if (hasProject) arrangementPlaying else null,
                        onToggle = { if (arrangementPlaying) pauseArrangement() else playArrangement() },
                        onForward = if (hasProject) {
                            { seekArrangement((arrangementPositionMs + 15_000L).coerceAtMost(selectedProject.durationMs)) }
                        } else null,
                        onOpen = { if (hasProject) nowPlayingExpanded = true },
                    )
                }
            }

            }
            }
            AmPlayerSheet(visible = nowPlayingExpanded && hasProject, origin = miniOrigin,
                artworkOrigin = artworkOrigin, motion = playerMotion, colorHex = artworkColor,
                playing = arrangementPlaying,
                onCovered = { playerCovered = it }) {
                if (selectedProject != null) {
                    AmExpandedPlayer(
                        projectName = selectedProject.name,
                        colorHex = artworkColor,
                        project = selectedProject,
                        position = positionProvider,
                        playing = arrangementPlaying,
                        onSeek = ::seekArrangement,
                        onToggle = { if (arrangementPlaying) pauseArrangement() else playArrangement() },
                        onStop = {
                            stopArrangement()
                            nowPlayingExpanded = false
                        },
                        onOpenVideo = ::openArrangementVideo,
                        onDismiss = { nowPlayingExpanded = false },
                    )
                }
            }

            AmMoreOptions(
                backdrop = pageBackdrop, visible = showMenu, settings = glassSettings,
                onSettings = { glassSettings = it.normalized() },
                onGlassSettings = { showMenu = false; showGlassSettings = true },
                onSync = { showMenu = false; requestLibrarySync(force = true) },
                onHelp = { showMenu = false; showHelp = true },
                onAbout = { showMenu = false; showSettings = true },
                onDismiss = { showMenu = false },
            )
            AmGlassSettingsPanel(pageBackdrop, showGlassSettings, glassSettings,
                onSettings = { glassSettings = it.normalized() }, onDismiss = { showGlassSettings = false })

            val row = rowMenu
            AmActionSheet(
                backdrop = pageBackdrop,
                visible = row != null,
                title = row?.arrangement?.name,
                message = null,
                actions = listOf(
                    AmAction(if (recordingRefId == null) "录制参考指纹" else "停止录制参考指纹") {
                        val target = row
                        rowMenu = null
                        if (recordingRefId != null) {
                            stopRecordRef()
                        } else if (target != null) {
                            startRecordRef(target)
                        }
                    },
                    AmAction("设为当前并播放") {
                        rowMenu = null
                        selectArrangement(row?.id ?: return@AmAction)
                        playArrangement()
                        nowPlayingExpanded = true
                    },
                    AmAction("打开关联视频") {
                        rowMenu = null
                        openVideoUrl(row?.arrangement?.videoUrl)
                    },
                    AmAction("刷新在线曲库") {
                        rowMenu = null
                        requestLibrarySync(force = true)
                    },
                    AmAction("导出 JSON") {
                        val target = row
                        rowMenu = null
                        if (target != null) {
                            pendingExportId = target.id
                            exportArrangementLauncher.launch(ArrangementShareFiles.fileName(target))
                        }
                    },
                    AmAction("分享文件") {
                        val target = row
                        rowMenu = null
                        if (target != null) shareArrangement(target)
                    },
                    AmAction("删除工程", destructive = true) {
                        confirmDeleteId = row?.id
                        rowMenu = null
                    },
                ),
                onDismiss = { rowMenu = null },
            )

            AmDialog(
                backdrop = pageBackdrop,
                visible = showFollowDialog,
                title = "跟随视频",
                message = if (selectedProject != null &&
                    File(filesDir, "ref_${LibrarySync.refIdFor(selectedProject.videoUrl, selectedProject.name)}.bin").exists()
                ) "开始监听后播放视频，自动对齐（支持拖动）"
                else "开始监听后播放视频，出声即从当前进度跟随",
                confirmText = "开始监听",
                onConfirm = {
                    showFollowDialog = false
                    startFollow()
                },
                onDismiss = { showFollowDialog = false },
            )

            AmDialog(
                backdrop = pageBackdrop,
                visible = confirmDeleteId != null,
                title = "删除这个工程？",
                message = "此操作会从本机 App 存储中移除这份编排。",
                confirmText = "删除",
                confirmDestructive = true,
                onConfirm = {
                    confirmDeleteId?.let(::deleteArrangement)
                    confirmDeleteId = null
                },
                onDismiss = { confirmDeleteId = null },
            )

            AmDialog(
                backdrop = pageBackdrop,
                visible = showHelp,
                title = "使用说明",
                message = "「编排」可直接导入 Resolve Cue、iaglight 或 RRNN 文件，行菜单可导出或分享。点「跟随视频」开始监听后再播放视频；含参考指纹时可自动对齐。「设备」连接荧光棒，「控制」调整灯效，「律动」跟随系统音频。",
                confirmText = "知道了",
                onConfirm = { showHelp = false },
                onDismiss = { showHelp = false },
            )

            AmDialog(
                backdrop = pageBackdrop,
                visible = showSettings,
                title = "IAG Official Light Stick",
                message = "版本 ${BuildConfig.VERSION_NAME}\n连接状态：${if (deviceState == LightStickController.State.CONNECTED) "已连接" else "未连接"}\n已保存编排：${arrangements.size} 份",
                confirmText = "好",
                onConfirm = { showSettings = false },
                onDismiss = { showSettings = false },
            )
        }
    }

    private fun selectedStored(): StoredArrangement? = arrangements.firstOrNull { it.id == selectedArrangementId }

    private fun selectedArrangement(): LightingArrangement? = selectedStored()?.arrangement

    private fun requestLibrarySync(force: Boolean = false) {
        if (libraryJob?.isActive == true) { log("曲库正在同步，请稍候"); return }
        libraryJob = lifecycleScope.launch {
            val result = LibrarySync.sync(applicationContext, force)
            log(result)
            if (result.startsWith("曲库已更新") || result.startsWith("曲库已同步")) refreshArrangements()
        }
    }

    private fun refreshArrangements(preferredId: String? = selectedArrangementId) {
        refreshJob?.cancel()
        val selectionAtStart = selectedArrangementId
        refreshJob = lifecycleScope.launch {
            val failures = mutableListOf<ArrangementStorage.ReadFailure>()
            val loaded = try { withContext(Dispatchers.IO) { arrangementStorage.list { failures.add(it) } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { log("读取已保存编排失败：${e.message}"); return@launch }
            arrangements = loaded
            val choice = if (selectedArrangementId != selectionAtStart) selectedArrangementId else preferredId
            val selected = loaded.firstOrNull { it.id == choice } ?: loaded.firstOrNull()
            if (selected?.id != selectedArrangementId) {
                stopArrangement()
                arrangementPositionMs = 0L
                arrangementEffectLabel = null
            }
            selectedArrangementId = selected?.id
            arrangementPositionMs = arrangementPositionMs.coerceIn(0L, selected?.arrangement?.durationMs ?: 0L)
            if (failures.isNotEmpty()) {
                arrangementMessage = "有 ${failures.size} 份编排文件无法读取，原文件已保留"
                log("无法读取的编排：${failures.joinToString("；") { "${it.fileName}（${it.reason}）" }}")
            }
        }
    }

    private fun documentOperation(action: suspend () -> Unit): Job? {
        if (documentJob?.isActive == true) { log("正在处理文件，请稍候"); return null }
        return lifecycleScope.launch { action() }.also { documentJob = it }
    }

    private fun importArrangement(uri: Uri, requestId: String? = null) = documentOperation {
        try {
            val imported = withContext(Dispatchers.IO) {
                val json = contentResolver.openInputStream(uri)?.use { InputLimits.read(it, InputLimits.PROJECT_BYTES).toString(Charsets.UTF_8) }
                    ?: error("无法打开所选 JSON 文件")
                arrangementStorage.importJson(json, requestId)
            }
            refreshArrangements(imported.id)
            arrangementMessage = "已保存「${imported.arrangement.name}」"
            activeTab = 1
            log("已导入并保存「${imported.arrangement.name}」")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { arrangementMessage = "导入失败：${e.message}"; log(arrangementMessage!!) }
    }

    private fun exportSelectedArrangement(uri: Uri, id: String? = selectedArrangementId) {
        val stored = arrangements.firstOrNull { it.id == id } ?: run { log("待导出的编排已不存在"); return }
        documentOperation {
            try {
                withContext(Dispatchers.IO) {
                    val raw = arrangementStorage.rawJson(stored.id)
                    val output = contentResolver.openOutputStream(uri, "wt") ?: error("无法创建目标文件")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(raw) }
                }
                arrangementMessage = "已导出「${stored.arrangement.name}」"
                log(arrangementMessage!!)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { arrangementMessage = "导出失败：${e.message}"; log(arrangementMessage!!) }
        }
    }

    private fun shareArrangement(stored: StoredArrangement) = documentOperation {
        try {
            val file = withContext(Dispatchers.IO) {
                ArrangementShareFiles.prepare(File(cacheDir, "shared-arrangements"), stored, arrangementStorage.rawJson(stored.id))
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.arrangements", file)
            val share = Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newUri(contentResolver, file.name, uri) }
            startActivity(Intent.createChooser(share, "分享灯光编排"))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { arrangementMessage = "分享失败：${e.message}"; log(arrangementMessage!!) }
    }

    private fun deleteArrangement(id: String) {
        if (selectedArrangementId == id) stopArrangement()
        documentOperation {
            try {
                withContext(Dispatchers.IO) { arrangementStorage.delete(id) }
                refreshArrangements()
                log("已删除工程")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { log("删除编排失败：${e.message}") }
        }
    }

    private fun selectArrangement(id: String) {
        if (id == selectedArrangementId) return
        stopArrangement()
        arrangementMessage = null
        selectedArrangementId = arrangements.firstOrNull { it.id == id }?.id
        arrangementPositionMs = 0L
        activeArrangementEventKey = null
        arrangementEffectLabel = null
        rrnnLastCue = null
        rrnnLastFrame = null
        resolveOutput.reset()
    }

    private fun openArrangementVideo() {
        openVideoUrl(selectedArrangement()?.videoUrl)
    }

    private fun openVideoUrl(url: String?) {
        if (url.isNullOrBlank()) {
            log("这个编排没有关联视频链接")
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { log("无法打开视频链接：${it.message}") }
    }

    private fun playArrangement() {
        val project = selectedArrangement() ?: run {
            log("请先导入 Resolve Cue 或灯光工程")
            return
        }
        if (arrangementPlaying) return
        if (followActive) {
            log("跟随运行中，先停止跟随再手动播放")
            return
        }
        if (musicActive) stopMusicCapture(false)
        if (arrangementPositionMs >= project.durationMs) arrangementPositionMs = 0L
        arrangementAnchorPositionMs = arrangementPositionMs
        arrangementAnchorElapsedMs = SystemClock.elapsedRealtime()
        arrangementPlaying = true
        startArrangementForegroundService()
        renderArrangementPosition(project, arrangementPositionMs, forceOutput = true)
        uiHandler.removeCallbacks(arrangementTicker)
        uiHandler.post(arrangementTicker)
        if (deviceState != LightStickController.State.CONNECTED) log("编排开始：当前仅显示本机预览，连接荧光棒后会同步控制设备")
    }

    private fun pauseArrangement() {
        if (followActive) stopFollowCapture()
        val project = selectedArrangement()
        if (arrangementPlaying) {
            arrangementPositionMs = (arrangementAnchorPositionMs + SystemClock.elapsedRealtime() - arrangementAnchorElapsedMs)
                .coerceAtMost(project?.durationMs ?: Long.MAX_VALUE)
        }
        arrangementPlaying = false
        uiHandler.removeCallbacks(arrangementTicker)
        stopArrangementForegroundService()
        if (selectedStored()?.resolve != null) {
            resolveOutput.reset()
            setCurrentPreviewColor("#000000")
            if (controller.state == LightStickController.State.CONNECTED) {
                controller.setKeepAlive(false)
                controller.sendColorFrame(0, 0, 0)
            }
        } else if (project != null) renderArrangementPosition(project, arrangementPositionMs)
    }

    private fun stopArrangement() {
        if (followActive) stopFollowCapture()
        arrangementPlaying = false
        uiHandler.removeCallbacks(arrangementTicker)
        stopArrangementForegroundService()
        arrangementPositionMs = 0L
        activeArrangementEventKey = null
        arrangementEffectLabel = null
        effectHandler.removeCallbacks(effectTicker)
        setCurrentPreviewColor("#000000")
        if (::controller.isInitialized && controller.state == LightStickController.State.CONNECTED) {
            if (selectedStored()?.resolve != null) {
                controller.setKeepAlive(false)
                controller.sendColorFrame(0, 0, 0)
            } else if (selectedStored()?.rrnn != null) controller.shutdownOff() else controller.setColor(0, 0, 0, 0x04)
        }
        rrnnLastCue = null
        rrnnLastFrame = null
        resolveOutput.reset()
    }

    private fun seekArrangement(positionMs: Long) {
        val project = selectedArrangement() ?: return
        if (followActive) {
            stopFollowCapture()
            log("已退出跟随，手动定位")
        }
        arrangementPositionMs = positionMs.coerceIn(0L, project.durationMs)
        if (arrangementPlaying) {
            arrangementAnchorPositionMs = arrangementPositionMs
            arrangementAnchorElapsedMs = SystemClock.elapsedRealtime()
            if (arrangementPositionMs >= project.durationMs) {
                arrangementPlaying = false
                uiHandler.removeCallbacks(arrangementTicker)
                stopArrangementForegroundService()
            }
        }
        renderArrangementPosition(project, arrangementPositionMs, forceOutput = true)
    }

    private fun stopArrangementForManualControl() {
        if (arrangementPlaying || activeArrangementEventKey != null) stopArrangement()
    }

    private fun startArrangementForegroundService() {
        if (deviceState != LightStickController.State.CONNECTED) return
        val intent = Intent(this, ArrangementPlaybackService::class.java)
            .putExtra(ArrangementPlaybackService.EXTRA_TITLE, selectedArrangement()?.name ?: "灯光编排")
        try {
            startForegroundService(intent)
        } catch (_: SecurityException) {
            log("蓝牙权限已变化，请重新授权后播放")
        } catch (_: IllegalStateException) {
            log("后台保活暂不可用，请回到应用后重新播放")
        }
    }

    private fun stopArrangementForegroundService() {
        stopService(Intent(this, ArrangementPlaybackService::class.java))
    }

    private fun renderArrangementPosition(
        project: LightingArrangement,
        positionMs: Long,
        forceOutput: Boolean = false,
    ) {
        selectedStored()?.resolve?.let {
            renderResolvePosition(it, positionMs, forceOutput)
            return
        }
        val rrnn = selectedStored()?.rrnn
        if (rrnn != null) {
            renderRrnnPosition(rrnn, positionMs, forceOutput)
            return
        }
        val event = project.activeEventAt(positionMs)
        arrangementEffectLabel = event?.effect?.label ?: "编排空段"
        val token = event?.let { "${it.trackId}:${it.id}:${it.effect.id}" }
        val color = event?.let { sampleArrangementEvent(it, positionMs) } ?: "#000000"
        setCurrentPreviewColor(color)

        val changed = forceOutput || token != activeArrangementEventKey
        if (changed) {
            activeArrangementEventKey = token
            effectHandler.removeCallbacks(effectTicker)
            if (musicActive) stopMusicCapture(false)
            if (event == null) {
                if (controller.state == LightStickController.State.CONNECTED) controller.setColor(0, 0, 0, 0x04)
            } else if (controller.state == LightStickController.State.CONNECTED) {
                when (event.effect) {
                    ArrangementEffect.SOLID -> {
                        controller.setKeepAlive(true)
                        controller.setColorHex(color)
                    }
                    ArrangementEffect.OFF -> controller.setColor(0, 0, 0, 0x04)
                    ArrangementEffect.BLINK, ArrangementEffect.FAST_BLINK ->
                        controller.setHardwareEffect(0x03, event.color, flashByte(event))
                    ArrangementEffect.BREATHE ->
                        controller.setHardwareEffect(0x04, event.color, event.breatheGear ?: 5)
                    ArrangementEffect.FADE, ArrangementEffect.GRADIENT -> {
                        controller.setKeepAlive(true)
                        controller.setColorHex(color)
                    }
                }
            }
        } else if (event != null && event.effect in setOf(ArrangementEffect.FADE, ArrangementEffect.GRADIENT) &&
            controller.state == LightStickController.State.CONNECTED
        ) {
            controller.updateKeepAliveColor(
                colorComponent(color, 0), colorComponent(color, 2), colorComponent(color, 4),
            )
        }
    }

    private fun renderResolvePosition(sheet: ResolveCueSheet, positionMs: Long, force: Boolean) {
        val sample = sheet.sample(positionMs)
        arrangementEffectLabel = when {
            sample.state == ResolveLightState.BLACK -> "黑场"
            sample.gradient -> "渐变"
            sample.state.mode == "breathing" -> "呼吸"
            sample.state.mode == "flash" -> "闪烁"
            else -> "常亮"
        }
        setCurrentPreviewColor(sample.state.color)
        // Seek/import can update a preview while paused; only explicit playback/follow emits cues.
        if (!arrangementPlaying && !followActive && positionMs < sheet.durationMs) return
        val state = resolveOutput.next(sample, SystemClock.elapsedRealtime(), force) ?: return
        if (controller.state != LightStickController.State.CONNECTED) return
        controller.setKeepAlive(false)
        when (state.mode) {
            "breathing" -> controller.setHardwareEffect(0x04, state.color, state.speed ?: 5)
            "flash" -> controller.setHardwareEffect(0x03, state.color, state.speed ?: 6)
            else -> controller.sendColorFrame(colorComponent(state.color, 0), colorComponent(state.color, 2), colorComponent(state.color, 4))
        }
    }

    // ---------- RRNN 回放（jiaoben.js Gate 语义：帧去重 + cue 边界强发 + 空段 OFF） ----------

    private var rrnnLastCue: RrnnCue? = null
    private var rrnnLastFrame: ByteArray? = null

    private fun renderRrnnPosition(payload: RrnnPayload, positionMs: Long, force: Boolean) {
        val t = positionMs / 1000.0
        val cue = StageCompile.active(payload.fxs, t)
        if (cue == null || cue.type == "off") {
            arrangementEffectLabel = "黑场"
            setCurrentPreviewColor("#000000")
            val off = StageCompile.OFF
            if (force || rrnnLastCue != null || !off.contentEquals(rrnnLastFrame)) {
                rrnnLastCue = null
                rrnnLastFrame = off
                if (::controller.isInitialized && controller.state == LightStickController.State.CONNECTED) {
                    controller.setKeepAlive(false)
                    controller.sendRawFrame(off)
                }
            }
            return
        }
        val frame = StageCompile.compileAt(cue, t)
        arrangementEffectLabel = rrnnEffectLabel(cue, frame)
        setCurrentPreviewColor(frame.hex)
        val bytes = StageCompile.pack(frame)
        val boundary = cue !== rrnnLastCue
        if (force || boundary || !bytes.contentEquals(rrnnLastFrame)) {
            rrnnLastCue = cue
            rrnnLastFrame = bytes
            if (::controller.isInitialized && controller.state == LightStickController.State.CONNECTED) {
                when (frame.cmd) {
                    0x03 -> controller.setHardwareEffect(0x03, frame.hex, frame.freqByte ?: 6)
                    0x04 -> controller.setHardwareEffect(0x04, frame.hex, frame.freqByte ?: 5)
                    0x00 -> {
                        controller.setKeepAlive(false)
                        controller.sendRawFrame(bytes)
                    }
                    else -> {
                        controller.setKeepAlive(true)
                        controller.sendColorFrame(frame.rgb[0], frame.rgb[1], frame.rgb[2])
                    }
                }
            }
        }
    }

    private fun rrnnEffectLabel(cue: RrnnCue, frame: StageCompile.CompiledFrame): String = when {
        frame.multiColor -> "彩色闪烁"
        cue.type == "flash" -> "闪烁"
        cue.type == "breathe" -> "呼吸"
        cue.type == "gradient" -> "渐变"
        cue.type == "rainbow" -> "彩虹"
        cue.type == "pulse" -> "脉冲"
        cue.type == "strobe" -> "频闪"
        cue.type == "color" -> "常亮"
        else -> "灯光"
    }

    private fun sampleArrangementEvent(event: ArrangementEvent, positionMs: Long): String {
        if (positionMs < event.startMs || positionMs >= event.endMs || event.effect == ArrangementEffect.OFF) return "#000000"
        val elapsed = positionMs - event.startMs
        val phase = when (event.effect) {
            ArrangementEffect.BLINK, ArrangementEffect.FAST_BLINK ->
                (elapsed.toDouble() * effectiveFlashHz(event) / 1_000.0) % 1.0
            ArrangementEffect.BREATHE -> {
                val rate = breatheGearHz(event.breatheGear ?: 5)
                if (rate != null) (elapsed.toDouble() * rate / 1_000.0) % 1.0
                else (elapsed % event.periodMs).toDouble() / event.periodMs.toDouble()
            }
            else -> (elapsed % event.periodMs).toDouble() / event.periodMs.toDouble()
        }
        val base = (1..6 step 2).map { offset -> event.color.substring(offset, offset + 2).toInt(16) }
        val factor = when (event.effect) {
            ArrangementEffect.SOLID, ArrangementEffect.BREATHE -> 1.0
            ArrangementEffect.BLINK -> if (phase < .5) 1.0 else 0.0
            ArrangementEffect.FAST_BLINK -> if (phase < .5) 1.0 else 0.0
            ArrangementEffect.FADE -> elapsed.toDouble() / (event.durationMs - 1L).coerceAtLeast(1L)
            ArrangementEffect.GRADIENT -> if (phase < .5) phase * 2.0 else (1.0 - phase) * 2.0
            ArrangementEffect.OFF -> 0.0
        }.coerceIn(0.0, 1.0)
        val amount = if (event.effect == ArrangementEffect.BREATHE) {
            .12 + .88 * (.5 - .5 * sin(2.0 * PI * phase - PI / 2.0))
        } else factor
        return "#" + base.joinToString("") { "%02X".format((it * amount).toInt().coerceIn(0, 255)) }
    }

    private fun flashByte(event: ArrangementEvent): Int = event.flashHz?.let { hz ->
        (52.05 / hz.coerceIn(0.5, 10.0)).roundToInt().coerceIn(5, 255)
    } ?: if (event.effect == ArrangementEffect.FAST_BLINK) 10 else 6

    private fun effectiveFlashHz(event: ArrangementEvent): Double = 52.05 / flashByte(event).toDouble()

    private fun breatheGearHz(gear: Int): Double? {
        val measured = listOf(3 to 0.590, 4 to 0.780, 5 to 0.977, 6 to 1.165, 8 to 1.579,
            10 to 1.935, 12 to 2.310, 15 to 3.000, 20 to 3.750)
        if (gear < measured.first().first || gear > measured.last().first) return null
        val pair = measured.zipWithNext().firstOrNull { (left, right) -> gear in left.first..right.first } ?: return null
        val (left, right) = pair
        val ratio = (gear - left.first).toDouble() / (right.first - left.first)
        return left.second + (right.second - left.second) * ratio
    }


    private fun setBaseColor(hex: String) {
        stopArrangementForManualControl()
        selectedBaseColor = hex
        stopMusicCapture(false)
        applyEffect(1)
    }

    private fun updateColorBrightness(value: Int) {
        stopArrangementForManualControl()
        brightness = value.coerceIn(15, 100)
        stopMusicCapture(false)
        applyEffect(1)
    }

    private fun chooseEffect(mode: Int) {
        stopArrangementForManualControl()
        stopMusicCapture(false)
        applyEffect(mode)
    }

    /** Apple Music 播放/暂停大钮：灯亮着则熄灭，已熄灭则恢复常亮；律动激活时先停止律动。 */
    private fun onMainToggle() {
        when {
            musicActive -> stopMusicCapture(true)
            currentMode == 0 -> applyEffect(1)
            else -> applyEffect(0)
        }
    }

    private fun searchDevices() = withBluetooth { controller.startScan() }

    private fun connect(candidate: StickCandidate) = withBluetooth { controller.connect(candidate) }

    private fun disconnectDevice() {
        stopMusicCapture(false)
        effectHandler.removeCallbacks(effectTicker)
        controller.disconnect()
    }

    private fun applyEffect(mode: Int) {
        effectHandler.removeCallbacks(effectTicker)
        currentMode = mode
        when (mode) {
            0 -> {
                setCurrentPreviewColor("#000000")
                if (controller.state == LightStickController.State.CONNECTED) controller.setColor(0, 0, 0, 0x04)
            }
            1 -> {
                val color = scaleColor(selectedBaseColor, brightness / 100f)
                setCurrentPreviewColor(color)
                if (controller.state == LightStickController.State.CONNECTED) {
                    controller.setColor(colorComponent(color, 0), colorComponent(color, 2), colorComponent(color, 4))
                    controller.setKeepAlive(true)
                }
            }
            2 -> {
                effectTick = 0
                if (controller.state == LightStickController.State.CONNECTED) controller.setKeepAlive(true)
                effectTicker.run()
            }
            3 -> applyHardwareEffect(0x03, 10)
            4 -> applyHardwareEffect(0x03, 6)
            5 -> applyHardwareEffect(0x04, 5)
            in 6..8 -> {
                effectTick = 0
                if (controller.state == LightStickController.State.CONNECTED) controller.setKeepAlive(true)
                effectTicker.run()
            }
        }
    }

    private fun applyHardwareEffect(command: Int, frequency: Int) {
        val color = scaleColor(selectedBaseColor, brightness / 100f)
        setCurrentPreviewColor(color)
        if (controller.state == LightStickController.State.CONNECTED) controller.setHardwareEffect(command, color, frequency)
    }

    /** 官方灯效机制 1:1：软件效果只用 setColor（保活帧承载），颜色取官方 rainbowColors。 */
    private fun renderEffect(mode: Int, tick: Int, baseColor: String): String = when (mode) {
        2 -> rainbowColors.random()
        6, 7 -> rainbowColors[tick % rainbowColors.size]
        8 -> if (tick % 3 == 2) "#000000" else rainbowColors.random()
        else -> baseColor
    }

    private fun randomMusicColor(): String = musicPaletteColor((Math.random() * musicPalette.size).toInt())

    private fun musicPaletteColor(index: Int): String {
        val (red, green, blue) = musicPalette[index]
        return "#%02X%02X%02X".format(red, green, blue)
    }

    private fun scaleColor(hex: String, factor: Float): String {
        val value = hex.removePrefix("#")
        return "#" + (0..4 step 2).joinToString("") { offset ->
            val channel = floor(value.substring(offset, offset + 2).toInt(16) * factor.coerceIn(0f, 1f)).toInt()
            "%02X".format(channel)
        }
    }

    private fun colorComponent(hex: String, offset: Int): Int = hex.removePrefix("#").substring(offset, offset + 2).toInt(16)

    private fun setCurrentPreviewColor(hex: String) {
        previewColor = hex.uppercase(Locale.ROOT)
    }

    private fun startMusicCapture() {
        stopArrangementForManualControl()
        if (controller.state != LightStickController.State.CONNECTED) {
            log("请先搜索并连接 IAG 荧光棒")
            activeTab = 3
            return
        }
        if (Build.VERSION.SDK_INT < 29) {
            log("系统播放音频捕获需要 Android 10 或更高版本")
            return
        }
        ensureCapturePerms {
            pendingRecordRefId = ""
            pendingManualFollow = false
            pendingCaptureMode = CaptureService.MODE_MUSIC
            val manager = getSystemService(MediaProjectionManager::class.java)
            log("请求 Android 系统播放音频捕获授权（不启用麦克风音源）")
            runCatching { startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE) }
                .onFailure { captureAuthorizationPending = false; log("无法请求系统音频捕获授权：${it.message}") }
        }
    }

    private fun stopMusicCapture(userAction: Boolean) {
        if (!musicActive) return
        musicActive = false
        captureSessionId = null
        musicHandler.removeCallbacks(musicColorTicker)
        stopService(Intent(this, CaptureService::class.java))
        if (currentMode == 99) {
            currentMode = 1
            val color = scaleColor(selectedBaseColor, brightness / 100f)
            setCurrentPreviewColor(color)
            if (controller.state == LightStickController.State.CONNECTED) controller.setColorHex(color)
        }
        if (userAction) log("音乐调光已停止")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                if (resultCode == RESULT_OK) bluetoothActionAfterEnable?.invoke() else log("蓝牙未开启")
                bluetoothActionAfterEnable = null
            }
            REQUEST_CAPTURE -> {
                captureAuthorizationPending = false
                if (resultCode != RESULT_OK || data == null) {
                    pendingRecordRefId = ""
                    pendingManualFollow = false
                    log("系统播放音频捕获授权被拒绝")
                    return
                }
                lifecycleScope.launch {
                    refreshJob?.join()
                    startCaptureService(resultCode, data)
                }
            }
        }
    }

    private fun newCaptureIntent(): Intent {
        captureSessionId = java.util.UUID.randomUUID().toString()
        return Intent(this, CaptureService::class.java).putExtra(CaptureService.EXTRA_SESSION_ID, captureSessionId)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val recordRefId = pendingRecordRefId
        pendingRecordRefId = ""
        val manualFollow = pendingManualFollow
        pendingManualFollow = false
        runCatching {
            val kind = CaptureRouting.resolve(pendingCaptureMode, recordRefId, manualFollow)
            if (kind == CaptureKind.REFERENCE) {
                startRecordCapture(resultCode, data, recordRefId)
                return@runCatching
            }
            if (kind == CaptureKind.MANUAL_FOLLOW) {
                startManualFollowCapture(resultCode, data)
                return@runCatching
            }
            if (kind == CaptureKind.FOLLOW) {
                startFollowCapture(resultCode, data)
                return@runCatching
            }
            currentMode = 99
            musicActive = true
            latestMusicLevel = 0
            musicPaletteIndex = -1
            controller.setKeepAlive(true)
            musicHandler.removeCallbacks(musicColorTicker)
            musicHandler.post(musicColorTicker)
            val serviceIntent = newCaptureIntent()
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
                .putExtra(CaptureService.EXTRA_MODE, pendingCaptureMode)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            log("音乐调光已启动：读取系统播放音频，不使用麦克风")
        }.onFailure {
            if (followActive) stopFollowCapture() else stopMusicCapture(false)
            log("启动捕获失败：${it.message}")
        }
    }

    // ---------- 视频跟随（JIHUA.md §1） ----------

    private fun onFollowToggle() {
        if (followActive) {
            stopFollowCapture()
            log("跟随已停止，转手动播放")
            if (arrangementPlaying) {
                arrangementAnchorPositionMs = arrangementPositionMs
                arrangementAnchorElapsedMs = SystemClock.elapsedRealtime()
                uiHandler.removeCallbacks(arrangementTicker)
                uiHandler.post(arrangementTicker)
            }
        } else {
            showFollowDialog = true
        }
    }

    private fun startFollow() {
        val project = selectedArrangement() ?: run {
            log("请先选择灯光工程")
            return
        }
        val refId = LibrarySync.refIdFor(project.videoUrl, project.name)
        val hasRef = File(filesDir, "ref_$refId.bin").exists()
        if (musicActive) stopMusicCapture(false)
        ensureCapturePerms {
            pendingRecordRefId = ""
            pendingManualFollow = !hasRef
            if (hasRef) {
                pendingCaptureMode = CaptureService.MODE_SYNC
                pendingFollowRefId = refId
            } else {
                // 降级（JIHUA 第二级）：无指纹库 → 手动对齐，用音乐调光回路的电平做静音判定
                pendingCaptureMode = CaptureService.MODE_MUSIC
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            log(if (hasRef) "请求系统播放音频捕获授权（跟随模式）" else "无参考指纹库，进入手动对齐跟随")
            runCatching { startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE) }
                .onFailure { captureAuthorizationPending = false; log("无法请求系统音频捕获授权：${it.message}") }
        }
    }

    private fun startFollowCapture(resultCode: Int, data: Intent) {
        followActive = true
        followOffsetMs = 0L
        followEngine = FollowEngine(
            handler = uiHandler,
            durationMs = { selectedArrangement()?.durationMs ?: 0L },
            onUpdate = ::updateFollowPosition,
            onState = { followState = it },
        ).also { it.start() }
        arrangementPlaying = true
        startArrangementForegroundService()
        if (deviceState != LightStickController.State.CONNECTED) {
            log("跟随开始：仅本机预览，连接荧光棒后同步控制设备")
        }
        val serviceIntent = newCaptureIntent()
            .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_DATA, data)
            .putExtra(CaptureService.EXTRA_MODE, CaptureService.MODE_SYNC)
            .putExtra(CaptureService.EXTRA_VIDEO_ID, pendingFollowRefId)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        log("跟随已启动：正在聆听音频寻找咬合…")
    }

    /** 手动对齐跟随（无指纹库降级）：武装监听，视频出声瞬间从当前进度开始外推。 */
    /** 参考指纹录制：把工程视频在 B 站完整播放一遍，录下 IAGREF1；之后跟随升级为自动对齐（可拖动）。 */
    private fun startRecordRef(stored: StoredArrangement) {
        selectArrangement(stored.id)
        if (followActive) stopFollowCapture()
        if (musicActive) stopMusicCapture(false)
        val refId = LibrarySync.refIdFor(stored.arrangement.videoUrl, stored.arrangement.name)
        ensureCapturePerms {
            pendingManualFollow = false
            pendingRecordRefId = refId
            pendingCaptureMode = CaptureService.MODE_RECORD
            val manager = getSystemService(MediaProjectionManager::class.java)
            log("请求音频捕获授权（录制参考指纹）")
            runCatching { startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE) }
                .onFailure { captureAuthorizationPending = false; log("无法请求系统音频捕获授权：${it.message}") }
        }
    }

    private fun stopRecordRef() {
        if (recordingRefId == null) return
        log("参考录制停止，正在保存…")
        recordingRefId = null
        // Accept this session's final save result; a later start installs a new identity.
        stopService(Intent(this, CaptureService::class.java))
    }

    private fun startRecordCapture(resultCode: Int, data: Intent, refId: String) {
        recordingRefId = refId
        val serviceIntent = newCaptureIntent()
            .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_DATA, data)
            .putExtra(CaptureService.EXTRA_MODE, CaptureService.MODE_RECORD)
            .putExtra(CaptureService.EXTRA_VIDEO_ID, refId)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        log("参考录制中：请在 B 站把这首视频完整播放一遍（勿静音/暂停），录完再点「停止录制」")
    }

    private fun startManualFollowCapture(resultCode: Int, data: Intent) {
        followActive = true
        followManual = true
        followOffsetMs = 0L
        followEngine = FollowEngine(
            handler = uiHandler,
            durationMs = { selectedArrangement()?.durationMs ?: 0L },
            onUpdate = ::updateFollowPosition,
            onState = { followState = it },
        ).also { it.startArmed(arrangementPositionMs) }
        arrangementPlaying = true
        startArrangementForegroundService()
        if (deviceState != LightStickController.State.CONNECTED) {
            log("跟随（手动）：仅本机预览，连接荧光棒后同步控制设备")
        }
        val serviceIntent = newCaptureIntent()
            .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_DATA, data)
            .putExtra(CaptureService.EXTRA_MODE, CaptureService.MODE_MUSIC)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        log("监听已开启：播放视频后从当前进度开始跟随")
    }

    private fun stopFollowCapture() {
        if (!followActive) return
        followActive = false
        captureSessionId = null
        followManual = false
        followEngine?.stop()
        followState = null
        stopService(Intent(this, CaptureService::class.java))
    }

    private fun updateFollowPosition(posMs: Long, hard: Boolean) {
        val project = selectedArrangement() ?: run {
            stopFollowCapture()
            return
        }
        val pos = posMs.coerceIn(0L, project.durationMs)
        arrangementPositionMs = pos
        arrangementAnchorPositionMs = pos
        arrangementAnchorElapsedMs = SystemClock.elapsedRealtime()
        renderArrangementPosition(project, pos, forceOutput = hard)
        if (project.durationMs > 0 && pos >= project.durationMs) {
            log("跟随：播放完成")
            stopFollowCapture()
            stopArrangement()
        }
    }

    private fun followStatusUi(state: FollowState?, manual: Boolean = false): AmFollowStatus? = when (state) {
        FollowState.WAITING -> AmFollowStatus(if (manual) "监听中" else "等待", Color(0xFF8E8E93))
        FollowState.LOCKED -> AmFollowStatus(if (manual) "手动" else "已咬合", Color(0xFF34C759))
        FollowState.HOLD -> AmFollowStatus("保持", Color(0xFFFF9500))
        FollowState.LOST -> AmFollowStatus("失配", Color(0xFFFF3B30))
        null -> null
    }

    private fun withBluetooth(action: () -> Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < 31) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                .forEach(permissions::add)
        }
        requestPermissionsOrContinue(permissions) {
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null) {
                log("手机不支持蓝牙")
            } else if (!adapter.isEnabled) {
                bluetoothActionAfterEnable = action
                runCatching { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH) }
                    .onFailure { log("无法打开蓝牙设置：${it.message}") }
            } else action()
        }
    }

    private fun ensureCapturePerms(action: () -> Unit) {
        if (captureAuthorizationPending) { log("请先完成当前系统授权"); return }
        if (Build.VERSION.SDK_INT < 29) {
            log("系统播放音频捕获需要 Android 10 或更高版本")
            return
        }
        captureAuthorizationPending = true
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsOrContinue(permissions, action)
    }

    private fun requestPermissionsOrContinue(permissions: List<String>, action: () -> Unit) {
        if (permissions.isEmpty()) runPermissionAction(action) else {
            pendingPermissionAction = action
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun runPermissionAction(action: () -> Unit) {
        try { action() }
        catch (_: SecurityException) {
            captureAuthorizationPending = false
            log("权限已变化，请重新授权后操作")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val action = pendingPermissionAction
        pendingPermissionAction = null
        val requiredGranted = grantResults.isNotEmpty() && permissions.indices.all { index ->
            (Build.VERSION.SDK_INT >= 33 && permissions[index] == Manifest.permission.POST_NOTIFICATIONS) ||
                grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        }
        if (requiredGranted) {
            if (action != null) runPermissionAction(action) else { captureAuthorizationPending = false; log("权限已更新，请重新选择操作") }
        } else { captureAuthorizationPending = false; log("必要权限未授予，操作无法继续") }
    }

    private fun log(message: String) {
        val line = "${clock.format(Date())}  $message"
        runOnUiThread {
            logLines.addLast(line)
            while (logLines.size > 8) logLines.removeFirst()
            logText = logLines.joinToString("\n")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", activeTab)
        outState.putString("projectId", selectedArrangementId)
        outState.putLong("position", arrangementPositionMs)
        outState.putString("baseColor", selectedBaseColor)
        outState.putInt("brightness", brightness)
        outState.putString("captureMode", pendingCaptureMode)
        outState.putString("followRef", pendingFollowRefId)
        outState.putString("recordRef", pendingRecordRefId)
        outState.putBoolean("manualFollow", pendingManualFollow)
        outState.putBoolean("capturePending", captureAuthorizationPending)
        outState.putString("exportId", pendingExportId)
        outState.putStringArrayList("incomingUris", ArrayList(pendingImports.map { it.uri.toString() }))
        outState.putStringArrayList("incomingIds", ArrayList(pendingImports.map { it.requestId }))
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        glassSettings.save(applicationContext)
        super.onStop()
    }

    override fun onDestroy() {
        incomingImportJob?.cancel()
        arrangementPlaying = false
        uiHandler.removeCallbacks(arrangementTicker)
        stopArrangementForegroundService()
        stopMusicCapture(false)
        effectHandler.removeCallbacksAndMessages(null)
        musicHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(captureReceiver) }
        runCatching { unregisterReceiver(realignReceiver) }
        followEngine?.stop()
        if (followActive || recordingRefId != null) stopService(Intent(this, CaptureService::class.java))
        if (::controller.isInitialized) controller.close()
        super.onDestroy()
    }

    companion object {
        private const val ARRANGEMENT_TICK_MS = 33L
        private const val REQUEST_PERMISSIONS = 100
        private const val REQUEST_CAPTURE = 200
        private const val REQUEST_ENABLE_BLUETOOTH = 201
    }
}
