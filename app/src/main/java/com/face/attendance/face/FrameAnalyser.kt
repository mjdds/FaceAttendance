package com.face.attendance.face

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.face.attendance.store.Person
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

class FrameAnalyser(
    context: Context,
    var boundingBoxOverlay: BoundingBoxOverlay,
    private var model: FaceNetModel
) : ImageAnalysis.Analyzer {

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()
    private val detector = FaceDetection.getClient(realTimeOpts)

    // 参与识别的人员：Triple(人员id, 姓名, 特征向量)
    private var faceList = mutableListOf<Triple<Long, String, FloatArray>>()
    private var personList = mutableListOf<Person>()

    // 用于人脸识别的 L2 距离阈值，越低越严格
    private val metricToBeUsed = "l2"
    @Volatile private var isProcessing = false

    // 稳定识别参数：连续同名帧数 / 距离阈值系数
    private val STABLE_FRAMES = 3
    private val STABLE_RATIO = 0.7f

    /** 是否为录入模式（录入模式不与已有人员匹配） */
    var enrollMode = false

    /** 最近一帧检测到的人脸 embedding（用于录入采集） */
    @Volatile
    var lastEmbedding: FloatArray? = null
        private set

    /** lastEmbedding 生成的时间戳，用于判断新鲜度 */
    @Volatile
    var lastEmbeddingTs: Long = 0L
        private set

    /** 最近一帧的人脸位图（采集时截取，用于录入与自动采集存图） */
    @Volatile
    var lastFaceBitmap: Bitmap? = null
        private set

    /** 人脸状态变化回调（detected=true 表示当前帧检测到人脸） */
    var onFaceStateChange: ((Boolean) -> Unit)? = null

    /** 判断 lastEmbedding 是否新鲜（默认 2 秒内） */
    fun hasFreshEmbedding(maxAgeMs: Long = 2000L): Boolean {
        val emb = lastEmbedding ?: return false
        return System.currentTimeMillis() - lastEmbeddingTs < maxAgeMs
    }

    /** 清除缓存的 embedding（摄像头停止或退出录入模式时调用） */
    fun clearLastEmbedding() {
        lastEmbedding = null
        lastEmbeddingTs = 0L
        lastFaceBitmap = null
        stableName = null
        stableCount = 0
        signInStates.clear()
        lastLabeledPredictions = emptyList()
        // 重置处理互斥标志：关闭摄像头时若恰有帧在 ML Kit 推理中，
        // 回调可能不再触发，标志位会永久卡 true，导致重新开启后所有帧被丢弃
        isProcessing = false
        isInferring = false
    }

    /** 识别到人员时的回调（姓名/l2距离/人脸框）；多人同框时各自独立计时，同一帧可回调多人 */
    var onRecognized: ((name: String, distance: Float, bbox: android.graphics.Rect) -> Unit)? = null

    /**
     * 识别稳定回调：连续 [STABLE_FRAMES] 帧识别为同一人且距离足够小时触发，
     * 传出 name + 当前帧 embedding + 当前帧人脸位图，用于自动采集补充特征。
     * 触发后重置计数，避免连续重复；频率由调用方额外 cooldown 控制。
     */
    var onStableRecognized: ((name: String, embedding: FloatArray, faceBitmap: Bitmap) -> Unit)? = null

    // 稳定识别计数
    @Volatile private var stableName: String? = null
    @Volatile private var stableCount: Int = 0

    // 签到触发：每个人独立累计「在画面中」的时长，达到 SIGN_IN_HOLD_MS 即触发一次。
    // 多人同框互不影响；漏检/遮挡间隔小于 SIGN_IN_GRACE_MS 时时长连续累计，超过则重新计时。
    private class SignInState(var accumulatedMs: Long = 0L, var lastSeenTs: Long = 0L, var fired: Boolean = false)
    private val signInStates = HashMap<Long, SignInState>()
    private val SIGN_IN_HOLD_MS = 100L
    private val SIGN_IN_GRACE_MS = 1500L

    // FaceNet 推理互斥：防止多帧重叠推理
    @Volatile private var isInferring = false

    // 省流模式：记录上次 FaceNet 推理时间，用于帧节流（中间帧只绘制追踪框）
    @Volatile private var lastInferTs: Long = 0L

    /** 省流模式是否开启（实时读取） */
    private val lowPerf: Boolean
        get() = com.face.attendance.util.PerformanceManager.lowPerformanceMode

    // 上一帧带标签的预测结果，用于快速绘制时 IoU 匹配继承标签
    @Volatile private var lastLabeledPredictions: List<Prediction> = emptyList()

    fun setPersons(persons: List<Person>) {
        personList = persons.toMutableList()
        // 只保留已录入人脸特征的人员参与识别，过滤掉仅有名字尚未补录的
        faceList = persons.filter { it.embedding.isNotEmpty() }
            .map { Triple(it.id, it.name, it.embedding) }.toMutableList()
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        if (isProcessing) {
            image.close()
            return
        }
        isProcessing = true
        try {
            val cameraXImage = image.image!!
            val plane = image.planes[0]
            val w = cameraXImage.width
            val h = cameraXImage.height
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val buffer = plane.buffer
            // 始终使用 ARGB_8888：TFLite TensorImage.fromBitmap 仅支持该 config，
            // 省流模式的内存优化通过降低分析分辨率实现，不能改用 RGB_565（会导致特征提取异常）
            val frameBitmap = if (pixelStride == 4 && rowStride == w * 4) {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    buffer.rewind()
                    it.copyPixelsFromBuffer(buffer)
                }
            } else {
                // 处理 rowStride 含 padding 的情况
                val argb = IntArray(w * h)
                val rowBytes = ByteArray(rowStride)
                var pos = 0
                buffer.rewind()
                for (y in 0 until h) {
                    buffer.get(rowBytes, 0, rowStride)
                    for (x in 0 until w) {
                        val i = x * pixelStride
                        val r = rowBytes[i].toInt() and 0xff
                        val g = rowBytes[i + 1].toInt() and 0xff
                        val b = rowBytes[i + 2].toInt() and 0xff
                        argb[pos++] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
            }
            var frameBitmap2 = BitmapUtils.rotateBitmap(
                frameBitmap, image.imageInfo.rotationDegrees.toFloat()
            )
            // 前置摄像头需要水平翻转，使 overlay 坐标与预览一致
            if (boundingBoxOverlay.cameraFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT) {
                frameBitmap2 = BitmapUtils.flipBitmap(frameBitmap2)
            }

            if (!boundingBoxOverlay.areDimsInit) {
                boundingBoxOverlay.frameHeight = frameBitmap2.height
                boundingBoxOverlay.frameWidth = frameBitmap2.width
            }

            val inputImage = InputImage.fromBitmap(frameBitmap2, 0)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    // ── 快速路径：ML Kit 检测后立即更新追踪框（不等待 FaceNet）──
                    val activeFaces = dedupeFaces(faces)
                    val fastPredictions = matchLabelsFromLast(activeFaces)
                    boundingBoxOverlay.faceBoundingBoxes = fastPredictions
                    boundingBoxOverlay.invalidate()
                    onFaceStateChange?.invoke(activeFaces.isNotEmpty())
                    // 释放 isProcessing，允许下一帧进入 analyze（追踪框高帧率）
                    isProcessing = false
                    // ── 慢速路径：异步运行 FaceNet 特征提取与匹配（互斥防重叠）──
                    // 省流模式按 inferenceIntervalMs 节流，间隔内的帧只保留追踪框、不跑模型
                    val nowTs = System.currentTimeMillis()
                    val inferInterval = com.face.attendance.util.PerformanceManager.inferenceIntervalMs
                    val inferAllowed = inferInterval <= 0L || nowTs - lastInferTs >= inferInterval
                    if (activeFaces.isNotEmpty() && !isInferring && inferAllowed) {
                        isInferring = true
                        lastInferTs = nowTs
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                runModel(activeFaces, frameBitmap2)
                            } catch (e: Exception) {
                                Log.e("FrameAnalyser", "infer error: ${e.message}")
                            } finally {
                                isInferring = false
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    isProcessing = false
                }
                .addOnCompleteListener {
                    image.close()
                }
        } catch (e: Exception) {
            Log.e("FrameAnalyser", "analyze error: ${e.message}")
            isProcessing = false
            image.close()
        }
    }

    private suspend fun runModel(activeFaces: List<Face>, cameraFrameBitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            try {
                // Phase 1：逐框裁剪 + 提取特征向量
                val items = ArrayList<Triple<Face, FloatArray, Bitmap>>(activeFaces.size)
                for (face in activeFaces) {
                    try {
                        val croppedBitmap = BitmapUtils.cropRectFromBitmap(
                            cameraFrameBitmap, face.boundingBox
                        )
                        val subject = model.getFaceEmbedding(croppedBitmap)
                        if (activeFaces.size == 1) {
                            lastEmbedding = subject
                            lastEmbeddingTs = System.currentTimeMillis()
                            lastFaceBitmap = croppedBitmap
                        }
                        items.add(Triple(face, subject, croppedBitmap))
                    } catch (e: Exception) {
                        Log.e("FrameAnalyser", "face error: ${e.message}")
                    }
                }

                // ── 去重（第 2 层）：一对一贪心匹配 ──
                // 同一注册人员在同一帧内只分配给最相似的检测框，
                // 避免多个检测框重复框选/显示同一个人
                val matchedName = HashMap<Int, String>()   // faceIdx -> 姓名
                val matchedPerson = HashMap<Int, Long>()  // faceIdx -> 人员id
                val matchedScore = HashMap<Int, Float>()  // faceIdx -> 距离/相似度
                if (!enrollMode && faceList.isNotEmpty()) {
                    val cosine = metricToBeUsed == "cosine"
                    val candidates = ArrayList<Triple<Int, Int, Float>>() // faceIdx, personIdx, score
                    for (i in items.indices) {
                        for (j in faceList.indices) {
                            val s = if (cosine) cosineSimilarity(items[i].second, faceList[j].third)
                            else L2Norm(items[i].second, faceList[j].third)
                            val pass = if (cosine) s > model.model.cosineThreshold
                            else s < model.model.l2Threshold
                            if (pass) candidates.add(Triple(i, j, s))
                        }
                    }
                    // L2 升序（小者优先）/ 余弦降序（大者优先）：全局最优的配对先占位
                    if (cosine) candidates.sortByDescending { it.third }
                    else candidates.sortBy { it.third }
                    val usedPersons = HashSet<Int>()
                    for ((fi, pj, s) in candidates) {
                        if (matchedName.containsKey(fi) || pj in usedPersons) continue
                        matchedName[fi] = faceList[pj].second
                        matchedPerson[fi] = faceList[pj].first
                        matchedScore[fi] = s
                        usedPersons.add(pj)
                    }
                }

                // Phase 2：生成预测结果 + 回调
                val predictions = ArrayList<Prediction>(items.size)
                val frameTs = System.currentTimeMillis()
                // 本帧识别成功的人员 id 集合，用于对累计计时表做“在场对账”
                val seenIds = HashSet<Long>()
                for (i in items.indices) {
                    val face = items[i].first
                    val subject = items[i].second
                    val croppedBitmap = items[i].third
                    val bestScoreUserName: String
                    val bestScore: Float
                    when {
                        enrollMode || faceList.isEmpty() -> {
                            bestScoreUserName = ""
                            bestScore = 0f
                        }
                        matchedName.containsKey(i) -> {
                            bestScoreUserName = matchedName[i]!!
                            bestScore = matchedScore[i]!!
                        }
                        else -> {
                            bestScoreUserName = "未识别"
                            bestScore = 0f
                        }
                    }
                    predictions.add(Prediction(face.boundingBox, bestScoreUserName, ""))

                    // 多人同框：每个识别成功的人独立累计「在画面中」的时长，互不影响
                    val pid = matchedPerson[i]
                    if (!enrollMode && pid != null) {
                        seenIds.add(pid)
                        val st = signInStates[pid] ?: SignInState().also { signInStates[pid] = it }
                        val gap = frameTs - st.lastSeenTs
                        // 首次出现或离开超过宽限时间则重新计时，否则时长连续累计
                        st.accumulatedMs =
                            if (st.lastSeenTs == 0L || gap > SIGN_IN_GRACE_MS) 0L
                            else st.accumulatedMs + gap
                        st.lastSeenTs = frameTs
                        // 累计在画面中满 SIGN_IN_HOLD_MS（0.1 秒）即签到成功，每人只触发一次
                        if (!st.fired && st.accumulatedMs >= SIGN_IN_HOLD_MS) {
                            st.fired = true
                            withContext(Dispatchers.Main) {
                                onRecognized?.invoke(bestScoreUserName, bestScore, face.boundingBox)
                            }
                        }

                        // 稳定识别自动采集：仅保留单人正脸时触发，避免多人互相污染样本
                        if (items.size == 1) {
                            if (bestScoreUserName == stableName) {
                                stableCount++
                            } else {
                                stableName = bestScoreUserName
                                stableCount = 1
                            }
                            val stable = !lowPerf &&
                                stableCount >= STABLE_FRAMES &&
                                bestScore < model.model.l2Threshold * STABLE_RATIO &&
                                croppedBitmap.width > 1
                            if (stable) {
                                val embCopy = subject.copyOf()
                                stableCount = 0 // 重置，避免连续触发；频率由 VM 侧 cooldown 二次控制
                                withContext(Dispatchers.Main) {
                                    onStableRecognized?.invoke(bestScoreUserName, embCopy, croppedBitmap)
                                }
                            }
                        }
                    } else if (!enrollMode && items.size == 1) {
                        // 单人但未识别：重置稳定计数
                        stableName = null
                        stableCount = 0
                    }
                }
                // 清理本帧未出现、离开已超过宽限时间的人，再次入镜重新计时
                val cleanupIter = signInStates.entries.iterator()
                while (cleanupIter.hasNext()) {
                    val e = cleanupIter.next()
                    if (e.key !in seenIds && frameTs - e.value.lastSeenTs > SIGN_IN_GRACE_MS) {
                        cleanupIter.remove()
                    }
                }
                // 更新带标签的预测结果，供快速绘制时 IoU 匹配继承
                lastLabeledPredictions = predictions
                withContext(Dispatchers.Main) {
                    boundingBoxOverlay.faceBoundingBoxes = predictions
                    boundingBoxOverlay.invalidate()
                }
            } catch (e: Exception) {
                Log.e("FrameAnalyser", "runModel error: ${e.message}")
            }
        }
    }

    /**
     * 用上一帧的标签为当前帧的检测框快速匹配标签（IoU），
     * 使追踪框在 FaceNet 推理期间也能持续刷新（高帧率绘制）。
     */
    private fun matchLabelsFromLast(faces: List<Face>): List<Prediction> {
        val last = lastLabeledPredictions
        if (last.isEmpty()) {
            return faces.map { Prediction(it.boundingBox, "", "") }
        }
        return faces.map { face ->
            var bestLabel = ""
            var bestIou = 0.3f
            for (p in last) {
                val v = iou(face.boundingBox, p.bbox)
                if (v > bestIou) {
                    bestIou = v
                    bestLabel = p.label
                }
            }
            Prediction(face.boundingBox, bestLabel, "")
        }
    }

    /**
     * 同帧检测框去重：ML Kit 偶尔会对同一人物输出多个重叠框。
     * 按面积降序保留，与已保留框 IoU 超过阈值的视为重复并丢弃。
     */
    private fun dedupeFaces(faces: List<Face>): List<Face> {
        if (faces.size <= 1) return faces
        val sorted = faces.sortedByDescending {
            it.boundingBox.width().toLong() * it.boundingBox.height()
        }
        val kept = ArrayList<Face>(faces.size)
        outer@ for (f in sorted) {
            for (k in kept) {
                if (iou(f.boundingBox, k.boundingBox) > 0.5f) continue@outer
            }
            kept.add(f)
        }
        return kept
    }

    /** 两个矩形的交并比（IoU） */
    private fun iou(a: android.graphics.Rect, b: android.graphics.Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val inter = maxOf(0, right - left).toFloat() * maxOf(0, bottom - top).toFloat()
        val union = a.width().toFloat() * a.height() + b.width().toFloat() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun L2Norm(x1: FloatArray, x2: FloatArray): Float {
        return sqrt(x1.mapIndexed { i, xi -> (xi - x2[i]).pow(2) }.sum())
    }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        val mag1 = sqrt(x1.map { it * it }.sum())
        val mag2 = sqrt(x2.map { it * it }.sum())
        val dot = x1.mapIndexed { i, xi -> xi * x2[i] }.sum()
        return dot / (mag1 * mag2)
    }
}