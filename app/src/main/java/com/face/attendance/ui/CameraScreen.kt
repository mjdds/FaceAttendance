package com.face.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.face.attendance.face.BoundingBoxOverlay
import com.face.attendance.face.CameraController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelReady by viewModel.modelReady.collectAsState()
    val persons by viewModel.persons.collectAsState()
    val lastRec by viewModel.lastRecognized.collectAsState()
    val faceDetected by viewModel.faceDetected.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val recognitionClass by viewModel.recognitionClass.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraEnabled by remember { mutableStateOf(true) }
    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var showClassPicker by remember { mutableStateOf(false) }

    // 相机是否已成功绑定并开始出流（用于消除首次黑屏间隙）
    var cameraStreaming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.refreshPersons()
    }

    if (!hasPermission) {
        PermissionPrompt(Modifier.fillMaxSize())
        return
    }

    val overlay = remember { BoundingBoxOverlay(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // PERFORMANCE 模式使用 SurfaceView 渲染，延迟更低，避免部分设备首次黑屏
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    LaunchedEffect(Unit) { viewModel.initModel(context, overlay) }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── 摄像头预览 ──
        if (cameraEnabled) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            AndroidView(factory = { overlay }, modifier = Modifier.fillMaxSize())
        } else {
            CameraOffPlaceholder(Modifier.fillMaxSize())
        }

        // ── 启动/停止摄像头（不等待模型加载，权限拿到即出画面） ──
        if (hasPermission && cameraEnabled) {
            DisposableEffect(hasPermission, cameraEnabled) {
                cameraStreaming = false
                // 先以无 analyser 启动相机预览，模型就绪后再 attachAnalyser 补绑识别
                val controller = CameraController(context, lifecycleOwner, previewView, overlay, null).also { ctrl ->
                    ctrl.onCameraBound = { cameraStreaming = true }
                    ctrl.start()
                }
                cameraController = controller
                // 超时兜底：5 秒后强制隐藏遮罩，避免相机异常时永久卡住
                val timeoutJob = scope.launch(Dispatchers.Main) {
                    delay(5000)
                    if (!cameraStreaming) cameraStreaming = true
                }
                onDispose {
                    timeoutJob.cancel()
                    controller.stop()
                    viewModel.getAnalyser()?.clearLastEmbedding()
                }
            }
        }

        // ── 模型就绪后，把识别分析补绑到已在运行的相机上 ──
        // key 含 cameraEnabled：摄像头关闭再开启时需重新补绑 ImageAnalysis，
        // 否则新 CameraController（analyser=null）只有预览、无人脸检测，框会卡住
        LaunchedEffect(modelReady, cameraEnabled) {
            if (modelReady && cameraEnabled) {
                val an = viewModel.getAnalyser()
                if (an != null) cameraController?.attachAnalyser(an)
            }
        }

        // ── 顶部渐变遮罩 + 状态药丸 ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusBarsPadding + 120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
        StatusPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarsPadding + 16.dp, start = 20.dp, end = 20.dp)
        ) {
            val recName = lastRec?.name
            val statusText: String
            val statusIcon: ImageVector
            val statusColor: Color

            when {
                !modelReady -> {
                    statusText = "正在加载模型…"
                    statusIcon = Icons.Filled.CenterFocusStrong
                    statusColor = Warning
                }
                persons.isEmpty() -> {
                    statusText = "暂无录入人员"
                    statusIcon = Icons.Filled.CenterFocusStrong
                    statusColor = Warning
                }
                recName == null -> {
                    statusText = "请将脸部正对镜头"
                    statusIcon = Icons.Filled.Face
                    statusColor = Info
                }
                recName == "未识别" -> {
                    statusText = "未识别，请重试"
                    statusIcon = Icons.Filled.CenterFocusStrong
                    statusColor = Warning
                }
                else -> {
                    statusText = recName
                    statusIcon = Icons.Filled.CheckCircle
                    statusColor = Success
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("识别状态", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    Text(statusText, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // ── 人脸检测指示器（底部浮层） ──
        if (cameraEnabled && modelReady) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 130.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (faceDetected) Brush.horizontalGradient(SuccessGradient.map { it.copy(alpha = 0.85f) })
                            else Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.7f)))
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (faceDetected) Color.White else Warning)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (faceDetected) "已检测到人脸" else "等待人脸入镜…",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ── 班级选择按钮（左下角，切换摄像头上方） ──
        if (classes.isNotEmpty() && cameraEnabled && modelReady) {
            FloatingActionButton(
                onClick = { showClassPicker = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 200.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (recognitionClass.isBlank()) "全部班级" else recognitionClass,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        }

        // ── 班级选择下拉菜单 ──
        if (showClassPicker) {
            AlertDialog(
                onDismissRequest = { showClassPicker = false },
                title = { Text("选择识别班级") },
                text = {
                    Column {
                        // 全部班级选项
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.setRecognitionClass("")
                                showClassPicker = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = recognitionClass.isBlank(),
                                onClick = {
                                    viewModel.setRecognitionClass("")
                                    showClassPicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("全部班级", style = MaterialTheme.typography.bodyLarge)
                        }
                        // 各班级选项
                        classes.forEach { cls ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.setRecognitionClass(cls)
                                    showClassPicker = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = recognitionClass == cls,
                                    onClick = {
                                        viewModel.setRecognitionClass(cls)
                                        showClassPicker = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(cls, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showClassPicker = false }) { Text("取消") }
                }
            )
        }

        // ── 前后摄像头切换 FAB ──
        FloatingActionButton(
            onClick = { cameraController?.switchCamera() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 130.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = "切换摄像头")
        }

        // ── 摄像头开关 FAB ──
        ExtendedFloatingActionButton(
            onClick = { cameraEnabled = !cameraEnabled },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 120.dp),
            containerColor = if (cameraEnabled) Error else Success,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
            icon = {
                Icon(
                    if (cameraEnabled) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    contentDescription = null
                )
            },
            text = { Text(if (cameraEnabled) "关闭" else "开启", fontWeight = FontWeight.SemiBold) }
        )

        // ── 加载遮罩：模型未就绪 或 相机未出流时显示，消除首次黑屏 ──
        val showLoading = !modelReady || (cameraEnabled && !cameraStreaming)
        AnimatedVisibility(showLoading, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val loadingText = when {
                        !modelReady -> "正在加载人脸识别模型…"
                        !cameraStreaming -> "正在启动摄像头…"
                        else -> ""
                    }
                    if (loadingText.isNotEmpty()) {
                        Text(loadingText, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.VideocamOff, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("需要相机权限才能进行人脸识别", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CameraOffPlaceholder(modifier: Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.VideocamOff, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("摄像头已关闭", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("点击右下角按钮重新开启", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 顶部状态药丸 — 半透明深色背景，文字清晰 */
@Composable
fun StatusPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(14.dp)
    ) { content() }
}
