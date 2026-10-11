package com.face.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.face.attendance.ui.AttendanceTheme
import com.face.attendance.ui.AttendanceViewModel
import com.face.attendance.ui.CameraScreen
import com.face.attendance.ui.ClassManageScreen
import com.face.attendance.ui.DockItem
import com.face.attendance.ui.EnrollScreen
import com.face.attendance.dock.LiquidDock
import com.face.attendance.dock.LiquidDockTab
import com.face.attendance.dock.SimpleDock
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.face.attendance.ui.OnboardingScreen
import com.face.attendance.ui.PersonDetailScreen
import com.face.attendance.ui.PersonLibraryScreen
import com.face.attendance.ui.RecordsScreen
import com.face.attendance.ui.SettingsScreen
import com.face.attendance.ui.StatsScreen
import com.face.attendance.ui.VoiceSelectScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AttendanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 尽早加载省流模式配置，供相机/模型/Dock 读取
        com.face.attendance.util.PerformanceManager.init(applicationContext)
        // Android 9 及以下：自动备份写公共目录需要存储权限（Android 10+ 走 MediaStore，免权限）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001
            )
        }
        // App 切到后台（含卸载前被系统接管）时把最新数据备份到公共目录，保证卸载不丢
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                viewModel.flushBackupNow()
            }
        })
        val prefs = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
        // 版本号联动：版本升级时自动重播引导页
        val currentVersionCode = packageManager
            .getPackageInfo(packageName, 0).let {
                if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt()
                else @Suppress("DEPRECATION") it.versionCode
            }
        val lastShownVersion = prefs.getInt("onboarding_shown_version", -1)
        val onboardingDone = prefs.getBoolean("onboarding_done", false)
        val needOnboarding = !onboardingDone || lastShownVersion != currentVersionCode
        setContent {
            AttendanceTheme {
                var showOnboarding by remember { mutableStateOf(needOnboarding) }

                if (showOnboarding) {
                    OnboardingScreen(onFinished = {
                        prefs.edit()
                            .putBoolean("onboarding_done", true)
                            .putInt("onboarding_shown_version", currentVersionCode)
                            .apply()
                        showOnboarding = false
                    })
                } else {
                    MainContent(
                        viewModel = viewModel,
                        onReplayOnboarding = { showOnboarding = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    viewModel: AttendanceViewModel,
    onReplayOnboarding: () -> Unit
) {
    val snackbarHost = remember { SnackbarHostState() }
    val toast by viewModel.toast.collectAsState()
    LaunchedEffect(toast) {
        val t = toast
        if (t != null) {
            snackbarHost.showSnackbar(t)
            viewModel.clearToast()
        }
    }

    // 重装/清空后：若公共目录存在自动备份且本机为空，提示一键恢复
    val pendingRestore by viewModel.pendingRestore.collectAsState()
    LaunchedEffect(Unit) { viewModel.maybeOfferRestore() }
    pendingRestore?.let { ref ->
        val dateStr = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()
        ).format(java.util.Date(ref.exportTime))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissRestoreOffer() },
            title = { androidx.compose.material3.Text("发现上次备份") },
            text = {
                androidx.compose.material3.Text(
                    "检测到卸载或清空前的自动备份：\n共 ${ref.personCount} 人 · 备份于 $dateStr\n\n是否立即恢复？恢复为合并方式，不会覆盖现有数据。"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.restoreFromAutoBackup() }) {
                    androidx.compose.material3.Text("恢复")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.dismissRestoreOffer() }) {
                    androidx.compose.material3.Text("暂不")
                }
            }
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showVoiceSelect by remember { mutableStateOf(false) }
    var showClassManage by remember { mutableStateOf(false) }
    val items = remember {
        listOf(
            DockItem("考勤", Icons.Filled.HowToReg),
            DockItem("录入", Icons.Filled.PersonAdd),
            DockItem("人脸", Icons.Filled.Face),
            DockItem("记录", Icons.Filled.History),
            DockItem("统计", Icons.Filled.BarChart),
            DockItem("设置", Icons.Filled.Settings)
        )
    }

    // Kyant 层背景：供液态玻璃 Dock 折射/模糊其下方内容；省流模式不接入
    val screenBackdrop = rememberLayerBackdrop()
    val lowPerf = com.face.attendance.util.PerformanceManager.lowPerformanceMode

    val detail by viewModel.detailPerson.collectAsState()

    // ── 小红书风格共享元素转场：记录被点击卡片的屏幕坐标 ──
    var detailOrigin by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val detailProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 转场容器在根坐标系中的偏移：卡片捕获的是 positionInRoot 根坐标，
    // 而 graphicsLayer 位移发生在 Scaffold padding 之后的容器坐标系，需换算对齐
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    // 带退出动画的关闭：先缩回再真正置空
    fun closeDetailAnimated() {
        scope.launch {
            detailProgress.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            viewModel.closeDetail()
            detailOrigin = null
        }
    }

    // 入场动画：详情与点击源同时就绪时，从 0 缩放展开到 1
    LaunchedEffect(detail, detailOrigin) {
        if (detail != null && detailOrigin != null) {
            detailProgress.snapTo(0f)
            detailProgress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        }
    }

    // ── 返回键/侧滑返回拦截，按优先级处理 ──
    // 1. 详情页打开 → 带动画退出
    BackHandler(enabled = detail != null) {
        closeDetailAnimated()
    }
    // 2. 语音选择覆盖页打开 → 关闭
    BackHandler(enabled = showVoiceSelect) {
        showVoiceSelect = false
    }
    // 2.5. 班级管理覆盖页打开 → 关闭
    BackHandler(enabled = showClassManage) {
        showClassManage = false
    }
    // 3. 非考勤页 → 回到考勤页
    BackHandler(enabled = detail == null && !showVoiceSelect && selectedIndex != 0) {
        selectedIndex = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { containerOrigin = it.positionInRoot() }
        ) {
            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()
            val p = detailProgress.value

            // 子页面卡片坐标（相对各自根布局）→ 容器坐标，供共享元素展开动画使用
            fun toContainerRect(rect: android.graphics.Rect) = android.graphics.Rect(
                (rect.left - containerOrigin.x).toInt(),
                (rect.top - containerOrigin.y).toInt(),
                (rect.right - containerOrigin.x).toInt(),
                (rect.bottom - containerOrigin.y).toInt()
            )

            // 背景层：随转场进度轻微缩小并淡出，营造层次感
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1f - p * 0.08f
                    scaleY = 1f - p * 0.08f
                    alpha = 1f - p * 0.5f
                }
            ) {
                AnimatedContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (lowPerf) Modifier else Modifier.layerBackdrop(screenBackdrop)),
                    targetState = selectedIndex,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(250)) { it * direction / 4 } + fadeIn(tween(250))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it * direction / 4 } + fadeOut(tween(200)))
                    },
                    label = "screen"
                ) { index ->
                    when (index) {
                        0 -> CameraScreen(viewModel)
                        1 -> EnrollScreen(viewModel, onOpenDetail = { rect ->
                            // 根坐标 → 容器坐标换算，消除状态栏内边距导致的收回偏移
                            detailOrigin = toContainerRect(rect)
                        })
                        2 -> PersonLibraryScreen(viewModel, onOpenDetail = { rect ->
                            detailOrigin = toContainerRect(rect)
                        })
                        3 -> RecordsScreen(viewModel)
                        4 -> StatsScreen(viewModel, onManageClasses = { showClassManage = true })
                        5 -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { selectedIndex = 0 },
                            onOpenVoiceSelect = { showVoiceSelect = true },
                            onReplayOnboarding = onReplayOnboarding
                        )
                    }
                }

                val dockModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                if (lowPerf) {
                    // 省流模式：单层不透明胶囊，无玻璃模糊/着色器
                    SimpleDock(
                        icons = items.map { it.icon },
                        selectedIndex = selectedIndex,
                        onSelected = { selectedIndex = it },
                        modifier = dockModifier
                    )
                } else {
                    // 完整复刻 AndroidLiquidGlass 的液态玻璃 Dock
                    LiquidDock(
                        selectedTabIndex = { selectedIndex },
                        onTabSelected = { selectedIndex = it },
                        backdrop = screenBackdrop,
                        tabsCount = items.size,
                        modifier = dockModifier
                    ) {
                        items.forEachIndexed { i, item ->
                            LiquidDockTab(onClick = { selectedIndex = i }) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (i == selectedIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHost,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            val detailOpening = detail != null && detailOrigin != null
            if (detailOpening) {
                val o = detailOrigin!!
                // 半透明遮罩：随进度加深；点击遮罩任意处反向缩回（小红书交互）
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f * p))
                        .pointerInput(Unit) {
                            detectTapGestures { closeDetailAnimated() }
                        }
                )
                // 详情页从点击源矩形缩放展开到全屏：
                // - 位置：translation 从源中心 → 屏幕中心
                // - 尺寸：scaleX/Y 独立插值，起始帧与卡片矩形完全重合（无跳变）
                // - 圆角：28dp → 0dp 随进度插值
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val sx = o.width() / screenW
                        val sy = o.height() / screenH
                        scaleX = sx + (1f - sx) * p
                        scaleY = sy + (1f - sy) * p
                        translationX = (o.exactCenterX() - screenW / 2f) * (1f - p)
                        translationY = (o.exactCenterY() - screenH / 2f) * (1f - p)
                        alpha = (p * 1.6f).coerceAtMost(1f)
                    }.clip(RoundedCornerShape((1f - p) * 18f))
                ) {
                    PersonDetailScreen(viewModel, onBack = { closeDetailAnimated() })
                }
            } else if (detail != null) {
                // 兜底：无点击源坐标时直接展示
                PersonDetailScreen(viewModel, onBack = { closeDetailAnimated() })
            }

            // 语音模型选择覆盖页
            if (showVoiceSelect) {
                VoiceSelectScreen(
                    viewModel = viewModel,
                    onBack = { showVoiceSelect = false }
                )
            }

            // 班级管理覆盖页
            if (showClassManage) {
                ClassManageScreen(
                    viewModel = viewModel,
                    onBack = { showClassManage = false }
                )
            }
        }
    }
}
