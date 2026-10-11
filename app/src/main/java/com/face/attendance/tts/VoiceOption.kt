package com.face.attendance.tts

/**
 * 语音模型选项。
 *
 * 引擎类型：
 * - [ENGINE_EDGE]：微软 Edge 在线语音（免费、高音质，需网络，国内可能需代理）
 * - [ENGINE_BAIDU_TRANSLATE]：百度翻译 TTS（免费免 Key，国内直连）
 * - [ENGINE_YOUDAO]：有道词典 TTS（免费免 Key，国内直连）
 * - [ENGINE_BAIDU_AIP]：百度智能云语音合成（需 API Key，有免费额度）
 * - [ENGINE_SYSTEM]：Android 系统 TTS（离线，依赖设备已安装的语音引擎）
 */
data class VoiceOption(
    val id: String,
    val displayName: String,
    val description: String,
    val engine: String,
    val edgeVoiceId: String? = null,
    val gender: String = "女声",
    /** 引擎参数：百度翻译=语速(1-5)，有道=发音类型(0-3)，百度AIP=发音人编号 */
    val engineParam: Int = 0,
    /** 是否需要在设置中配置 API 密钥 */
    val needsApiKey: Boolean = false,
    /** 提供商名称（用于 UI 分组显示） */
    val provider: String = "其他"
) {
    companion object {
        const val ENGINE_EDGE = "edge"
        const val ENGINE_BAIDU_TRANSLATE = "baidu_translate"
        const val ENGINE_YOUDAO = "youdao"
        const val ENGINE_BAIDU_AIP = "baidu_aip"
        const val ENGINE_SYSTEM = "system"
    }
}

/**
 * 所有可选语音模型，按提供商分组。
 *
 * 免费免Key：百度翻译TTS、有道词典TTS
 * 免费额度需Key：百度智能云TTS（多款发音人）
 * 微软在线：Edge TTS（多款 Neural 音色，国内可能需代理）
 * 离线：系统 TTS
 */
fun availableVoices(): List<VoiceOption> = listOf(
    // ── 百度翻译 TTS（免费免Key，国内直连）──
    VoiceOption(
        id = "baidu_trans_normal",
        displayName = "百度翻译·标准",
        description = "百度翻译 · 标准语速 · 免费免Key · 国内直连",
        engine = VoiceOption.ENGINE_BAIDU_TRANSLATE,
        engineParam = 3,
        gender = "女声",
        provider = "百度翻译"
    ),
    VoiceOption(
        id = "baidu_trans_fast",
        displayName = "百度翻译·快速",
        description = "百度翻译 · 快速语速 · 免费免Key · 国内直连",
        engine = VoiceOption.ENGINE_BAIDU_TRANSLATE,
        engineParam = 5,
        gender = "女声",
        provider = "百度翻译"
    ),

    // ── 有道词典 TTS（免费免Key，国内直连）──
    VoiceOption(
        id = "youdao_us",
        displayName = "有道·美音",
        description = "有道词典 · 美式发音 · 免费免Key · 国内直连",
        engine = VoiceOption.ENGINE_YOUDAO,
        engineParam = 0,
        gender = "女声",
        provider = "有道词典"
    ),
    VoiceOption(
        id = "youdao_uk",
        displayName = "有道·英音",
        description = "有道词典 · 英式发音 · 免费免Key · 国内直连",
        engine = VoiceOption.ENGINE_YOUDAO,
        engineParam = 1,
        gender = "女声",
        provider = "有道词典"
    ),

    // ── 百度智能云 TTS（需API Key，有免费额度）──
    VoiceOption(
        id = "baidu_aip_female",
        displayName = "百度云·度小美",
        description = "百度智能云 · 普通女声 · 需配置API Key · 有免费额度",
        engine = VoiceOption.ENGINE_BAIDU_AIP,
        engineParam = 0,
        gender = "女声",
        needsApiKey = true,
        provider = "百度智能云"
    ),
    VoiceOption(
        id = "baidu_aip_male",
        displayName = "百度云·度小宇",
        description = "百度智能云 · 普通男声 · 需配置API Key · 有免费额度",
        engine = VoiceOption.ENGINE_BAIDU_AIP,
        engineParam = 1,
        gender = "男声",
        needsApiKey = true,
        provider = "百度智能云"
    ),
    VoiceOption(
        id = "baidu_aip_xiaoyao",
        displayName = "百度云·度逍遥",
        description = "百度智能云 · 情感男声 · 需配置API Key · 有免费额度",
        engine = VoiceOption.ENGINE_BAIDU_AIP,
        engineParam = 3,
        gender = "男声",
        needsApiKey = true,
        provider = "百度智能云"
    ),
    VoiceOption(
        id = "baidu_aip_yaya",
        displayName = "百度云·度丫丫",
        description = "百度智能云 · 情感女声 · 需配置API Key · 有免费额度",
        engine = VoiceOption.ENGINE_BAIDU_AIP,
        engineParam = 4,
        gender = "女声",
        needsApiKey = true,
        provider = "百度智能云"
    ),

    // ── 微软 Edge TTS（免费，国内可能需代理）──
    VoiceOption(
        id = "edge_xiaoxiao",
        displayName = "微软·晓晓",
        description = "微软 Edge · 温暖女声 · 在线高音质 · 国内可能需代理",
        engine = VoiceOption.ENGINE_EDGE,
        edgeVoiceId = "zh-CN-XiaoxiaoNeural",
        gender = "女声",
        provider = "微软 Edge"
    ),
    VoiceOption(
        id = "edge_yunxi",
        displayName = "微软·云希",
        description = "微软 Edge · 阳光男声 · 在线高音质 · 国内可能需代理",
        engine = VoiceOption.ENGINE_EDGE,
        edgeVoiceId = "zh-CN-YunxiNeural",
        gender = "男声",
        provider = "微软 Edge"
    ),
    VoiceOption(
        id = "edge_yunyang",
        displayName = "微软·云扬",
        description = "微软 Edge · 专业男声 · 在线高音质 · 国内可能需代理",
        engine = VoiceOption.ENGINE_EDGE,
        edgeVoiceId = "zh-CN-YunyangNeural",
        gender = "男声",
        provider = "微软 Edge"
    ),
    VoiceOption(
        id = "edge_xiaoyi",
        displayName = "微软·晓伊",
        description = "微软 Edge · 清亮女声 · 在线高音质 · 国内可能需代理",
        engine = VoiceOption.ENGINE_EDGE,
        edgeVoiceId = "zh-CN-XiaoyiNeural",
        gender = "女声",
        provider = "微软 Edge"
    ),
    VoiceOption(
        id = "edge_xiaohan",
        displayName = "微软·晓涵",
        description = "微软 Edge · 知性女声 · 在线高音质 · 国内可能需代理",
        engine = VoiceOption.ENGINE_EDGE,
        edgeVoiceId = "zh-CN-XiaohanNeural",
        gender = "女声",
        provider = "微软 Edge"
    ),

    // ── 系统离线 TTS ──
    VoiceOption(
        id = "system_default",
        displayName = "系统语音",
        description = "Android 系统 TTS · 离线可用 · 设备内置引擎",
        engine = VoiceOption.ENGINE_SYSTEM,
        edgeVoiceId = null,
        gender = "默认",
        provider = "系统离线"
    )
)
