package com.face.attendance.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * 语音播报服务 — 支持多款免费语音模型切换。
 *
 * 引擎：
 * - Edge TTS（微软在线，高音质，国内可能需代理）
 * - 百度翻译 TTS（免费免Key，国内直连）
 * - 有道词典 TTS（免费免Key，国内直连）
 * - 百度智能云 TTS（需API Key，有免费额度）
 * - Android 系统 TextToSpeech（离线降级）
 *
 * 选用的语音模型持久化在 SharedPreferences，默认百度翻译标准女声。
 */
class TtsService(private val context: Context) {

    private val edgeEngine = EdgeTtsEngine()
    private val baiduTransEngine = BaiduTranslateTtsEngine()
    private val youdaoEngine = YoudaoTtsEngine()
    private val baiduAipEngine = BaiduAipTtsEngine()
    private val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)

    private var systemTts: TextToSpeech? = null
    @Volatile private var systemTtsReady = false

    @Volatile private var mediaPlayer: MediaPlayer? = null

    /** 当前选用的语音模型 */
    private val _currentVoice = MutableStateFlow(loadVoice())
    val currentVoice: StateFlow<VoiceOption> = _currentVoice.asStateFlow()

    /** 当前引擎是否可用（最近一次合成是否成功） */
    private val _engineAvailable = MutableStateFlow(false)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    /** Edge TTS 是否可用 */
    private val _edgeAvailable = MutableStateFlow(false)
    val edgeAvailable: StateFlow<Boolean> = _edgeAvailable.asStateFlow()

    /** 系统 TTS 是否就绪 */
    private val _systemReady = MutableStateFlow(false)
    val systemReady: StateFlow<Boolean> = _systemReady.asStateFlow()

    /** 最近一次播报结果描述 */
    private val _lastStatus = MutableStateFlow("未测试")
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    init {
        // 加载百度AIP凭证
        val aipKey = prefs.getString("baidu_aip_api_key", "") ?: ""
        val aipSecret = prefs.getString("baidu_aip_secret_key", "") ?: ""
        if (aipKey.isNotEmpty() && aipSecret.isNotEmpty()) {
            baiduAipEngine.setCredentials(aipKey, aipSecret)
        }
        applyVoice(_currentVoice.value)
        // 始终预初始化系统 TTS，使设置页状态准确反映真实就绪情况
        initSystemTts()
    }

    /** 初始化系统 TTS（降级用），未就绪时可重复调用以重试 */
    fun initSystemTts() {
        // 已就绪则无需重复
        if (systemTtsReady && systemTts != null) return
        // 上次创建过但未就绪，先释放再重试
        if (systemTts != null && !systemTtsReady) {
            try {
                systemTts?.shutdown()
            } catch (_: Exception) {
            }
            systemTts = null
        }
        if (systemTts != null) return
        _systemReady.value = false
        _lastStatus.value = "正在初始化系统语音引擎…"
        systemTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langRes = systemTts?.setLanguage(Locale.CHINA)
                    ?: TextToSpeech.LANG_NOT_SUPPORTED
                systemTtsReady = langRes != TextToSpeech.LANG_MISSING_DATA &&
                    langRes != TextToSpeech.LANG_NOT_SUPPORTED
                if (!systemTtsReady) {
                    systemTts?.setLanguage(Locale.getDefault())
                    systemTtsReady = true
                }
                systemTts?.setSpeechRate(1.0f)
                systemTts?.setPitch(1.0f)
                _systemReady.value = systemTtsReady
                _lastStatus.value = if (systemTtsReady) "系统语音引擎就绪" else "系统语音不支持中文（语言数据缺失）"
                Log.d(TAG, "系统TTS就绪=$systemTtsReady langRes=$langRes")
            } else {
                systemTtsReady = false
                _systemReady.value = false
                _lastStatus.value = "系统语音引擎初始化失败（设备可能未安装 TTS 引擎）"
                Log.w(TAG, "系统TTS初始化失败 status=$status")
            }
        }
    }

    /** 切换语音模型并持久化 */
    fun setVoice(voice: VoiceOption) {
        prefs.edit().putString("voice_id", voice.id).apply()
        applyVoice(voice)
        _currentVoice.value = voice
    }

    private fun applyVoice(voice: VoiceOption) {
        when (voice.engine) {
            VoiceOption.ENGINE_EDGE -> {
                edgeEngine.voice = voice.edgeVoiceId ?: "zh-CN-XiaoxiaoNeural"
            }
            VoiceOption.ENGINE_BAIDU_TRANSLATE -> {
                baiduTransEngine.speed = voice.engineParam.coerceIn(1, 5)
            }
            VoiceOption.ENGINE_YOUDAO -> {
                youdaoEngine.voiceType = voice.engineParam.coerceIn(0, 3)
            }
            VoiceOption.ENGINE_BAIDU_AIP -> {
                baiduAipEngine.per = voice.engineParam
            }
            VoiceOption.ENGINE_SYSTEM -> {
                initSystemTts()
            }
        }
    }

    private fun loadVoice(): VoiceOption {
        val id = prefs.getString("voice_id", "baidu_trans_normal") ?: "baidu_trans_normal"
        return availableVoices().firstOrNull { it.id == id }
            ?: availableVoices().first()
    }

    /** 保存百度智能云 API 密钥 */
    fun setBaiduAipCredentials(apiKey: String, secretKey: String) {
        prefs.edit()
            .putString("baidu_aip_api_key", apiKey)
            .putString("baidu_aip_secret_key", secretKey)
            .apply()
        baiduAipEngine.setCredentials(apiKey, secretKey)
    }

    /** 读取已保存的百度智能云 API 密钥 */
    fun getBaiduAipCredentials(): Pair<String, String> {
        val key = prefs.getString("baidu_aip_api_key", "") ?: ""
        val secret = prefs.getString("baidu_aip_secret_key", "") ?: ""
        return Pair(key, secret)
    }

    /** 播报文本；返回是否成功 */
    suspend fun speak(text: String): Boolean = withContext(Dispatchers.IO) {
        val voice = _currentVoice.value
        when (voice.engine) {
            VoiceOption.ENGINE_SYSTEM -> {
                val ok = systemFallback(text)
                _lastStatus.value = if (ok) "系统语音播报成功" else "系统语音播报失败"
                ok
            }
            VoiceOption.ENGINE_EDGE -> {
                if (!_edgeAvailable.value && systemTtsReady) {
                    val ok = systemFallback(text)
                    _lastStatus.value = if (ok) "系统语音播报成功（Edge 不可用已自动降级）" else "系统语音播报失败"
                    return@withContext ok
                }
                synthAndPlay(
                    text = text,
                    voice = voice,
                    synth = { edgeEngine.synthesize(text) },
                    onSuccess = { _edgeAvailable.value = true }
                )
            }
            VoiceOption.ENGINE_BAIDU_TRANSLATE -> {
                synthAndPlay(
                    text = text,
                    voice = voice,
                    synth = { baiduTransEngine.synthesize(text) },
                    onSuccess = { _engineAvailable.value = true }
                )
            }
            VoiceOption.ENGINE_YOUDAO -> {
                synthAndPlay(
                    text = text,
                    voice = voice,
                    synth = { youdaoEngine.synthesize(text) },
                    onSuccess = { _engineAvailable.value = true }
                )
            }
            VoiceOption.ENGINE_BAIDU_AIP -> {
                synthAndPlay(
                    text = text,
                    voice = voice,
                    synth = { baiduAipEngine.synthesize(text) },
                    onSuccess = { _engineAvailable.value = true }
                )
            }
            else -> {
                val ok = systemFallback(text)
                _lastStatus.value = if (ok) "系统语音播报成功" else "语音播报失败"
                ok
            }
        }
    }

    /** 通用：合成 → 播放 → 失败降级系统 */
    private suspend fun synthAndPlay(
        text: String,
        voice: VoiceOption,
        synth: suspend () -> ByteArray,
        onSuccess: () -> Unit
    ): Boolean {
        var synthOk = false
        var playOk = false
        try {
            val mp3 = synth()
            if (mp3.isNotEmpty()) {
                synthOk = true
                playOk = playMp3(mp3)
            }
        } catch (e: Exception) {
            _lastStatus.value = e.message ?: "${voice.provider}合成失败"
            Log.w(TAG, "${voice.provider} 合成失败，降级系统 TTS: ${e.message}")
        }
        if (playOk) {
            onSuccess()
            _lastStatus.value = "${voice.displayName} 播报成功"
            Log.d(TAG, "${voice.provider} 播报成功")
            return true
        }
        if (synthOk && !playOk) {
            onSuccess()
            _lastStatus.value = "${voice.provider} 合成成功但播放失败，已降级系统语音"
            Log.w(TAG, "${voice.provider} 合成成功但 MediaPlayer 播放失败")
        }
        // 降级系统 TTS
        val ok = systemFallback(text)
        if (!ok && !synthOk) {
            _lastStatus.value = "${voice.provider} 不可用且系统语音未就绪"
        }
        return ok
    }

    /** 测试播报（设置页用） */
    suspend fun testSpeak(): Boolean {
        return speak("语音播报测试，当前使用${_currentVoice.value.displayName}")
    }

    /** 仅测试 Edge TTS 连通性与音频数据，不播放；返回字节数 */
    suspend fun testEdgeConnection(): Int = withContext(Dispatchers.IO) {
        try {
            val mp3 = edgeEngine.synthesize("测试")
            val ok = mp3.isNotEmpty()
            _edgeAvailable.value = ok
            _lastStatus.value = if (ok) "Edge 连通正常，合成 ${mp3.size} 字节" else "Edge 未返回音频数据"
            mp3.size
        } catch (e: Exception) {
            _edgeAvailable.value = false
            _lastStatus.value = "Edge 连接失败: ${e.message}"
            0
        }
    }

    /** 重新检测所有引擎状态（设置页"重新检测"按钮用） */
    suspend fun recheck(): Boolean = withContext(Dispatchers.IO) {
        initSystemTts()
        var waited = 0
        while (!systemTtsReady && waited < 2000) {
            delay(100); waited += 100
        }

        // 检测当前引擎
        val voice = _currentVoice.value
        when (voice.engine) {
            VoiceOption.ENGINE_EDGE -> { testEdgeConnection() }
            VoiceOption.ENGINE_BAIDU_TRANSLATE -> {
                try {
                    val mp3 = baiduTransEngine.synthesize("测试")
                    val ok = mp3.isNotEmpty()
                    _engineAvailable.value = ok
                    _lastStatus.value = if (ok) "百度翻译TTS连通正常，${mp3.size}字节" else "百度翻译未返回数据"
                } catch (e: Exception) {
                    _engineAvailable.value = false
                    _lastStatus.value = "百度翻译TTS: ${e.message}"
                }
            }
            VoiceOption.ENGINE_YOUDAO -> {
                try {
                    val mp3 = youdaoEngine.synthesize("测试")
                    val ok = mp3.isNotEmpty()
                    _engineAvailable.value = ok
                    _lastStatus.value = if (ok) "有道TTS连通正常，${mp3.size}字节" else "有道未返回数据"
                } catch (e: Exception) {
                    _engineAvailable.value = false
                    _lastStatus.value = "有道TTS: ${e.message}"
                }
            }
            VoiceOption.ENGINE_BAIDU_AIP -> {
                if (!baiduAipEngine.hasCredentials()) {
                    _lastStatus.value = "百度智能云：请先配置 API Key"
                } else {
                    try {
                        val mp3 = baiduAipEngine.synthesize("测试")
                        val ok = mp3.isNotEmpty()
                        _engineAvailable.value = ok
                        _lastStatus.value = if (ok) "百度智能云TTS连通正常，${mp3.size}字节" else "百度智能云未返回数据"
                    } catch (e: Exception) {
                        _engineAvailable.value = false
                        _lastStatus.value = "百度智能云TTS: ${e.message}"
                    }
                }
            }
            VoiceOption.ENGINE_SYSTEM -> {
                _lastStatus.value = if (systemTtsReady) "系统TTS就绪" else "系统TTS未就绪"
            }
        }
        true
    }

    private fun playMp3(data: ByteArray): Boolean {
        return try {
            val tmp = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tmp).use { it.write(data) }
            Log.d(TAG, "playMp3: 写入 ${data.size} 字节到 ${tmp.absolutePath}")
            mediaPlayer?.release()
            val mp = MediaPlayer()
            mp.setDataSource(tmp.absolutePath)
            mp.setOnCompletionListener {
                mp.release(); tmp.delete()
                if (mediaPlayer == mp) mediaPlayer = null
            }
            mp.setOnErrorListener { m, what, extra ->
                Log.e(TAG, "MediaPlayer 错误 what=$what extra=$extra")
                m.release(); tmp.delete(); if (mediaPlayer == m) mediaPlayer = null
                true
            }
            mp.prepare()
            // 确保音量拉满，避免被某侧通道静音
            mp.setVolume(1.0f, 1.0f)
            mp.start()
            mediaPlayer = mp
            Log.d(TAG, "playMp3: 播放已启动")
            true
        } catch (e: Exception) {
            Log.e(TAG, "playMp3 失败: ${e.message}")
            false
        }
    }

    private suspend fun systemFallback(text: String): Boolean {
        if (!systemTtsReady) initSystemTts()
        var waited = 0
        while (!systemTtsReady && waited < 2500) {
            delay(100)
            waited += 100
        }
        if (!systemTtsReady) {
            Log.w(TAG, "系统 TTS 未就绪，播报失败")
            return false
        }
        return try {
            systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
            Log.d(TAG, "系统 TTS 播报: $text")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统 TTS 失败: ${e.message}")
            false
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
    }

    companion object {
        private const val TAG = "TtsService"
    }
}
