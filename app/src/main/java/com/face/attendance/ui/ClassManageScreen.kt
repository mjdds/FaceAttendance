package com.face.attendance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.face.attendance.store.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassManageScreen(
    viewModel: AttendanceViewModel,
    onBack: () -> Unit
) {
    val persons by viewModel.persons.collectAsState()
    val classes by viewModel.classes.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshPersons() }

    // 按班级分组（空字符串归入"未分班"）
    val grouped = remember(persons) {
        val map = persons.groupBy { it.className }
        // 有名称的班级按字母排序 + 未分班放最后
        val named = map.keys.filter { it.isNotBlank() }.sorted()
        val unclassified = map[""] ?: emptyList()
        named.map { cls -> cls to (map[cls] ?: emptyList()) } +
            (if (unclassified.isNotEmpty()) listOf("" to unclassified) else emptyList())
    }

    // 对话框状态
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var moveTarget by remember { mutableStateOf<Person?>(null) }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarsPadding)
    ) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("班级管理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }

        // ── 说明 ──
        Text(
            "点击班级展开查看成员，可重命名班级或调整成员所属班级",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        if (persons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Group, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("暂无人员", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp)
            ) {
                items(grouped, key = { it.first }) { (cls, members) ->
                    ClassCard(
                        className = cls,
                        members = members,
                        allClasses = classes,
                        onRename = { renameTarget = cls },
                        onMovePerson = { moveTarget = it }
                    )
                }
            }
        }
    }

    // 重命名对话框
    var renameInput by remember { mutableStateOf("") }
    LaunchedEffect(renameTarget) { renameInput = renameTarget ?: "" }
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名班级") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("新班级名称") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameInput.isNotBlank()) {
                        viewModel.renameClass(renameTarget!!, renameInput)
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // 调整人员班级对话框
    var moveSelectedClass by remember { mutableStateOf("") }
    LaunchedEffect(moveTarget) { moveSelectedClass = moveTarget?.className ?: "" }
    if (moveTarget != null) {
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("调整「${moveTarget!!.name}」的班级") },
            text = {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = moveSelectedClass,
                        onValueChange = { moveSelectedClass = it; expanded = true },
                        label = { Text("选择或输入班级") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Text(
                            "未分班",
                            Modifier.menuAnchor().fillMaxWidth().padding(12.dp).clickable {
                                moveSelectedClass = ""
                                expanded = false
                            }
                        )
                        classes.forEach { c ->
                            Text(
                                c,
                                Modifier.menuAnchor().fillMaxWidth().padding(12.dp).clickable {
                                    moveSelectedClass = c
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePersonClass(moveTarget!!.id, moveSelectedClass)
                    moveTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ClassCard(
    className: String,
    members: List<Person>,
    allClasses: List<String>,
    onRename: () -> Unit,
    onMovePerson: (Person) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = if (className.isBlank()) "未分班" else className

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // 班级标题行
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(PrimaryGradient.map { it.copy(alpha = 0.15f) })),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${members.size} 人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 重命名按钮（未分班不可重命名）
                if (className.isNotBlank()) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Filled.Edit, contentDescription = "重命名", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "展开"
                    )
                }
            }

            // 展开后的人员列表
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    members.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.name.take(1).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            // 调整班级按钮
                            TextButton(onClick = { onMovePerson(p) }) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("调整")
                            }
                        }
                    }
                }
            }
        }
    }
}