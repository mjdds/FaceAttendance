package com.face.attendance.tts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 有道词典 TTS 引擎 —— 利有道词典公开发音接口。
 *
 * 免费、免 API Key、国内直连可用。
 * 接口：https://dict.youdao.com/dictvoice?audio=TEXT&type=N
 * type=0 美式英语，type=1 英式英语，type=2 男声（英），type=3 女声（英）
 *
 * 中文支持有限，适合中英混合短文本播报。
 */
class YoudaoTtsEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** 发音类型：0=美音, 1=英音, 2=男声, 3=女声 */
    var voiceType: Int = 0

    /** 合成语音，返回 MP3 字节流；失败抛异常 */
    suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val truncated = if (text.length > 200) text.substring(0, 200) else text
        val encoded = URLEncoder.encode(truncated, "UTF-8")
        val url = "https://dict.youdao.com/dictvoice?audio=$encoded&type=$voiceType"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://dict.youdao.com/")
            .header("Accept", "*/*")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                response.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    Log.d(TAG, "有道TTS合成成功: ${bytes.size} 字节")
                    bytes
                } else {
                    throw RuntimeException("有道TTS返回空数据")
                }
            } else {
                val code = response.code
                response.close()
                throw RuntimeException("有道TTS请求失败: HTTP $code")
            }
        } catch (e: java.net.UnknownHostException) {
            throw RuntimeException("DNS解析失败：dict.youdao.com 不可达")
        } catch (e: java.net.SocketTimeoutException) {
            throw RuntimeException("有道TTS连接超时")
        } catch (e: Exception) {
            if (e.message?.contains("有道") == true || e.message?.contains("DNS") == true || e.message?.contains("超时") == true)
                throw e
            throw RuntimeException("有道TTS失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "YoudaoTts"
    }
}
