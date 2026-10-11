package com.face.attendance.util

import android.content.Context
import android.util.Range

/**
 * 性能模式管理（单例）。
 *
 * 低端平板开启「省流模式」后，集中降低以下开销，显著减小内存压力与闪退概率：
 * - 关闭 Dock 液态玻璃多层渐变/描边绘制（改用单层半透明背景），减轻 GPU/合成负担
 * - 相机帧率区间由 30-120 降到 15-30，减少送帧与推理次数
 * - ImageAnalysis 目标分辨率降到 640x480，单帧位图内存大幅下降
 * - 帧分析节流（约每 400ms 最多推理一次），丢弃中间帧
 * - 帧位图改用 RGB_565（相比 ARGB_8888 内存减半）
 * - FaceNet 推理线程数 4 → 2，降低低端 CPU 线程争用与峰值内存
 * - 关闭自动补采特征的额外写库与裁剪开销
 *
 * 标志位为 @Volatile 静态值，相机/分析器可实时读取；
 * 帧率/分辨率/线程数在相机重新绑定或应用重启后完全生效。
 */
object PerformanceManager {
    private const val PREFS = "perf_prefs"
    private const val KEY_LOW_PERF = "low_performance_mode"

    @Volatile
    var lowPerformanceMode: Boolean = false
        private set

    /** 从持久化配置初始化（应用启动时调用一次） */
    fun init(context: Context) {
        lowPerformanceMode = prefs(context).getBoolean(KEY_LOW_PERF, false)
    }

    /** 切换省流模式并持久化 */
    fun setLowPerformance(context: Context, enabled: Boolean) {
        lowPerformanceMode = enabled
        prefs(context).edit().putBoolean(KEY_LOW_PERF, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 派生参数：相机/分析器直接读取 ──

    /** 相机目标帧率区间 */
    val targetFpsRange: Range<Int>
        get() = if (lowPerformanceMode) Range(15, 30) else Range(30, 120)

    /** ImageAnalysis 目标分辨率短边 */
    val analysisShortSide: Int
        get() = if (lowPerformanceMode) 480 else 720

    /** 帧推理最小间隔（毫秒），低于此间隔的帧只绘制追踪框、不跑 FaceNet */
    val inferenceIntervalMs: Long
        get() = if (lowPerformanceMode) 400L else 0L

    /** FaceNet TFLite 推理线程数 */
    val modelThreads: Int
        get() = if (lowPerformanceMode) 2 else 4

    /** 是否开启自动补采特征（省流模式关闭以减少额外裁剪/写库） */
    val autoCollectEnabled: Boolean
        get() = !lowPerformanceMode
}
