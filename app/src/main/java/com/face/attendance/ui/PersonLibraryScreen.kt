package com.face.attendance.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.face.attendance.store.Person

/**
 * 已录入人脸页：按班级/姓名筛选查看所有人员，点击卡片查看特征详情，
 * 可直接编辑每个人的姓名与班级（编辑不触碰人脸特征数据）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonLibraryScreen(
    viewModel: AttendanceViewModel,
    onOpenDetail: (android.graphics.Rect) -> Unit
) {
    val persons by viewModel.persons.collectAsState()
    val classes by viewModel.classes.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshPersons() }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // null = 全部班级；空串 = 未分班
    var selectedClass by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Person?>(null) }

    val hasUnassigned = remember(persons) { persons.any { it.className.isBlank() } }
    val filtered = remember(persons, selectedClass, query) {
        val q = query.trim()
        persons.filter { p ->
            (selectedClass == null || p.className == selectedClass) &&
                (q.isBlank() || p.name.contains(q, ignoreCase = true) ||
                    p.className.contains(q, ignoreCase = true))
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarsPadding)
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                Text("已录入人脸", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "共 ${persons.size} 人，当前显示 ${filtered.size} 人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 姓名/班级搜索
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索姓名或班级") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // 班级筛选
        if (classes.isNotEmpty() || hasUnassigned) {
            item {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedClass == null,
                            onClick = { selectedClass = null },
                            label = { Text("全部班级") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    items(classes) { cls ->
                        FilterChip(
                            selected = selectedClass == cls,
                            onClick = { selectedClass = cls },
                            label = { Text(cls) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    if (hasUnassigned) {
                        item {
                            FilterChip(
                                selected = selectedClass != null && selectedClass!!.isBlank(),
                                onClick = { selectedClass = "" },
                                label = { Text("未分班") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(80.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Inbox, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (persons.isEmpty()) "尚未录入人员" else "没有匹配的人员",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { p ->
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.horizontalGradient(PrimaryGradient.map { it.copy(alpha = 0.15f) })),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (p.className.isNotBlank()) {
                                Text(p.className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                if (p.hasFaceEmbedding) "已录入人脸 · 点击查看特征" else "⚠ 未录入人脸，点击补录",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.hasFaceEmbedding) MaterialTheme.colorScheme.onSurfaceVariant else Warning
                            )
                        }
                        IconButton(onClick = { editing = p }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑个人信息")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(130.dp)) }
        }
    }

    editing?.let { p ->
        EditPersonDialog(
            person = p,
            classOptions = classes,
            onDismiss = { editing = null },
            onSave = { name, cls ->
                viewModel.savePersonInfo(p.id, name, cls)
                editing = null
            }
        )
    }
}

/**
 * 编辑人员姓名/班级对话框。班级可输入新值或从已有班级中选择；
 * 仅修改文字信息，人脸特征向量与样本图片完全不动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPersonDialog(
    person: Person,
    classOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, className: String) -> Unit
) {
    var name by remember { mutableStateOf(person.name) }
    var cls by remember { mutableStateOf(person.className) }
    var classExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑个人信息") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = classExpanded,
                    onExpandedChange = { classExpanded = it }
                ) {
                    OutlinedTextField(
                        value = cls,
                        onValueChange = { cls = it; classExpanded = true },
                        label = { Text("班级（可输入或选择）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false }
                    ) {
                        if (classOptions.isEmpty()) {
                            Text("暂无班级，请输入新建", Modifier.padding(12.dp))
                        }
                        classOptions.forEach { c ->
                            Text(
                                c,
                                Modifier.fillMaxWidth().padding(12.dp).clickable {
                                    cls = c
                                    classExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "人脸特征数据不会被修改",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, cls) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
