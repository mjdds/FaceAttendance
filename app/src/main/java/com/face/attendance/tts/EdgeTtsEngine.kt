package com.face.attendance.tts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Edge TTS 引擎 — 通过 WebSocket 调用微软 Edge 在线语音合成服务。
 * 协议参照 edge-tts，返回 MP3 音频字节流。
 *
 * 关键修复：二进制帧前 2 字节为 big-endian 头长度前缀，必须剥离，
 * 否则拼接出的 MP3 数据被污染，MediaPlayer 解码失败 → 无声。
 */
class EdgeTtsEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()
) {

    /** 当前发音人（Edge 在线音色 ID） */
    var voice: String = "zh-CN-XiaoxiaoNeural"

    /** 语速百分比，如 "+0%" */
    var rate: String = "+0%"

    /** 音调，如 "+0Hz" */
    var pitch: String = "+0Hz"

    /** 合成语音，返回 MP3 字节流；失败抛异常 */
    suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val url = "$WS_BASE&ConnectionId=$requestId"

        val request = Request.Builder()
            .url(url)
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .build()

        val ssml = buildSsml(text, voice)
        val audio = ArrayList<ByteArray>()

        suspendCancellableCoroutine { cont ->
            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(buildConfigMessage())
                    webSocket.send(buildSsmlMessage(requestId, ssml))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, null)
                        val merged = audio.fold(ByteArray(0)) { acc, b -> acc + b }
                        if (cont.isActive) cont.resume(merged)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // ★ 关键：剥离 2 字节头前缀，只取纯音频载荷
                    val raw = bytes.toByteArray()
                    if (raw.size > 2) {
                        audio.add(raw.copyOfRange(2, raw.size))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val diag = diagnoseFailure(t)
                    Log.e(TAG, "ws failure: ${t.javaClass.simpleName}: ${t.message} | $diag")
                    if (cont.isActive) cont.resumeWithException(RuntimeException(diag))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) {
                        if (audio.isEmpty()) {
                            cont.resumeWithException(RuntimeException("未收到音频数据"))
                        } else {
                            val merged = audio.fold(ByteArray(0)) { acc, b -> acc + b }
                            cont.resume(merged)
                        }
                    }
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    }

    private fun buildSsml(text: String, voice: String): String =
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
            "<voice name='$voice'>" +
            "<prosody pitch='$pitch' rate='$rate' volume='+0%'>" +
            escapeXml(text) +
            "</prosody></voice></speak>"

    private fun buildConfigMessage(): String =
        "X-Timestamp:${nowIso()}\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
            "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"}," +
            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"

    private fun buildSsmlMessage(requestId: String, ssml: String): String =
        "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "Path:ssml\r\n\r\n$ssml"

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;")

    private fun nowIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            .format(java.util.Date())

    /** 把底层异常翻译成用户可理解的中文诊断 */
    private fun diagnoseFailure(t: Throwable): String = when (t) {
        is java.net.UnknownHostException ->
            "DNS 解析失败：speech.platform.bing.com 无法访问（可能被网络屏蔽，需代理）"
        is java.net.SocketTimeoutException ->
            "连接超时：微软语音服务无响应（网络受限或需代理）"
        is java.net.ConnectException ->
            "连接被拒绝：微软语音服务不可达（可能需代理）"
        is javax.net.ssl.SSLException ->
            "SSL 握手失败：网络可能被劫持或需代理"
        else -> {
            val msg = t.message ?: t.javaClass.simpleName
            if (msg.contains("403", true) || msg.contains("Forbidden", true))
                "鉴权失败：Token 已失效（需更新）"
            else
                "Edge 连接失败：$msg"
        }
    }

    companion object {
        private const val TAG = "EdgeTts"
        private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        const val WS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TOKEN"
    }
}
