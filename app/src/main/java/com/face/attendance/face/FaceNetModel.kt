package com.face.attendance.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class FaceNetModel(
    context: Context,
    var model: ModelInfo,
    useGpu: Boolean,
    useXNNPack: Boolean
) {
    private val imgSize = model.inputDims
    val embeddingDim = model.outputDims

    private var interpreter: Interpreter
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    init {
        val options = Interpreter.Options().apply {
            // 省流模式下降到 2 线程，降低低端 CPU 线程争用与峰值内存
            numThreads = com.face.attendance.util.PerformanceManager.modelThreads
            setUseXNNPACK(useXNNPack)
        }
        interpreter = Interpreter(FileUtil.loadMappedFile(context, model.assetsFilename), options)
        Log.i("FaceNet", "Using ${model.name} model.")
    }

    fun getFaceEmbedding(image: Bitmap): FloatArray {
        return runFaceNet(convertBitmapToBuffer(image))[0]
    }

    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        val t1 = System.currentTimeMillis()
        val outputs = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputs, outputs)
        Log.i("FaceNet", "${model.name} inference ${System.currentTimeMillis() - t1}ms")
        return outputs
    }

    private fun convertBitmapToBuffer(image: Bitmap): ByteBuffer {
        return imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer
    }

    fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
    }

    class StandardizeOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt(pixels.map { (it - mean).pow(2) }.sum() / pixels.size.toFloat())
            std = max(std, 1f / sqrt(pixels.size.toFloat()))
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] - mean) / std
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(pixels)
            return output
        }
    }
}