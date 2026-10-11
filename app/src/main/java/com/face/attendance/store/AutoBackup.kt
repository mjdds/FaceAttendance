package com.face.attendance.store

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.File

/**
 * 卸载不丢的「自动备份」。
 *
 * 应用私有数据库（/data/data/...）在卸载时会被系统一并删除；这里把全量 JSON
 * 备份写到**公共下载目录** `下载/智能刷脸考勤/auto_backup.json`，该位置：
 *  - 卸载应用不会被删除；
 *  - Android 10+ 走 MediaStore，无需任何存储权限，同包名重装后仍可读写本应用创建的条目；
 *  - Android 7~9 直写公共 Documents 目录（需 WRITE_EXTERNAL_STORAGE，清单已按 maxSdkVersion=28 声明）。
 */
object AutoBackup {

    private const val DIR_NAME = "智能刷脸考勤"
    const val FILE_NAME = "auto_backup.json"
    private const val MIME = "application/json"

    /** 备份文件引用：新系统用 [uri]，Android 9 及以下用 [file] */
    data class Ref(
        val uri: Uri?,
        val file: File?,
        val personCount: Int,
        val exportTime: Long
    )

    /** 覆盖写入自动备份文件，成功返回 true */
    fun write(context: Context, json: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeQ(context, json)
        else writeLegacy(json)

    /** 查找已存在的自动备份（含人数/备份时间元信息），不存在返回 null */
    fun find(context: Context): Ref? {
        val (uri, file) = locate(context) ?: return null
        val text = readText(context, uri, file) ?: return null
        val (count, time) = parseMeta(text)
        return Ref(uri, file, count, time)
    }

    /** 读取备份全文，用于恢复 */
    fun readText(context: Context, ref: Ref): String? =
        readText(context, ref.uri, ref.file)

    private fun readText(context: Context, uri: Uri?, file: File?): String? = try {
        when {
            uri != null -> context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
            file != null -> if (file.exists()) file.readText(Charsets.UTF_8) else null
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun parseMeta(text: String): Pair<Int, Long> = try {
        val o = JSONObject(text)
        o.optInt("personCount", 0) to o.optLong("exportTime", 0L)
    } catch (e: Exception) {
        0 to 0L
    }

    private fun locate(context: Context): Pair<Uri?, File?>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return locateQ(context)
        val f = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$DIR_NAME/$FILE_NAME"
        )
        return if (f.exists()) null to f else null
    }

    // ── API 29+：MediaStore.Downloads，免权限、卸载不删 ──

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun locateQ(context: Context): Pair<Uri?, File?>? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        // RELATIVE_PATH 在库中以带尾斜杠形式存储
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME/", FILE_NAME)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(collection, id) to null
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("NewApi")
    private fun writeQ(context: Context, json: String): Boolean = try {
        val resolver = context.contentResolver
        val existing = locateQ(context)?.first
        if (existing != null) {
            // 已存在（本应用创建）→ 截断覆盖
            resolver.openOutputStream(existing, "wt")?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                resolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: return false
            } finally {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            true
        }
    } catch (e: Exception) {
        false
    }

    // ── API 24~28：公共 Documents 目录直写（需 WRITE_EXTERNAL_STORAGE） ──

    private fun writeLegacy(json: String): Boolean = try {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            DIR_NAME
        )
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME).writeText(json, Charsets.UTF_8)
        true
    } catch (e: Exception) {
        false
    }
}
