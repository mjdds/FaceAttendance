package com.face.attendance.tts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 百度翻译 TTS 引擎 —— 利用百度翻译网页版公开 TTS 接口。
 *
 * 免费、免 API Key、国内直连可用。
 * 接口：https://fanyi.baidu.com/gettts?lan=zh&text=TEXT&spd=N&source=web
 * 返回 MP3 音频字节流。
 *
 * 适合短文本播报，长文本可能被截断或限流。
 */
class BaiduTranslateTtsEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** 语速 1（慢）~ 5（快），默认 3 */
    var speed: Int = 3

    /** 合成语音，返回 MP3 字节流；失败抛异常 */
    suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        // 百度翻译 TTS 对过长文本会截断，限制在 200 字以内
        val truncated = if (text.length > 200) text.substring(0, 200) else text
        val encoded = URLEncoder.encode(truncated, "UTF-8")
        val url = "https://fanyi.baidu.com/gettts?lan=zh&text=$encoded&spd=$speed&source=web"

        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://fanyi.baidu.com/")
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                response.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    Log.d(TAG, "百度翻译TTS合成成功: ${bytes.size} 字节")
                    bytes
                } else {
                    throw RuntimeException("百度翻译TTS返回空数据")
                }
            } else {
                val code = response.code
                response.close()
                throw RuntimeException("百度翻译TTS请求失败: HTTP $code")
            }
        } catch (e: java.net.UnknownHostException) {
            throw RuntimeException("DNS解析失败：fanyi.baidu.com 不可达")
        } catch (e: java.net.SocketTimeoutException) {
            throw RuntimeException("百度翻译TTS连接超时")
        } catch (e: Exception) {
            if (e.message?.contains("百度翻译") == true || e.message?.contains("DNS") == true || e.message?.contains("超时") == true)
                throw e
            throw RuntimeException("百度翻译TTS失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BaiduTranslateTts"
    }
}
