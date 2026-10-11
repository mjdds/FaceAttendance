package com.face.attendance.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.face.attendance.tts.VoiceOption
import com.face.attendance.tts.availableVoices
import kotlinx.coroutines.launch

/**
 * 语音模型选择页 —— 多提供商多款免费语音模型可切换，支持逐个试听。
 * 提供商：百度翻译、有道词典、百度智能云、微软 Edge、系统离线。
 */
@Composable
fun VoiceSelectScreen(
    viewModel: AttendanceViewModel,
    onBack: () -> Unit
) {
    val currentVoice by viewModel.tts.currentVoice.collectAsState()
    val voices = remember { availableVoices() }
    val scope = rememberCoroutineScope()
    var testingId by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var pendingVoice by remember { mutableStateOf<VoiceOption?>(null) }

    // 百度AIP已配置的密钥
    val (savedApiKey, savedSecretKey) = remember { viewModel.tts.getBaiduAipCredentials() }
    var apiKeyInput by remember { mutableStateOf(savedApiKey) }
    var secretKeyInput by remember { mutableStateOf(savedSecretKey) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
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
                "选择语音模型",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Text(
            "共 ${voices.size} 款语音 · 5 家提供商 · 点击试听 · 选中后自动保存",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 按提供商分组
            val grouped = voices.groupBy { it.provider }
            grouped.forEach { (provider, providerVoices) ->
                item {
                    Text(
                        provider,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                items(providerVoices, key = { it.id }) { voice ->
                    val selected = voice.id == currentVoice.id
                    val testing = testingId == voice.id
                    val engineColors = engineColor(voice.engine)
                    val tagInfo = engineTag(voice.engine)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (voice.needsApiKey && !viewModel.tts.getBaiduAipCredentials().first.isNotBlank()) {
                                    pendingVoice = voice
                                    showApiKeyDialog = true
                                } else {
                                    viewModel.tts.setVoice(voice)
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selected) androidx.compose.foundation.BorderStroke(
                            2.dp, MaterialTheme.colorScheme.primary
                        ) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(engineColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (voice.needsApiKey) Icons.Filled.Key else Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(Modifier.size(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        voice.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(engineColors[0].copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            tagInfo,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = engineColors[0],
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    voice.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    voice.gender,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (voice.needsApiKey) {
                                    val configured = viewModel.tts.getBaiduAipCredentials().first.isNotBlank()
                                    // 配置后此行变为“修改”入口：点击重弹对话框编辑密钥，
                                    // pendingVoice=null 表示仅改密钥不切换语音
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                pendingVoice = if (configured) null else voice
                                                showApiKeyDialog = true
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (configured) "✓ API密钥已配置" else "⚠ 需配置API密钥（点击设置）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (configured) Color(0xFF34D399) else Color(0xFFF59E0B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (configured) {
                                            Spacer(Modifier.size(6.dp))
                                            Text(
                                                "修改",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                            )
                                        }
                                    }
                                }
                            }

                            // 试听按钮
                            IconButton(
                                onClick = {
                                    if (testingId == null) {
                                        if (voice.needsApiKey && !viewModel.tts.getBaiduAipCredentials().first.isNotBlank()) {
                                            pendingVoice = voice
                                            showApiKeyDialog = true
                                        } else {
                                            testingId = voice.id
                                            viewModel.tts.setVoice(voice)
                                            scope.launch {
                                                viewModel.tts.testSpeak()
                                                testingId = null
                                            }
                                        }
                                    }
                                },
                                enabled = testingId == null
                            ) {
                                if (testing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "试听",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "已选",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // 百度智能云 API 密钥配置对话框
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = {
                showApiKeyDialog = false
                pendingVoice = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("配置百度智能云密钥")
                }
            },
            text = {
                Column {
                    Text(
                        "在 cloud.baidu.com 注册并创建「语音技术」应用，获取 API Key 和 Secret Key。标准语音每日免费 5 万次。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = secretKeyInput,
                        onValueChange = { secretKeyInput = it },
                        label = { Text("Secret Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (apiKeyInput.isNotBlank() && secretKeyInput.isNotBlank()) {
                            viewModel.tts.setBaiduAipCredentials(apiKeyInput.trim(), secretKeyInput.trim())
                            pendingVoice?.let { viewModel.tts.setVoice(it) }
                            showApiKeyDialog = false
                            pendingVoice = null
                        }
                    }
                ) { Text(if (pendingVoice != null) "保存并使用" else "保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showApiKeyDialog = false
                    pendingVoice = null
                }) { Text("取消") }
            }
        )
    }
}

/** 引擎对应的渐变色 */
private fun engineColor(engine: String): List<Color> = when (engine) {
    VoiceOption.ENGINE_BAIDU_TRANSLATE -> listOf(Color(0xFF2932E1), Color(0xFF4A6CFF))
    VoiceOption.ENGINE_YOUDAO -> listOf(Color(0xFFE60012), Color(0xFFFF6B6B))
    VoiceOption.ENGINE_BAIDU_AIP -> listOf(Color(0xFF00B38A), Color(0xFF00D4AA))
    VoiceOption.ENGINE_EDGE -> listOf(Color(0xFF5B6CFF), Color(0xFFA78BFA))
    VoiceOption.ENGINE_SYSTEM -> listOf(Color(0xFF34D399), Color(0xFF2DD4BF))
    else -> listOf(Color(0xFF64748B), Color(0xFF94A3B8))
}

/** 引擎对应的标签 */
private fun engineTag(engine: String): String = when (engine) {
    VoiceOption.ENGINE_BAIDU_TRANSLATE -> "免费"
    VoiceOption.ENGINE_YOUDAO -> "免费"
    VoiceOption.ENGINE_BAIDU_AIP -> "需Key"
    VoiceOption.ENGINE_EDGE -> "在线"
    VoiceOption.ENGINE_SYSTEM -> "离线"
    else -> "其他"
}
