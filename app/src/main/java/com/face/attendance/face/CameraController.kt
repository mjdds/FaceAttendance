package com.face.attendance.face

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.view.View
import android.view.ViewTreeObserver
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val overlay: BoundingBoxOverlay,
    private val analyser: FrameAnalyser?
) {
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var stopped = false

    // 镜头方向可切换（默认前置，便于人脸录入/签到）
    @Volatile var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
        private set

    // 持有本 controller 绑定的 use case，stop/switch 时只解绑自己的，绝不 unbindAll
    @Volatile private var boundPreview: Preview? = null
    @Volatile private var boundImageAnalysis: ImageAnalysis? = null

    // 防止 layout 监听重复绑定
    @Volatile private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** 相机成功绑定到生命周期后的回调（首帧出流前即可触发） */
    var onCameraBound: (() -> Unit)? = null

    @Volatile private var bound = false
    // 模型就绪后通过 attachAnalyser 注入，bind 时据此决定是否绑 ImageAnalysis
    @Volatile private var pendingAnalyser: FrameAnalyser? = analyser

    fun start() {
        stopped = false
        bound = false
        startWhenReady()
    }

    /**
     * 等待 PreviewView 完成首次 layout 再绑定相机。
     *
     * 根因：首次进入页面时 AndroidView(factory={previewView}) 刚挂载，previewView 的
     * width/height 尚为 0，此时 Preview.setSurfaceProvider(previewView.surfaceProvider)
     * 绑定的是一个无效的空 Surface —— Preview 无画面输出，表现为黑屏。
     * 用户切换摄像头时重新 bind，此时 previewView 已完成布局，Surface 就绪，故能正常显示。
     *
     * 修复：width>0 才 bind；否则监听 globalLayout，布局完成后回调 bind，并移除监听避免重复触发。
     */
    private fun startWhenReady() {
        if (stopped) return
        if (previewView.width > 0 && previewView.height > 0) {
            bind()
            return
        }
        previewView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (stopped) {
                        previewView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    if (previewView.width > 0 && previewView.height > 0) {
                        previewView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        layoutListener = null
                        bind()
                    }
                }
            }.also { layoutListener = it }
        )
    }

    /** 切换前后摄像头 */
    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        // 主动解绑当前 use case 再重新绑定（此时 previewView 必然已 layout）
        unbindOwn()
        bind()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bind() {
        overlay.cameraFacing = lensFacing
        overlay.resetTransform()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                unbindOwn()

                val targetFpsRange = com.face.attendance.util.PerformanceManager.targetFpsRange
                val preview = Preview.Builder()
                    .also { builder ->
                        Camera2Interop.Extender(builder)
                            .setCaptureRequestOption(
                                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                targetFpsRange
                            )
                    }
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                boundPreview = preview

                // analyser 可为 null（模型尚未就绪）：仅绑预览即可出画面
                val an = pendingAnalyser
                if (an != null) {
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    // 省流模式降低分析分辨率，单帧位图内存显著下降
                    if (com.face.attendance.util.PerformanceManager.lowPerformanceMode) {
                        val shortSide = com.face.attendance.util.PerformanceManager.analysisShortSide
                        analysisBuilder.setTargetResolution(
                            android.util.Size(shortSide * 4 / 3, shortSide)
                        )
                    }
                    val imageAnalysis = analysisBuilder
                        .also { builder ->
                            Camera2Interop.Extender(builder)
                                .setCaptureRequestOption(
                                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                    targetFpsRange
                                )
                        }
                        .build()
                    imageAnalysis.setAnalyzer(executor) { image ->
                        an.analyze(image)
                    }
                    boundImageAnalysis = imageAnalysis
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview,
                        imageAnalysis
                    )
                } else {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview
                    )
                }
                bound = true
                onCameraBound?.invoke()
            } catch (e: Exception) {
                Log.e("CameraController", "bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 模型就绪后补绑识别分析（ImageAnalysis）。
     * 相机预览可先于模型加载启动，此方法在模型就绪后调用，无需重新启动相机。
     */
    fun attachAnalyser(analyser: FrameAnalyser) {
        analyser.boundingBoxOverlay = overlay
        pendingAnalyser = analyser
        // 若相机已绑定，重新 bind 以挂上 ImageAnalysis；未绑定时 start() 会自动带上
        if (bound && !stopped) {
            unbindOwn()
            bind()
        }
    }

    private fun unbindOwn() {
        val provider = cameraProvider ?: return
        boundPreview?.let { provider.unbind(it) }
        boundImageAnalysis?.let { provider.unbind(it) }
    }

    fun stop() {
        stopped = true
        layoutListener?.let {
            try { previewView.viewTreeObserver.removeOnGlobalLayoutListener(it) } catch (_: Throwable) {}
            layoutListener = null
        }
        unbindOwn()
        boundPreview = null
        boundImageAnalysis = null
    }
}