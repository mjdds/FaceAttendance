package com.face.attendance.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.face.attendance.face.FaceNetModel
import com.face.attendance.face.FrameAnalyser
import com.face.attendance.face.ModelInfo
import com.face.attendance.store.ACTION_CHECK_IN
import com.face.attendance.store.AttendanceDb
import com.face.attendance.store.AutoBackup
import com.face.attendance.store.FaceSample
import com.face.attendance.store.Person
import com.face.attendance.store.Record
import com.face.attendance.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

class AttendanceViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx: Application = app
    private val db = AttendanceDb(app)
    private var faceNet: FaceNetModel? = null
    private var analyser: FrameAnalyser? = null

    // ── 卸载不丢：自动备份到公共「下载/智能刷脸考勤」，重装后提示恢复 ──
    private val _pendingRestore = MutableStateFlow<AutoBackup.Ref?>(null)
    val pendingRestore: StateFlow<AutoBackup.Ref?> = _pendingRestore.asStateFlow()
    private var backupJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastBackupSig: String? = null
    val tts = TtsService(app)

    private val _persons = MutableStateFlow<List<Person>>(emptyList())
    val persons: StateFlow<List<Person>> = _persons.asStateFlow()

    private val _records = MutableStateFlow<List<Record>>(emptyList())
    val records: StateFlow<List<Record>> = _records.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _lastRecognized = MutableStateFlow<RecognizedInfo?>(null)
    val lastRecognized: StateFlow<RecognizedInfo?> = _lastRecognized.asStateFlow()

    private val _modelReady = MutableStateFlow(false)
    val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    private val _enrollName = MutableStateFlow("")
    val enrollName: StateFlow<String> = _enrollName.asStateFlow()

    // 录入时所选班级
    private val _enrollClass = MutableStateFlow("")
    val enrollClass: StateFlow<String> = _enrollClass.asStateFlow()

    // 所有已使用的班级列表（供录入页下拉选择）
    private val _classes = MutableStateFlow<List<String>>(emptyList())
    val classes: StateFlow<List<String>> = _classes.asStateFlow()

    // 识别时按班级过滤：空字符串表示识别全部人员，否则只识别该班级
    private val _recognitionClass = MutableStateFlow("")
    val recognitionClass: StateFlow<String> = _recognitionClass.asStateFlow()

    private val _enrollProgress = MutableStateFlow(0)
    val enrollProgress: StateFlow<Int> = _enrollProgress.asStateFlow()

    /** 当前是否检测到人脸（供录入页判断能否采集） */
    private val _faceDetected = MutableStateFlow(false)
    val faceDetected: StateFlow<Boolean> = _faceDetected.asStateFlow()

    // ── 详情页状态 ──
    private val _detailPerson = MutableStateFlow<Person?>(null)
    val detailPerson: StateFlow<Person?> = _detailPerson.asStateFlow()

    private val _detailSamples = MutableStateFlow<List<FaceSample>>(emptyList())
    val detailSamples: StateFlow<List<FaceSample>> = _detailSamples.asStateFlow()

    // 录入样本：embedding + 人脸位图
    private val enrollSamples = ArrayList<Pair<FloatArray, Bitmap>>()
    private val enrollMutex = Mutex()

    // 防止同一人短时间重复打卡
    private val cooldownMutex = Mutex()

    // ── 语音播报去重：按 personId 记录已播报过的人员，本次会话内不再重复 ──
    private val announcedPersonIds = mutableSetOf<Long>()
    // 已播报集合记录的日期，跨天自动清空
    @Volatile private var announcedDate: Long = 0L
    // 上次 toast 文本与时间，用于防弹窗抖动
    @Volatile private var lastToastMsg: String? = null
    @Volatile private var lastToastTs: Long = 0L

    // 自动采集特征的频率/上限控制
    private val stableMutex = Mutex()
    private var stableLastName: String? = null
    private var stableLastTs: Long = 0L

    fun initModel(context: Context, overlayBinding: com.face.attendance.face.BoundingBoxOverlay) {
        if (_modelReady.value) {
            attachOverlay(overlayBinding)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // 尽早初始化系统 TTS（中文降级路径），不依赖模型加载完成
            tts.initSystemTts()
            try {
                val model = FaceNetModel(context, ModelInfo.FACENET, useGpu = false, useXNNPack = true)
                val an = FrameAnalyser(context, overlayBinding, model)
                an.setPersons(filteredPersons())
                an.onRecognized = { name, distance, bbox ->
                    handleRecognized(name, distance, bbox)
                }
                an.onFaceStateChange = { detected ->
                    _faceDetected.value = detected
                }
                an.onStableRecognized = { name, emb, bmp ->
                    handleStableRecognized(name, emb, bmp)
                }
                faceNet = model
                analyser = an
                _modelReady.value = true
                // 播报就绪，验证 TTS 链路通畅；若无声后续签到也不会响
                tts.speak("考勤系统已就绪")
            } catch (e: Exception) {
                Log.e("VM", "model init failed: ${e.message}")
                _toast.value = "模型加载失败: ${e.message}"
            }
        }
    }

    fun attachOverlay(overlay: com.face.attendance.face.BoundingBoxOverlay) {
        analyser?.let {
            it.boundingBoxOverlay = overlay
        }
    }

    fun getAnalyser(): FrameAnalyser? = analyser

    /** 切换到识别模式（默认） */
    fun setEnrollMode(on: Boolean) {
        analyser?.enrollMode = on
        if (!on) {
            enrollSamples.clear()
            _enrollProgress.value = 0
            analyser?.clearLastEmbedding()
            _faceDetected.value = false
        }
    }

    fun setEnrollName(v: String) { _enrollName.value = v }

    fun setEnrollClass(v: String) { _enrollClass.value = v }

    /** 设置识别时按班级过滤：空字符串表示识别全部，否则只识别该班级人员 */
    fun setRecognitionClass(className: String) {
        _recognitionClass.value = className
        // 切换班级后清除上一个识别结果，避免状态栏残留他人姓名
        _lastRecognized.value = null
        analyser?.setPersons(filteredPersons())
    }

    /** 按当前 recognitionClass 计算参与识别的人员名单（空字符串 = 全部） */
    private fun filteredPersons(): List<Person> {
        val cls = _recognitionClass.value
        return if (cls.isBlank()) _persons.value
        else _persons.value.filter { it.className == cls }
    }

    /** 在录入模式下采集一帧人脸 embedding + 人脸位图 */
    fun collectEnrollSample() {
        val an = analyser ?: run {
            _toast.value = "模型未就绪"
            return
        }
        if (!an.enrollMode) return
        // 必须检测到新鲜人脸才能采集
        if (!an.hasFreshEmbedding()) {
            _toast.value = "未检测到人脸，请正对镜头后再采集"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            enrollMutex.withLock {
                val lastEmbedding = an.lastEmbedding ?: return@withLock
                val lastBmp = an.lastFaceBitmap ?: return@withLock
                if (!an.hasFreshEmbedding()) {
                    _toast.value = "人脸已移出画面，请重新对准"
                    return@withLock
                }
                enrollSamples.add(lastEmbedding to lastBmp)
                _enrollProgress.value = enrollSamples.size
                if (enrollSamples.size >= ENROLL_SAMPLE_COUNT) {
                    finishEnroll()
                }
            }
        }
    }

    private suspend fun finishEnroll() {
        val name = _enrollName.value.trim()
        if (name.isEmpty()) {
            _toast.value = "请先输入姓名"
            return
        }
        if (enrollSamples.isEmpty()) {
            _toast.value = "未采集到人脸"
            return
        }
        // 取多个样本的平均作为该人员代表 embedding
        val dim = enrollSamples[0].first.size
        val avg = FloatArray(dim)
        for (s in enrollSamples) {
            for (i in 0 until dim) avg[i] += s.first[i]
        }
        for (i in 0 until dim) avg[i] /= enrollSamples.size
        val id = db.addPerson(name, avg, _enrollClass.value.trim())
        // 每张样本同时存入 face_samples（含人脸图），供详情页查看/删除
        for (s in enrollSamples) {
            val png = bitmapToPng(s.second)
            if (png != null) db.addFaceSample(id, s.first, png)
        }
        _persons.value = db.getPersons()
        analyser?.setPersons(_persons.value)
        enrollSamples.clear()
        _enrollProgress.value = 0
        _enrollName.value = ""
        _enrollClass.value = ""
        _classes.value = db.getClasses()
        scheduleAutoBackup()
        _toast.value = "已录入: $name"
    }

    fun cancelEnroll() {
        viewModelScope.launch {
            enrollMutex.withLock {
                enrollSamples.clear()
                _enrollProgress.value = 0
            }
        }
    }

    private fun handleRecognized(name: String, distance: Float, bbox: Rect) {
        _lastRecognized.value = RecognizedInfo(name, distance, bbox)
        viewModelScope.launch(Dispatchers.IO) {
            var msg: String? = null
            cooldownMutex.withLock {
                val now = System.currentTimeMillis()
                val person = _persons.value.firstOrNull { it.name == name } ?: return@withLock

                // 跨天清空已播报集合，允许次日重新播报
                val today = startOfToday()
                if (announcedDate != today) {
                    announcedPersonIds.clear()
                    announcedDate = today
                }

                // ★ 按用户ID去重：已播报过的人员本次会话内不再重复播报/弹窗
                if (person.id in announcedPersonIds) return@withLock

                if (db.hasCheckedToday(person.id, today)) {
                    msg = "$name 今日已签到"
                } else {
                    db.addRecord(person.id, name, now, ACTION_CHECK_IN, person.className)
                    _records.value = db.getRecords()
                    msg = "$name 签到成功"
                }
                announcedPersonIds.add(person.id)
            }
            // 锁外播报与提示，避免 TTS 网络等待阻塞签到流程
            val m = msg ?: return@launch
            // 弹窗防抖：相同消息 2 秒内不重复弹出
            val nowMs = System.currentTimeMillis()
            if (!(m == lastToastMsg && nowMs - lastToastTs < 2000)) {
                lastToastMsg = m
                lastToastTs = nowMs
                _toast.value = m
            }
            tts.speak(m)
        }
    }

    /**
     * 识别稳定后自动采集特征补充入库，提升后续识别正确率。
     * - 同人 [STABLE_COOLDOWN_MS] 内只采集一次
     * - 单人特征图累计不超过 [MAX_PERSON_SAMPLE_BYTES]（约 2GB），达上限跳过
     * - 人脸图存前压缩到约 10KB（缩放 + JPEG 降质），节省存储
     * - 存入 face_samples，并重算 person 代表 embedding（全部样本平均）
     */
    private fun handleStableRecognized(name: String, embedding: FloatArray, faceBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            stableMutex.withLock {
                val now = System.currentTimeMillis()
                if (stableLastName == name && now - stableLastTs < STABLE_COOLDOWN_MS) return@withLock
                val person = _persons.value.firstOrNull { it.name == name } ?: return@withLock
                val samples = db.getFaceSamples(person.id)
                val usedBytes = db.getPersonSampleBytes(person.id)
                if (usedBytes >= MAX_PERSON_SAMPLE_BYTES) {
                    stableLastName = name
                    stableLastTs = now
                    return@withLock
                }
                val png = bitmapToPng(faceBitmap) ?: return@withLock
                db.addFaceSample(person.id, embedding, png)
                // 重算代表 embedding：现有全部样本 + 本次
                val all = (samples.map { it.embedding } + embedding)
                val dim = all[0].size
                val avg = FloatArray(dim)
                for (e in all) for (i in 0 until dim) avg[i] += e[i]
                for (i in 0 until dim) avg[i] /= all.size
                db.updatePersonEmbedding(person.id, avg)
                _persons.value = db.getPersons()
                analyser?.setPersons(_persons.value)
                stableLastName = name
                stableLastTs = now
            }
        }
    }

    fun refreshPersons() {
        viewModelScope.launch(Dispatchers.IO) {
            _persons.value = db.getPersons()
            _classes.value = db.getClasses()
            analyser?.setPersons(filteredPersons())
        }
    }

    /**
     * 批量添加人员名单（仅姓名 + 班级，无特征向量）。
     * 用于先导入名单、后期再补录人脸的场景。
     * 同名人员已存在则跳过，绝不覆盖已有数据。
     * 返回 Pair<新增数, 跳过数>。
     */
    fun batchAddPersons(names: List<String>, className: String): Pair<Int, Int> {
        var added = 0
        var skipped = 0
        val existingNames = db.getPersons().map { it.name }.toHashSet()
        viewModelScope.launch(Dispatchers.IO) {
            for (rawName in names) {
                val name = rawName.trim()
                if (name.isEmpty()) continue
                if (name in existingNames) {
                    skipped++
                    continue
                }
                // embedding 传空 FloatArray，表示尚未录入人脸特征
                db.addPerson(name, FloatArray(0), className)
                existingNames.add(name)
                added++
            }
            refreshPersons()
            _toast.value = if (skipped > 0) "已导入 $added 人，跳过 $skipped 人（同名）" else "已导入 $added 人"
        }
        return added to skipped
    }

    fun refreshRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            _records.value = db.getRecords()
        }
    }

    /**
     * 计算出勤率统计：
     * - totalDays: 有签到记录的不同日期数（即"上了几节课"）
     * - 每人出勤率 = 该人签到日期数 / totalDays
     * - 班级累计出勤率 = 班内所有人签到日期数之和 / (班级人数 × totalDays)
     * - 班级当天出勤率 = 班内当天签到人数 / 班级人数
     */
    fun computeStats(): AttendanceSummary {
        val persons = _persons.value
        val records = _records.value.filter { it.action == ACTION_CHECK_IN }

        // 日期格式化：yyyy-MM-dd
        val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val allDays = records.map { dayFmt.format(java.util.Date(it.timestamp)) }.toSet()
        val totalDays = allDays.size

        // 今天（设备日期）已签到人员集合，用于班级当天出勤率
        val todayStr = dayFmt.format(java.util.Date(System.currentTimeMillis()))
        val todayCheckedIds = records
            .filter { dayFmt.format(java.util.Date(it.timestamp)) == todayStr }
            .map { it.personId }
            .toSet()

        // 每人签到日期集合
        val personDaysMap = mutableMapOf<Long, MutableSet<String>>()
        for (r in records) {
            personDaysMap.getOrPut(r.personId) { mutableSetOf() }
                .add(dayFmt.format(java.util.Date(r.timestamp)))
        }

        // 每人统计
        val personStats = persons.map { p ->
            val days = personDaysMap[p.id]?.size ?: 0
            val rate = if (totalDays > 0) days.toFloat() / totalDays else 0f
            PersonStat(p.id, p.name, p.className, days, totalDays, rate)
        }

        // 按班级分组
        val classStats = persons.map { it.className }
            .distinct()
            .sortedWith(compareBy({ it.isEmpty() }, { it }))
            .map { cls ->
                val members = personStats.filter { it.className == cls }
                val avg = if (members.isNotEmpty() && totalDays > 0)
                    members.sumOf { it.checkInDays.toDouble() }.toFloat() /
                        (members.size * totalDays)
                else 0f
                // 当天出勤率：班内今天已签到人数 / 班级人数
                val todayChecked = members.count { it.personId in todayCheckedIds }
                val todayRate = if (members.isNotEmpty()) todayChecked.toFloat() / members.size else 0f
                ClassStat(
                    className = cls,
                    totalPersons = members.size,
                    avgRate = avg,
                    todayRate = todayRate,
                    persons = members.sortedByDescending { it.rate }
                )
            }

        return AttendanceSummary(totalDays, persons.size, classStats)
    }

    fun deletePerson(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deletePerson(id)
            _persons.value = db.getPersons()
            _classes.value = db.getClasses()
            _records.value = db.getRecords()
            analyser?.setPersons(_persons.value)
        }
    }

    /** 重命名班级 */
    fun renameClass(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        // 防止与已有班级重名
        if (db.getClasses().any { it.equals(trimmed, ignoreCase = true) }) {
            _toast.value = "班级「$trimmed」已存在"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.renameClass(oldName, trimmed)
            _persons.value = db.getPersons()
            _classes.value = db.getClasses()
            analyser?.setPersons(_persons.value)
            _toast.value = "已重命名: $oldName → $trimmed"
        }
    }

    /** 修改单个人员的班级 */
    fun updatePersonClass(personId: Long, newClassName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updatePersonClass(personId, newClassName.trim())
            _persons.value = db.getPersons()
            _classes.value = db.getClasses()
            _records.value = db.getRecords()
            analyser?.setPersons(filteredPersons())
        }
    }

    /**
     * 编辑人员姓名/班级（人脸特征数据完全不动）。
     * 姓名变化会同步到其历史签到记录；重名（他人已占用）时拒绝保存。
     */
    fun savePersonInfo(personId: Long, newName: String, newClassName: String) {
        val name = newName.trim()
        val cls = newClassName.trim()
        if (name.isEmpty()) {
            _toast.value = "姓名不能为空"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.getPersons()
            val self = all.firstOrNull { it.id == personId } ?: return@launch
            if (all.any { it.id != personId && it.name == name }) {
                _toast.value = "已存在同名人员，请更换姓名"
                return@launch
            }
            if (self.name != name) db.updatePersonName(personId, name)
            if (self.className != cls) db.updatePersonClass(personId, cls)
            _persons.value = db.getPersons()
            _classes.value = db.getClasses()
            _records.value = db.getRecords()
            analyser?.setPersons(filteredPersons())
            // 详情页打开中时同步刷新标题与班级
            if (_detailPerson.value?.id == personId) {
                _detailPerson.value = _persons.value.firstOrNull { it.id == personId }
            }
            scheduleAutoBackup()
            _toast.value = "已保存: $name"
        }
    }

    // ── 详情页 ──

    fun openDetail(person: Person) {
        _detailPerson.value = person
        viewModelScope.launch(Dispatchers.IO) {
            _detailSamples.value = db.getFaceSamples(person.id)
        }
    }

    fun closeDetail() {
        _detailPerson.value = null
        _detailSamples.value = emptyList()
    }

    /** 删除单张特征样本，并重算所属人员代表 embedding */
    fun deleteSample(sampleId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val pid = _detailPerson.value?.id ?: return@launch
            db.deleteFaceSample(sampleId)
            val samples = db.getFaceSamples(pid)
            if (samples.isNotEmpty()) {
                val dim = samples[0].embedding.size
                val avg = FloatArray(dim)
                for (s in samples) for (i in 0 until dim) avg[i] += s.embedding[i]
                for (i in 0 until dim) avg[i] /= samples.size
                db.updatePersonEmbedding(pid, avg)
            }
            _detailSamples.value = samples
            _persons.value = db.getPersons()
            analyser?.setPersons(_persons.value)
        }
    }

    // ── 数据导出 / 导入 ──
    // 【关键约束】导入为「合并」而非「替换」：同名人员跳过，绝不覆盖已录入特征数据

    /**
     * 导出全部人员、特征样本与签到记录为 JSON 到指定 Uri。
     * 含 person 代表 embedding + 每张 face_sample（embedding+人脸图）+ 该人员签到记录。
     */
    fun exportData(uri: Uri): Boolean {
        return try {
            val json = buildExportJson()
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            _toast.value = "已导出 ${JSONObject(json).optInt("personCount")} 人数据"
            true
        } catch (e: Exception) {
            Log.e("VM", "export failed: ${e.message}")
            _toast.value = "导出失败: ${e.message}"
            false
        }
    }

    /** 构建全量备份 JSON：人员代表特征 + 每张特征样本(图) + 签到记录。手动导出与自动备份共用。 */
    fun buildExportJson(): String {
        val persons = db.getPersons()
        val records = db.getRecords()
        return JSONObject().apply {
            put("version", 1)
            put("exportTime", System.currentTimeMillis())
            put("personCount", persons.size)
            val personsArr = JSONArray()
            for (p in persons) {
                val samples = db.getFaceSamples(p.id)
                val samplesArr = JSONArray()
                for (s in samples) {
                    samplesArr.put(JSONObject().apply {
                        put("embedding", s.embedding.joinToString(","))
                        put("bitmap", Base64.encodeToString(s.bitmapPng, Base64.NO_WRAP))
                        put("createdAt", s.createdAt)
                    })
                }
                val recsArr = JSONArray()
                for (r in records.filter { it.personId == p.id }) {
                    recsArr.put(JSONObject().apply {
                        put("timestamp", r.timestamp)
                        put("action", r.action)
                    })
                }
                personsArr.put(JSONObject().apply {
                    put("name", p.name)
                    put("className", p.className)
                    put("embedding", p.embedding.joinToString(","))
                    put("samples", samplesArr)
                    put("records", recsArr)
                })
            }
            put("persons", personsArr)
        }.toString()
    }

    /** 从 JSON 文件 Uri 合并导入（同名跳过，绝不覆盖已有特征） */
    fun importData(uri: Uri): ImportResult? {
        val text = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return null
        } catch (e: Exception) {
            Log.e("VM", "import read failed: ${e.message}")
            _toast.value = "导入失败: ${e.message}"
            return null
        }
        return importJsonText(text)
    }

    /**
     * 从 JSON 文本合并导入（文件导入与自动备份恢复共用）：
     * - 同名人员已存在 → 跳过（不覆盖其特征/记录）
     * - 不存在 → 插入人员 + 全部特征样本 + 签到记录
     * 绝不删除或覆盖本机已录入数据。返回 null 表示解析失败。
     */
    fun importJsonText(text: String): ImportResult? {
        return try {
            val json = JSONObject(text)
            val personsArr = json.optJSONArray("persons") ?: JSONArray()

            val existingNames = db.getPersons().map { it.name }.toHashSet()
            var importedPersons = 0
            var skippedPersons = 0
            var importedRecords = 0

            for (i in 0 until personsArr.length()) {
                val pObj = personsArr.getJSONObject(i)
                val name = pObj.optString("name", "").trim()
                if (name.isEmpty()) continue
                // 同名跳过，保护已有特征数据
                if (name in existingNames) {
                    skippedPersons++
                    continue
                }
                val className = pObj.optString("className", "")
                val emb = parseEmbeddingStr(pObj.optString("embedding", ""))
                val newId = db.addPerson(name, emb, className)
                existingNames.add(name)
                importedPersons++

                val samplesArr = pObj.optJSONArray("samples")
                if (samplesArr != null) {
                    for (j in 0 until samplesArr.length()) {
                        val sObj = samplesArr.getJSONObject(j)
                        val sEmb = parseEmbeddingStr(sObj.optString("embedding", ""))
                        val bmpStr = sObj.optString("bitmap", "")
                        if (sEmb.isNotEmpty() && bmpStr.isNotEmpty()) {
                            val bmp = Base64.decode(bmpStr, Base64.DEFAULT)
                            val createdAt = sObj.optLong("createdAt", System.currentTimeMillis())
                            db.addFaceSample(newId, sEmb, bmp, createdAt)
                        }
                    }
                }

                val recsArr = pObj.optJSONArray("records")
                if (recsArr != null) {
                    for (k in 0 until recsArr.length()) {
                        val rObj = recsArr.getJSONObject(k)
                        val ts = rObj.optLong("timestamp", System.currentTimeMillis())
                        val action = rObj.optString("action", ACTION_CHECK_IN)
                        db.addRecord(newId, name, ts, action, className)
                        importedRecords++
                    }
                }
            }

            refreshPersons()
            refreshRecords()
            scheduleAutoBackup()
            _toast.value = "已导入 $importedPersons 人，跳过 $skippedPersons 人（同名）"
            ImportResult(importedPersons, skippedPersons, importedRecords)
        } catch (e: Exception) {
            Log.e("VM", "import failed: ${e.message}")
            _toast.value = "导入失败: ${e.message}"
            null
        }
    }

    // ── 卸载不丢：自动备份 / 重装恢复 ──

    /** 数据变化后防抖落盘，合并连续录入/识别；真正写入在切后台或 4 秒后进行 */
    fun scheduleAutoBackup() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(4000L)
            writeAutoBackup(force = false)
        }
    }

    /** App 切后台/卸载前立即备份（卸载流程会先让应用进入 onStop） */
    fun flushBackupNow() {
        backupJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { writeAutoBackup(force = false) }
    }

    /** 设置页「立即备份到本机」：强制写入公共目录 */
    fun manualBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = writeAutoBackup(force = true)
            _toast.value = if (ok)
                "已备份到手机「下载/智能刷脸考勤」，卸载不丢"
            else "备份失败，请改用「导出数据」"
        }
    }

    /** 写入公共备份；内容指纹未变且非强制时跳过，返回是否写入成功 */
    private fun writeAutoBackup(force: Boolean): Boolean {
        return try {
            val sig = db.dataSignature()
            if (!force && sig == lastBackupSig) return true
            val ok = AutoBackup.write(appCtx, buildExportJson())
            if (ok) lastBackupSig = sig
            ok
        } catch (e: Exception) {
            Log.e("VM", "auto backup failed: ${e.message}")
            false
        }
    }

    /**
     * 启动时检测：本机为空（重装/清空）且公共目录存在备份 → 发出待恢复提示。
     * 用户「暂不」后，同一备份时间点不再重复打扰。
     */
    fun maybeOfferRestore() {
        viewModelScope.launch(Dispatchers.IO) {
            if (db.getPersons().isNotEmpty()) return@launch
            val dismissedTs = appCtx
                .getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
                .getLong("restore_dismissed_ts", 0L)
            val ref = AutoBackup.find(appCtx) ?: return@launch
            if (ref.personCount > 0 && ref.exportTime > dismissedTs) {
                _pendingRestore.value = ref
            }
        }
    }

    /** 关闭恢复提示并记住已忽略的备份时间点，避免重复弹窗 */
    fun dismissRestoreOffer() {
        _pendingRestore.value?.let { ref ->
            appCtx.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putLong("restore_dismissed_ts", ref.exportTime).apply()
        }
        _pendingRestore.value = null
    }

    /** 从公共目录的自动备份合并恢复（同名跳过，不覆盖现有数据） */
    fun restoreFromAutoBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val ref = AutoBackup.find(appCtx)
            if (ref == null) {
                _toast.value = "未找到本机自动备份"
                return@launch
            }
            val text = AutoBackup.readText(appCtx, ref)
            if (text == null) {
                _toast.value = "备份文件无法读取"
                return@launch
            }
            val result = importJsonText(text)
            if (result != null) {
                _pendingRestore.value = null
                // 恢复后内容与备份一致，标记指纹避免立即重复回写
                lastBackupSig = db.dataSignature()
            }
        }
    }

    private fun parseEmbeddingStr(s: String): FloatArray =
        s.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()

    fun clearToast() { _toast.value = null }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Bitmap → 压缩字节（存库）：先缩放到 160px 短边，再 JPEG 降质至 ≤ 10KB。失败返回 null。 */
    private fun bitmapToPng(bmp: Bitmap): ByteArray? {
        return try {
            // 1. 缩放：短边 160px，控制原始数据量
            val shortSide = minOf(bmp.width, bmp.height)
            val scale = if (shortSide > 160) 160f / shortSide else 1f
            val scaled = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            // 2. JPEG 降质循环，直到 ≤ 10KB 或质量到 20
            var quality = 80
            val target = 10 * 1024
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > target && quality >= 20)
            bytes
        } catch (e: Exception) {
            Log.e("VM", "bitmapToPng: ${e.message}")
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceNet?.close()
        tts.release()
    }

    companion object {
        const val ENROLL_SAMPLE_COUNT = 5
        const val COOLDOWN_MS = 4000L
        /** 单人特征图累计上限：约 2GB（存储充足即可持续采集） */
        const val MAX_PERSON_SAMPLE_BYTES = 2L * 1024 * 1024 * 1024
        const val STABLE_COOLDOWN_MS = 10000L
    }
}

data class RecognizedInfo(
    val name: String,
    val distance: Float,
    val bbox: Rect
)

/** 单人出勤率统计 */
data class PersonStat(
    val personId: Long,
    val name: String,
    val className: String,
    val checkInDays: Int,
    val totalDays: Int,
    val rate: Float
)

/** 班级出勤率统计：avgRate 为历史累计，todayRate 为当天（今日已签/班级人数） */
data class ClassStat(
    val className: String,
    val totalPersons: Int,
    val avgRate: Float,
    val todayRate: Float,
    val persons: List<PersonStat>
)

/** 出勤率总览 */
data class AttendanceSummary(
    val totalDays: Int,
    val totalPersons: Int,
    val classStats: List<ClassStat>
)

/** 数据导入结果：新增/跳过人员数，新增记录数 */
data class ImportResult(
    val importedPersons: Int,
    val skippedPersons: Int,
    val importedRecords: Int
)