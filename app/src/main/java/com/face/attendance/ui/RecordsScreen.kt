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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.face.attendance.store.Record
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 日期筛选模式：单日 / 区间 / 全部 */
private enum class DayMode(val label: String) {
    SINGLE("单日"), RANGE("区间"), ALL("全部")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(viewModel: AttendanceViewModel) {
    val records by viewModel.records.collectAsState()
    val persons by viewModel.persons.collectAsState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        viewModel.refreshRecords()
        viewModel.refreshPersons()
    }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // ── 日期筛选状态 ──
    var dayMode by remember { mutableStateOf(DayMode.SINGLE) }
    var selectedDay by remember { mutableStateOf(startOfToday()) }
    var rangeStart by remember { mutableStateOf(startOfToday() - 7 * DAY_MS) }
    var rangeEnd by remember { mutableLongStateOf(startOfToday()) } // 0 表示至今
    // 日期选择器目标：0=单日，1=区间起，2=区间止
    var pickerTarget by remember { mutableStateOf<Int?>(null) }

    // ── 班级筛选：null 表示全部，空串表示未分班 ──
    var selectedClass by remember { mutableStateOf<String?>(null) }
    val classList = remember(persons) {
        persons.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val hasUnassigned = remember(persons) { persons.any { it.className.isBlank() } }

    // ── 人物筛选：null 表示全部人物 ──
    var selectedPersonId by remember { mutableStateOf<Long?>(null) }
    var personMenu by remember { mutableStateOf(false) }
    var personQuery by remember { mutableStateOf("") }

    // name → className 映射：历史记录未固化班级（旧数据）时兜底反查
    val nameToClass = remember(persons) {
        persons.associate { it.name to it.className }
    }

    // 人物下拉候选：先受班级筛选约束，再按姓名/班级搜索
    val personCandidates = remember(persons, selectedClass, personQuery) {
        val q = personQuery.trim()
        persons.filter { p ->
            (selectedClass == null || p.className == selectedClass) &&
                (q.isBlank() || p.name.contains(q, ignoreCase = true) ||
                    p.className.contains(q, ignoreCase = true))
        }.sortedWith(compareBy({ it.className }, { it.name }))
    }

    // 切换班级后，已选人物不属于该班则清空人物选择
    LaunchedEffect(selectedClass, persons) {
        val id = selectedPersonId
        if (id != null) {
            val p = persons.firstOrNull { it.id == id }
            if (p == null || (selectedClass != null && p.className != selectedClass)) {
                selectedPersonId = null
            }
        }
    }

    val dayFmt = remember { SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault()) }
    val rangeFmt = remember { SimpleDateFormat("MM月dd日", Locale.getDefault()) }

    val filtered = records.filter { r ->
        val matchDay = when (dayMode) {
            DayMode.SINGLE -> startOfDayOf(r.timestamp) == selectedDay
            DayMode.RANGE -> r.timestamp >= rangeStart &&
                (rangeEnd == 0L || r.timestamp < rangeEnd + DAY_MS)
            DayMode.ALL -> true
        }
        val recClass = r.className.ifBlank { nameToClass[r.name] ?: "" }
        val matchClass = selectedClass == null || recClass == selectedClass
        val matchPerson = selectedPersonId == null || r.personId == selectedPersonId
        matchDay && matchClass && matchPerson
    }

