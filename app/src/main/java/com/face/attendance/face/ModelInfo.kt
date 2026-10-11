package com.face.attendance.face

class ModelInfo(
    val name: String,
    val assetsFilename: String,
    val cosineThreshold: Float,
    val l2Threshold: Float,
    val outputDims: Int,
    val inputDims: Int
) {
    companion object {
        // facenet.tflite: input 160x160, output 128, l2 distance threshold 10
        val FACENET = ModelInfo("FaceNet", "facenet.tflite", 0.4f, 10f, 128, 160)
    }
}