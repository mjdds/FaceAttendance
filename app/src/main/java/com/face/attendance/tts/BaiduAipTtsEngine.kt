package com.face.attendance.tts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 百度智能云语音合成引擎（AIP TTS）。
 *
 * 需要用户在 cloud.baidu.com 注册并创建应用，获取 API Key 和 Secret Key。
 * 有免费额度（标准语音每日 5 万次调用）。
 *
 * 流程：
 * 1. 用 API Key + Secret Key 换取 access_token（有效期 30 天）
 * 2. POST https://tsn.baidu.com/text2audio 获取 MP3 音频
 *
 * 发音人 per：
 *   0=普通女声, 1=普通男声, 3=度逍遥(情感男), 4=度丫丫(情感女),
 *   5003=度小雯, 5118=度小鹿, 101001=度米香(粤语女声) 等
 */
class BaiduAipTtsEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** API Key（用户在设置中配置） */
    @Volatile private var apiKey: String = ""

    /** Secret Key（用户在设置中配置） */
    @Volatile private var secretKey: String = ""

    /** 发音人编号 */
    var per: Int = 0

    /** 语速 0~15，默认 5 */
    var spd: Int = 5

    /** 音调 0~15，默认 5 */
    var pit: Int = 5

    /** 音量 0~15，默认 5 */
    var vol: Int = 5

    /** Token 缓存 */
    @Volatile private var tokenCache: String? = null
    @Volatile private var tokenExpireAt: Long = 0

    /** 设置凭证并清除 token 缓存 */
    fun setCredentials(apiKey: String, secretKey: String) {
        this.apiKey = apiKey
        this.secretKey = secretKey
        this.tokenCache = null
        this.tokenExpireAt = 0
    }

    fun hasCredentials(): Boolean = apiKey.isNotBlank() && secretKey.isNotBlank()

    /** 获取 access_token（带缓存） */
    private suspend fun getToken(): String = withContext(Dispatchers.IO) {
        val cached = tokenCache
        if (cached != null && System.currentTimeMillis() < tokenExpireAt) {
            return@withContext cached
        }
        if (!hasCredentials()) {
            throw RuntimeException("请先在设置中配置百度智能云 API Key 和 Secret Key")
        }

        val url = "https://aip.baidubce.com/oauth/2.0/token" +
            "?grant_type=client_credentials&client_id=$apiKey&client_secret=$secretKey"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .header("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (body == null) throw RuntimeException("获取token返回空")

            val json = JSONObject(body)
            if (json.has("error")) {
                throw RuntimeException("百度鉴权失败: ${json.optString("error_description", json.optString("error"))}")
            }
            val token = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 2592000L)
            tokenCache = token
            tokenExpireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000
            Log.d(TAG, "百度AIP token获取成功，有效期 ${expiresIn} 秒")
            token
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("获取百度token失败: ${e.message}")
        }
    }

    /** 合成语音，返回 MP3 字节流；失败抛异常 */
    suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val tok = getToken()
        val truncated = if (text.length > 500) text.substring(0, 500) else text

        val formBody = FormBody.Builder()
            .add("tex", truncated)
            .add("tok", tok)
            .add("cuid", "attendance_face_app")
            .add("lan", "zh")
            .add("ctp", "1")
            .add("spd", spd.toString())
            .add("pit", pit.toString())
            .add("vol", vol.toString())
            .add("per", per.toString())
            .add("aue", "3") // 3=mp3
            .build()

        val request = Request.Builder()
            .url("https://tsn.baidu.com/text2audio")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val contentType = response.header("Content-Type") ?: ""
            if (contentType.contains("audio", ignoreCase = true)) {
                val bytes = response.body?.bytes()
                response.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    Log.d(TAG, "百度AIP合成成功: ${bytes.size} 字节, per=$per")
                    bytes
                } else {
                    throw RuntimeException("百度AIP返回空音频")
                }
            } else {
                // 非 audio 响应说明出错，返回的是 JSON
                val errBody = response.body?.string() ?: ""
                response.close()
                try {
                    val json = JSONObject(errBody)
                    val errMsg = json.optString("err_msg", errBody)
                    val errCode = json.optInt("err_no", -1)
                    // token 过期，清除缓存重试一次
                    if (errCode == 111 || errMsg.contains("token", ignoreCase = true)) {
                        tokenCache = null
                        throw RuntimeException("token已失效，请重新检测")
                    }
                    throw RuntimeException("百度AIP错误[$errCode]: $errMsg")
                } catch (_: Exception) {
                    throw RuntimeException("百度AIP返回非音频: $errBody")
                }
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("百度AIP合成失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BaiduAipTts"
    }
}