    val selectedPersonName = persons.firstOrNull { it.id == selectedPersonId }?.name

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarsPadding)
    ) {
        // ── 标题 ──
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                Text("考勤记录", style = MaterialTheme.typography.headlineMedium)
                Text(
                    buildString {
                        append("共 ${filtered.size} 条记录")
                        val conds = mutableListOf<String>()
                        when (dayMode) {
                            DayMode.SINGLE -> conds += dayFmt.format(Date(selectedDay))
                            DayMode.RANGE -> conds += if (rangeEnd == 0L)
                                "${rangeFmt.format(Date(rangeStart))} 起至今"
                            else
                                "${rangeFmt.format(Date(rangeStart))} ~ ${rangeFmt.format(Date(rangeEnd))}"
                            DayMode.ALL -> {}
                        }
                        selectedClass?.let { conds += if (it.isBlank()) "未分班" else it }
                        selectedPersonName?.let { conds += it }
                        if (conds.isNotEmpty()) append(" · ").append(conds.joinToString(" · "))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 日期模式切换 ──
        item {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DayMode.entries) { mode ->
                    FilterChip(
                        selected = dayMode == mode,
                        onClick = { dayMode = mode },
                        label = { Text(mode.label) },
                        leadingIcon = if (mode == DayMode.RANGE) {
                            { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // ── 单日选择器 ──
        if (dayMode == DayMode.SINGLE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { selectedDay -= DAY_MS }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "前一天")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { pickerTarget = 0 }
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = "选择日期",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                dayFmt.format(Date(selectedDay)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedDay += DAY_MS },
                                enabled = selectedDay < startOfToday()
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "后一天")
                            }
                            TextButton(onClick = { selectedDay = startOfToday() }) { Text("今天") }
                        }
                    }
                }
            }
        }

        // ── 区间选择器 ──
        if (dayMode == DayMode.RANGE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        TextButton(onClick = { pickerTarget = 1 }) {
                            Text(rangeFmt.format(Date(rangeStart)), fontWeight = FontWeight.SemiBold)
                        }
                        Text("～", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { pickerTarget = 2 }) {
                            Text(
                                if (rangeEnd == 0L) "至今" else rangeFmt.format(Date(rangeEnd)),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { rangeEnd = 0L }) { Text("至今") }
                    }
                }
            }
        }

        // ── 班级筛选 ──
        if (classList.isNotEmpty() || hasUnassigned) {
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
                    items(classList) { cls ->
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

        // ── 人物筛选（内联展开面板，不使用 Popup/DropdownMenu）──
        // 说明：原先用 DropdownMenu（独立 Popup 窗口）+ 搜索框，点击选项时 Popup dismiss
        // 与本 LazyColumn 因筛选结果变化而重组发生在同一帧，在部分机型上会因焦点/输入法解绑
        // 或 anchor 重布局而闪退，故改为同一窗口内的普通展开面板。
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        personQuery = ""
                        personMenu = !personMenu
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            selectedPersonName ?: "全部人物",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedPersonName == null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "选择人物",
                            modifier = Modifier.rotate(if (personMenu) 180f else 0f)
                        )
                    }
                }

                if (personMenu) {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            OutlinedTextField(
                                value = personQuery,
                                onValueChange = { personQuery = it },
                                placeholder = { Text("搜索姓名或班级") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            // 外层是 LazyColumn（子项最大高度约束为无限），
                            // 必须用 heightIn 给出有限高度，内部纵向滚动才不会抛测量异常
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                PersonPickRow(
                                    title = "全部人物",
                                    subtitle = null,
                                    selected = selectedPersonId == null,
                                    onClick = {
                                        focusManager.clearFocus()
                                        selectedPersonId = null
                                        personQuery = ""
                                        personMenu = false
                                    }
                                )
                                HorizontalDivider()
                                if (personCandidates.isEmpty()) {
                                    Text(
                                        "无匹配人员",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                personCandidates.forEach { p ->
                                    PersonPickRow(
                                        title = p.name,
                                        subtitle = p.className.ifBlank { null },
                                        selected = selectedPersonId == p.id,
                                        onClick = {
                                            focusManager.clearFocus()
                                            selectedPersonId = p.id
                                            personQuery = ""
                                            personMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 单日未签到摘要（仅单日模式） ──
        if (dayMode == DayMode.SINGLE) {
            item {
                val checkedNames = filtered.map { it.name }.distinct()
                // 未签到统计范围跟随当前筛选：全部人员 / 指定班级 / 指定人物
                val scopeNames = persons.filter { p ->
                    (selectedClass == null || p.className == selectedClass) &&
                        (selectedPersonId == null || p.id == selectedPersonId)
                }.map { it.name }
                val absentNames = scopeNames.filterNot { it in checkedNames }
                val allChecked = scopeNames.isNotEmpty() && absentNames.isEmpty()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(
                                when {
                                    scopeNames.isEmpty() -> SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                    absentNames.isEmpty() -> Brush.horizontalGradient(SuccessGradient.map { it.copy(alpha = 0.2f) })
                                    else -> SolidColor(Warning.copy(alpha = 0.18f))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${absentNames.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                scopeNames.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                                absentNames.isEmpty() -> Success
                                else -> Warning
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            scopeNames.isEmpty() -> "当日暂无可统计人员"
                            allChecked -> "当日全员已签到"
                            else -> "当日未签到：${absentNames.joinToString("、")}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { r ->
                val cls = r.className.ifBlank { nameToClass[r.name] ?: "" }
                RecordCard(r, showDate = dayMode != DayMode.SINGLE, className = cls)
            }
            item { Spacer(Modifier.height(130.dp)) }
        }
    }

    // 日期选择对话框（Material3 的 millis 基于 UTC 午夜，需与本地时区的“当天零点”互转）
    val target = pickerTarget
    if (target != null) {
        val initial = when (target) {
            0 -> localDayToUtc(selectedDay)
            1 -> localDayToUtc(rangeStart)
            else -> if (rangeEnd == 0L) localDayToUtc(startOfToday()) else localDayToUtc(rangeEnd)
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = utcToLocalDay(utc)
                        when (target) {
                            0 -> selectedDay = day
                            1 -> {
                                rangeStart = day
                                if (rangeEnd != 0L && rangeEnd < day) rangeEnd = day
                            }
                            2 -> rangeEnd = day
                        }
                    }
                    pickerTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) { Text("取消") }
            }
        ) { DatePicker(state = state) }
    }
}

/** 人物筛选面板中的一行：姓名 + 班级副标题，选中时右侧显示对勾 */
@Composable
private fun PersonPickRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecordCard(r: Record, showDate: Boolean, className: String = "") {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.timestamp))
    val date = SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault()).format(Date(r.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(SuccessGradient.map { it.copy(alpha = 0.18f) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (className.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(className, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(if (showDate) "$date · $time" else time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Success.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(r.action, color = Success, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun startOfToday(): Long = startOfDayOf(System.currentTimeMillis())

private fun startOfDayOf(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** 本地时区的“当天零点” → Material3 DatePicker 使用的 UTC 午夜毫秒 */
private fun localDayToUtc(localDay: Long): Long {
    val lc = Calendar.getInstance().apply { timeInMillis = localDay }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(lc.get(Calendar.YEAR), lc.get(Calendar.MONTH), lc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Material3 DatePicker 的 UTC 午夜毫秒 → 本地时区的“当天零点” */
private fun utcToLocalDay(utcMillis: Long): Long {
    val uc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(uc.get(Calendar.YEAR), uc.get(Calendar.MONTH), uc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
