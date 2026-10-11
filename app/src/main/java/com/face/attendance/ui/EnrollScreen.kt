package com.face.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.face.attendance.face.BoundingBoxOverlay
import com.face.attendance.face.CameraController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollScreen(viewModel: AttendanceViewModel, onOpenDetail: (android.graphics.Rect) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelReady by viewModel.modelReady.collectAsState()
    val progress by viewModel.enrollProgress.collectAsState()
    val persons by viewModel.persons.collectAsState()
    val enrollName by viewModel.enrollName.collectAsState()
    val enrollClass by viewModel.enrollClass.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val faceDetected by viewModel.faceDetected.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    val overlay = remember { BoundingBoxOverlay(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initModel(context, overlay)
        viewModel.setEnrollMode(true)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setEnrollMode(false) }
    }

    val cameraActive = hasPermission && modelReady
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val canCollect = cameraActive && faceDetected && enrollName.isNotBlank() &&
        progress < AttendanceViewModel.ENROLL_SAMPLE_COUNT
    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var cameraStreaming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarsPadding)
    ) {
        // ── 标题 ──
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
            Text("人员录入", style = MaterialTheme.typography.headlineMedium)
            Text(
                "采集人脸特征向量并保存到本地",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── 摄像头预览卡片（固定在顶部，不随下方列表滚动） ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))
            ) {
                // AndroidView 始终存在，避免 if 切换导致重新挂载
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                AndroidView(factory = { overlay }, modifier = Modifier.fillMaxSize())

                // 摄像头生命周期：权限拿到即启动预览，不等模型加载（消除首次黑屏）
                DisposableEffect(hasPermission) {
                    cameraStreaming = false
                    val controller: CameraController? = if (hasPermission) {
                        viewModel.setEnrollMode(true)
                        CameraController(context, lifecycleOwner, previewView, overlay, null).also { ctrl ->
                            ctrl.onCameraBound = { cameraStreaming = true }
                            ctrl.start()
                        }
                    } else null
                    cameraController = controller
                    // 超时兜底
                    val timeoutJob = scope.launch {
                        kotlinx.coroutines.delay(5000)
                        if (!cameraStreaming) cameraStreaming = true
                    }
                    onDispose {
                        timeoutJob.cancel()
                        controller?.stop()
                    }
                }

                // 模型就绪后补绑识别分析
                LaunchedEffect(modelReady) {
                    if (modelReady) {
                        val an = viewModel.getAnalyser()
                        if (an != null) cameraController?.attachAnalyser(an)
                    }
                }

                // 未就绪遮罩
                if (!cameraActive) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
                            )
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                !hasPermission -> "需要相机权限"
                                !cameraStreaming -> "正在启动摄像头…"
                                else -> "正在加载模型…"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 人脸检测状态浮层
                if (cameraActive) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (faceDetected) Brush.horizontalGradient(SuccessGradient.map { it.copy(alpha = 0.9f) })
                                else Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.7f)))
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (faceDetected) Color.White else Warning))
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

                // 前后摄像头切换按钮
                if (cameraActive) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { cameraController?.switchCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Cameraswitch,
                            contentDescription = "切换摄像头",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ── 下方可滚动区域：表单 + 人员列表 ──
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            // 录入表单
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        var classExpanded by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = enrollName,
                            onValueChange = { viewModel.setEnrollName(it) },
                            label = { Text("姓名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        ExposedDropdownMenuBox(
                            expanded = classExpanded,
                            onExpandedChange = { classExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = enrollClass,
                                onValueChange = { viewModel.setEnrollClass(it); classExpanded = true },
                                label = { Text("班级（可输入或选择）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = classExpanded,
                                onDismissRequest = { classExpanded = false }
                            ) {
                                if (classes.isEmpty()) {
                                    Text("暂无班级，请输入新建", Modifier.padding(12.dp))
                                }
                                classes.forEach { cls ->
                                    Text(
                                        cls,
                                        Modifier.menuAnchor().fillMaxWidth().padding(12.dp).clickable {
                                            viewModel.setEnrollClass(cls)
                                            classExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        val ratio = progress.toFloat() / AttendanceViewModel.ENROLL_SAMPLE_COUNT
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("采集进度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$progress / ${AttendanceViewModel.ENROLL_SAMPLE_COUNT}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.collectEnrollSample() },
                                enabled = canCollect,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("采集")
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelEnroll() },
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("重置") }
                        }

                        if (!faceDetected && cameraActive) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "⚠ 请先让摄像头检测到人脸后再点击采集",
                                style = MaterialTheme.typography.labelSmall,
                                color = Warning
                            )
                        }
                        if (enrollName.isBlank() && cameraActive) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⚠ 请输入姓名",
                                style = MaterialTheme.typography.labelSmall,
                                color = Warning
                            )
                        }
                    }
                }
            }

            // 批量导入名单按钮
            item {
                var showBatchDialog by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showBatchDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("批量导入名单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("先输入姓名名单，后期再补录人脸", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showBatchDialog) {
                    BatchImportDialog(
                        viewModel = viewModel,
                        onDismiss = { showBatchDialog = false }
                    )
                }
            }

            // 已录入人员列表标题
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("已录入人员 (${persons.size})", style = MaterialTheme.typography.titleMedium)
                }
            }

            items(persons, key = { it.id }) { p ->
                var cardBounds by remember { mutableStateOf(android.graphics.Rect()) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            cardBounds = android.graphics.Rect(
                                pos.x.toInt(), pos.y.toInt(),
                                (pos.x + coords.size.width).toInt(),
                                (pos.y + coords.size.height).toInt()
                            )
                        }
                        .clickable { onOpenDetail(cardBounds); viewModel.openDetail(p) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.5.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(Brush.horizontalGradient(PrimaryGradient.map { it.copy(alpha = 0.15f) })),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                if (p.className.isNotBlank()) {
                                    Text(p.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                // 区分已录入特征和仅导入名单的人员
                                if (p.hasFaceEmbedding) {
                                    Text("点击查看特征图片", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("⚠ 未录入人脸，点击补录", style = MaterialTheme.typography.labelSmall, color = Warning)
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.deletePerson(p.id) },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(130.dp)) }
        }
    }
}

/**
 * 批量导入名单对话框 — 支持一次输入多个姓名（每行一个），统一归属到所选班级。
 * 仅导入姓名，不录入人脸特征；后期可在录入页逐人补录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchImportDialog(
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf("") }
    val classes by viewModel.classes.collectAsState()
    var classExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量导入名单") },
        text = {
            Column {
                Text(
                    "每行输入一个姓名，可先导入名单后期再补录人脸",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("姓名列表（每行一个）") },
                    placeholder = { Text("张三\n李四\n王五") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = classExpanded,
                    onExpandedChange = { classExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClass,
                        onValueChange = { selectedClass = it; classExpanded = true },
                        label = { Text("班级（可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false }
                    ) {
                        Text(
                            "不分配班级",
                            Modifier.menuAnchor().fillMaxWidth().padding(12.dp).clickable {
                                selectedClass = ""
                                classExpanded = false
                            }
                        )
                        classes.forEach { cls ->
                            Text(
                                cls,
                                Modifier.menuAnchor().fillMaxWidth().padding(12.dp).clickable {
                                    selectedClass = cls
                                    classExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val names = inputText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (names.isNotEmpty()) {
                        viewModel.batchAddPersons(names, selectedClass)
                        onDismiss()
                    }
                },
                enabled = inputText.lines().any { it.trim().isNotEmpty() }
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}