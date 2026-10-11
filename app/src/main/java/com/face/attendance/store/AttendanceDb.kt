package com.face.attendance.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Person(
    val id: Long,
    val name: String,
    val embedding: FloatArray,
    val className: String = ""
) {
    /** 是否已录入人脸特征（embedding 非空且长度 > 0） */
    val hasFaceEmbedding: Boolean get() = embedding.isNotEmpty()

    override fun equals(other: Any?): Boolean =
        this === other || (other is Person && id == other.id)
    override fun hashCode(): Int = id.hashCode()
}

data class Record(
    val id: Long,
    val personId: Long,
    val name: String,
    val timestamp: Long,
    val action: String,
    val className: String = ""
)

/**
 * 单张人脸特征样本：embedding 向量 + 采集时的人脸位图(PNG)。
 * 用于详情页查看/删除错误特征，并在识别稳定时自动追加以提升识别率。
 */
data class FaceSample(
    val id: Long,
    val personId: Long,
    val embedding: FloatArray,
    val bitmapPng: ByteArray,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is FaceSample && id == other.id)
    override fun hashCode(): Int = id.hashCode()
}

class AttendanceDb(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val ctx = context.applicationContext

    companion object {
        private const val DB_NAME = "face_attendance.db"
        private const val DB_VERSION = 4

        private const val T_PERSONS = "persons"
        private const val T_RECORDS = "records"
        private const val T_SAMPLES = "face_samples"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $T_PERSONS(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              embedding TEXT NOT NULL,
              class_name TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL(
            """CREATE TABLE $T_RECORDS(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              person_id INTEGER NOT NULL,
              name TEXT NOT NULL,
              ts INTEGER NOT NULL,
              action TEXT NOT NULL,
              class_name TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX idx_records_ts ON $T_RECORDS(ts DESC)")
        db.execSQL(
            """CREATE TABLE $T_SAMPLES(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              person_id INTEGER NOT NULL,
              embedding TEXT NOT NULL,
              bitmap BLOB NOT NULL,
              created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_samples_person ON $T_SAMPLES(person_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 → v2：新增 face_samples 表，不影响已有 persons/records 数据
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $T_SAMPLES(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  person_id INTEGER NOT NULL,
                  embedding TEXT NOT NULL,
                  bitmap BLOB NOT NULL,
                  created_at INTEGER NOT NULL)"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_person ON $T_SAMPLES(person_id)")
        }
        if (oldVersion < 3) {
            // v2 → v3：persons 表新增 class_name 列，已有数据默认空字符串
            db.execSQL("ALTER TABLE $T_PERSONS ADD COLUMN class_name TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            // v3 → v4：records 表新增 class_name 列，历史记录按人员当前班级回填，
            // 使考勤记录可直接按班级筛选，不再依赖人员实时映射
            db.execSQL("ALTER TABLE $T_RECORDS ADD COLUMN class_name TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "UPDATE $T_RECORDS SET class_name=(" +
                    "SELECT p.class_name FROM $T_PERSONS p WHERE p.id=$T_RECORDS.person_id)"
            )
        }
    }

    fun addPerson(name: String, embedding: FloatArray, className: String = ""): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("embedding", embedding.joinToString(",") { it.toString() })
            put("class_name", className)
        }
        return writableDatabase.insert(T_PERSONS, null, cv)
    }

    fun deletePerson(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(T_SAMPLES, "person_id=?", arrayOf(id.toString()))
            writableDatabase.delete(T_RECORDS, "person_id=?", arrayOf(id.toString()))
            writableDatabase.delete(T_PERSONS, "id=?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getPersons(): MutableList<Person> {
        val out = mutableListOf<Person>()
        readableDatabase.query(T_PERSONS, null, null, null, null, null, "name ASC").use {
            val idxId = it.getColumnIndexOrThrow("id")
            val idxName = it.getColumnIndexOrThrow("name")
            val idxEmb = it.getColumnIndexOrThrow("embedding")
            val idxClass = it.getColumnIndexOrThrow("class_name")
            while (it.moveToNext()) {
                val emb = it.getString(idxEmb).split(",")
                    .mapNotNull { v -> v.toFloatOrNull() }.toFloatArray()
                out.add(
                    Person(
                        it.getLong(idxId),
                        it.getString(idxName),
                        emb,
                        it.getString(idxClass)
                    )
                )
            }
        }
        return out
    }

    /** 返回所有已使用的班级名称（去重、排除空字符串，按名称排序） */
    fun getClasses(): MutableList<String> {
        val out = mutableListOf<String>()
        readableDatabase.query(
            true, T_PERSONS, arrayOf("class_name"),
            "class_name != ''", null, null, null, "class_name ASC", null
        ).use {
            while (it.moveToNext()) out.add(it.getString(0))
        }
        return out
    }

    /** 重命名班级：将该班级下所有人员及其历史记录的 class_name 更新为新名称 */
    fun renameClass(oldName: String, newName: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("class_name", newName) }
            db.update(T_PERSONS, cv, "class_name=?", arrayOf(oldName))
            db.update(T_RECORDS, cv, "class_name=?", arrayOf(oldName))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 修改单个人员的班级（传空字符串表示移出班级/设为未分班），历史记录班级同步更新 */
    fun updatePersonClass(personId: Long, newClassName: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("class_name", newClassName) }
            db.update(T_PERSONS, cv, "id=?", arrayOf(personId.toString()))
            db.update(T_RECORDS, cv, "person_id=?", arrayOf(personId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 修改人员姓名，并同步更新其历史签到记录中冗余的姓名（不动任何人脸特征数据） */
    fun updatePersonName(personId: Long, newName: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("name", newName) }
            db.update(T_PERSONS, cv, "id=?", arrayOf(personId.toString()))
            db.update(T_RECORDS, cv, "person_id=?", arrayOf(personId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 更新某人代表 embedding（识别稳定自动采集后重算平均） */
    fun updatePersonEmbedding(personId: Long, embedding: FloatArray) {
        val cv = ContentValues().apply {
            put("embedding", embedding.joinToString(",") { it.toString() })
        }
        writableDatabase.update(T_PERSONS, cv, "id=?", arrayOf(personId.toString()))
    }

    // ── 人脸特征样本 ──

    /** 追加一张特征样本（embedding + 人脸PNG）。返回新样本 id。 */
    fun addFaceSample(personId: Long, embedding: FloatArray, bitmapPng: ByteArray): Long =
        addFaceSample(personId, embedding, bitmapPng, System.currentTimeMillis())

    /** 追加特征样本并指定采集时间（数据导入时保留原始 createdAt） */
    fun addFaceSample(personId: Long, embedding: FloatArray, bitmapPng: ByteArray, createdAt: Long): Long {
        val cv = ContentValues().apply {
            put("person_id", personId)
            put("embedding", embedding.joinToString(",") { it.toString() })
            put("bitmap", bitmapPng)
            put("created_at", createdAt)
        }
        return writableDatabase.insert(T_SAMPLES, null, cv)
    }

    fun getFaceSamples(personId: Long): MutableList<FaceSample> {
        val out = mutableListOf<FaceSample>()
        readableDatabase.query(
            T_SAMPLES, null, "person_id=?", arrayOf(personId.toString()),
            null, null, "created_at ASC"
        ).use {
            while (it.moveToNext()) {
                val emb = it.getString(it.getColumnIndexOrThrow("embedding")).split(",")
                    .mapNotNull { v -> v.toFloatOrNull() }.toFloatArray()
                out.add(
                    FaceSample(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        personId = personId,
                        embedding = emb,
                        bitmapPng = it.getBlob(it.getColumnIndexOrThrow("bitmap")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return out
    }

    fun deleteFaceSample(sampleId: Long) {
        writableDatabase.delete(T_SAMPLES, "id=?", arrayOf(sampleId.toString()))
    }

    /** 某人全部特征图占用字节数（用于 2GB 上限判断） */
    fun getPersonSampleBytes(personId: Long): Long {
        readableDatabase.query(
            T_SAMPLES,
            arrayOf("SUM(LENGTH(bitmap))"),
            "person_id=?",
            arrayOf(personId.toString()),
            null, null, null
        ).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun addRecord(
        personId: Long,
        name: String,
        ts: Long,
        action: String,
        className: String = ""
    ): Long {
        val cv = ContentValues().apply {
            put("person_id", personId)
            put("name", name)
            put("ts", ts)
            put("action", action)
            put("class_name", className)
        }
        return writableDatabase.insert(T_RECORDS, null, cv)
    }

    fun getRecords(): MutableList<Record> {
        val out = mutableListOf<Record>()
        readableDatabase.query(T_RECORDS, null, null, null, null, null, "ts DESC").use {
            while (it.moveToNext()) {
                out.add(
                    Record(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        personId = it.getLong(it.getColumnIndexOrThrow("person_id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("ts")),
                        action = it.getString(it.getColumnIndexOrThrow("action")),
                        className = it.getString(it.getColumnIndexOrThrow("class_name"))
                    )
                )
            }
        }
        return out
    }

    /** 已打卡返回 true，用于防重复 */
    fun hasCheckedToday(personId: Long, startOfDay: Long): Boolean {
        readableDatabase.query(
            T_RECORDS,
            arrayOf("id"),
            "person_id=? AND ts>=? AND action=?",
            arrayOf(personId.toString(), startOfDay.toString(), ACTION_CHECK_IN),
            null, null, null, "1"
        ).use { return it.moveToFirst() }
    }

    /**
     * 数据指纹：人员/记录/特征样本的数量、最新时间与文本长度的轻量摘要。
     * 自动备份据此判断内容是否变化，未变化则不重复写文件。
     */
    fun dataSignature(): String {
        readableDatabase.rawQuery(
            """SELECT
              | (SELECT COUNT(*) FROM $T_PERSONS),
              | (SELECT COUNT(*) FROM $T_RECORDS),
              | (SELECT COALESCE(MAX(ts),0) FROM $T_RECORDS),
              | (SELECT COUNT(*) FROM $T_SAMPLES),
              | (SELECT COALESCE(MAX(created_at),0) FROM $T_SAMPLES),
              | (SELECT COALESCE(SUM(LENGTH(name)+LENGTH(class_name)+LENGTH(embedding)),0) FROM $T_PERSONS)""".trimMargin(),
            null
        ).use {
            return if (it.moveToFirst())
                (0..5).joinToString("|") { i -> it.getLong(i).toString() }
            else "0"
        }
    }
}

const val ACTION_CHECK_IN = "签到"
const val ACTION_CHECK_OUT = "签退"