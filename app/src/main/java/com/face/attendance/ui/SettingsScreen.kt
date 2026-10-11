package com.face.attendance.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.face.attendance.tts.VoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页 —— 显示语音引擎状态、语音模型选择入口、测试播报。
 */
@Composable
fun SettingsScreen(
    viewModel: AttendanceViewModel,
    onBack: () -> Unit,
    onOpenVoiceSelect: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val currentVoice by viewModel.tts.currentVoice.collectAsState()
    val edgeAvailable by viewModel.tts.edgeAvailable.collectAsState()
    val engineAvailable by viewModel.tts.engineAvailable.collectAsState()
    val systemReady by viewModel.tts.systemReady.collectAsState()
    val lastStatus by viewModel.tts.lastStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }

    // 数据导出：创建 JSON 文件并写入全部人员/特征/记录
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) { viewModel.exportData(uri) }
        }
    }
    // 数据导入：选择 JSON 文件，按姓名合并（同名跳过，不覆盖已有数据）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importing = true
            scope.launch(Dispatchers.IO) {
                viewModel.importData(uri)
                importing = false
            }
        }
    }

    // 动态读取当前安装版本，避免与 build.gradle 版本号脱节
    val context = LocalContext.current
    val versionInfo = remember {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt()
            else @Suppress("DEPRECATION") pi.versionCode
            "${pi.versionName} (build $code)"
        } catch (e: Exception) {
            "未知"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── 语音播报分区 ──
        SectionTitle("语音播报")

        // 当前语音模型卡片
        SettingCard(
            icon = Icons.Filled.RecordVoiceOver,
            title = "语音模型",
            subtitle = "${currentVoice.displayName} · ${currentVoice.description}",
            trailing = Icons.Filled.ChevronRight,
            onClick = onOpenVoiceSelect
        )

        Spacer(Modifier.height(12.dp))

        // 引擎状态卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "引擎状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                StatusRow(
                    label = "当前引擎",
                    ok = edgeAvailable || engineAvailable,
                    okText = "可用",
                    failText = "不可用（请重新检测或切换其他语音）"
                )
                Spacer(Modifier.height(10.dp))
                StatusRow(
                    label = "系统离线语音",
                    ok = systemReady,
                    okText = "就绪",
                    failText = "未就绪"
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "最近状态：$lastStatus",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                viewModel.tts.recheck()
                                checking = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !checking,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (checking) "检测中…" else "重新检测引擎", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 测试播报按钮
        Button(
            onClick = {
                if (!testing) {
                    testing = true
                    scope.launch {
                        viewModel.tts.testSpeak()
                        testing = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !testing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
                Text("测试中…")
            } else {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("测试播报", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── 性能优化分区 ──
        SectionTitle("性能优化")
        var lowPerf by remember {
            mutableStateOf(com.face.attendance.util.PerformanceManager.lowPerformanceMode)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable {
                    lowPerf = !lowPerf
                    com.face.attendance.util.PerformanceManager.setLowPerformance(context, lowPerf)
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "省流模式（低端设备推荐）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "关闭液态玻璃特效、降低相机帧率与识别分辨率、减少推理频率并降低内存占用，大幅减小闪退概率。切换页面或重启后完全生效。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(10.dp))
                androidx.compose.material3.Switch(
                    checked = lowPerf,
                    onCheckedChange = {
                        lowPerf = it
                        com.face.attendance.util.PerformanceManager.setLowPerformance(context, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── 数据管理分区 ──
        SectionTitle("数据管理")
        SettingCard(
            icon = Icons.Filled.IosShare,
            title = "导出数据",
            subtitle = "导出全部人员特征与签到记录，可跨设备迁移",
            trailing = Icons.Filled.ChevronRight,
            onClick = {
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                exportLauncher.launch("face_attendance_$ts.json")
            }
        )
        Spacer(Modifier.height(10.dp))
        SettingCard(
            icon = Icons.Filled.FileDownload,
            title = "导入数据",
            subtitle = "从备份文件合并导入（同名自动跳过，不覆盖已有数据）",
            trailing = Icons.Filled.ChevronRight,
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
        )
        Spacer(Modifier.height(10.dp))
        SettingCard(
            icon = Icons.Filled.CloudUpload,
            title = "立即备份到本机",
            subtitle = "写入手机「下载/智能刷脸考勤」，卸载应用也不会删除",
            trailing = Icons.Filled.ChevronRight,
            onClick = { viewModel.manualBackup() }
        )
        Spacer(Modifier.height(10.dp))
        SettingCard(
            icon = Icons.Filled.Restore,
            title = "从本机备份恢复",
            subtitle = "重装后找回人员特征与签到记录（合并，不覆盖现有数据）",
            trailing = Icons.Filled.ChevronRight,
            onClick = { viewModel.restoreFromAutoBackup() }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "每次离开应用时会自动备份到「下载/智能刷脸考勤/auto_backup.json」；卸载后重装，打开应用会自动提示恢复。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(28.dp))

        // ── 关于分区 ──
        SectionTitle("关于")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                AboutRow("应用名称", "智能刷脸考勤")
                Spacer(Modifier.height(8.dp))
                AboutRow("版本", versionInfo)
                Spacer(Modifier.height(8.dp))
                AboutRow("识别引擎", "FaceNet 端侧推理")
                Spacer(Modifier.height(8.dp))
                AboutRow("语音引擎", "百度翻译·有道·百度云·Edge·系统")
                Spacer(Modifier.height(8.dp))
                AboutRow("最近更新", "设置新增「开源致谢」")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 致谢入口 ──
        SectionTitle("致谢")
        SettingCard(
            icon = Icons.Filled.Favorite,
            title = "开源致谢",
            subtitle = "第三方开源组件、商标与开发声明",
            trailing = Icons.Filled.ChevronRight,
            onClick = { showCredits = true }
        )

        Spacer(Modifier.height(28.dp))

        // ── 重播引导页 ──
        SectionTitle("引导与帮助")
        SettingCard(
            icon = Icons.Outlined.PlayCircle,
            title = "重播引导页",
            subtitle = "重新观看应用介绍与用户协议动画",
            trailing = Icons.Filled.ChevronRight,
            onClick = onReplayOnboarding
        )

        Spacer(Modifier.height(40.dp))
    }

    if (showCredits) {
        CreditsDialog(onDismiss = { showCredits = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, bottom = 10.dp, top = 4.dp)
    )
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(trailing, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, failText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Color(0xFF34D399) else Color(0xFFF87171))
        )
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (ok) okText else failText,
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) Color(0xFF34D399) else Color(0xFFF87171),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private data class CreditEntry(val name: String, val detail: String)

@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    val credits = listOf(
        CreditEntry("Android Jetpack", "AndroidX、Compose、Lifecycle、CameraX · Apache-2.0"),
        CreditEntry("Material Components for Android", "Google · Apache-2.0"),
        CreditEntry("ML Kit Face Detection", "Google · Apache-2.0（检测模型经 Google Play 服务动态下发）"),
        CreditEntry("TensorFlow Lite", "The TensorFlow Authors · Apache-2.0"),
        CreditEntry("Kotlin Coroutines", "JetBrains · Apache-2.0"),
        CreditEntry("OkHttp", "Square · Apache-2.0"),
        CreditEntry("AndroidLiquidGlass", "Kyant0 · Apache-2.0（液态玻璃视觉效果）")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("致谢") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "本应用基于以下开源软件构建，谨向各项目作者致以谢意。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                credits.forEach { entry ->
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(2.dp))
                CreditsNote("各组件许可证全文，以及人脸模型、在线语音服务的完整声明，见开源仓库中的 NOTICE 文件。")
                Spacer(Modifier.height(8.dp))
                CreditsNote("“Liquid Glass”为 Apple Inc. 的商标。本应用为独立第三方作品，与 Apple Inc. 无任何关联，亦未获其认可或赞助。")
                Spacer(Modifier.height(8.dp))
                CreditsNote("本应用由 AI 编程助手辅助开发，由维护者负责测试、审核与发布。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun CreditsNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
