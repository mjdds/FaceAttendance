package com.face.attendance.face

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.core.graphics.toRectF

class BoundingBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var areDimsInit = false
    var frameHeight = 0
    var frameWidth = 0

    var cameraFacing: Int = CameraSelector.LENS_FACING_FRONT

    var faceBoundingBoxes: List<Prediction>? = null
    var drawMaskLabel: Boolean = false

    private val output2OverlayTransform = Matrix()

    private val boxPaint = Paint().apply {
        color = Color.argb(77, 0x39, 0x91, 0xFF)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
        isAntiAlias = true
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val labelBgPaint = Paint().apply {
        color = Color.argb(199, 0x10, 0x10, 0x14)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val predictions = faceBoundingBoxes ?: return
        if (!areDimsInit) {
            if (frameWidth == 0 || frameHeight == 0) return
            val vw = width.toFloat()
            val vh = height.toFloat()
            // 与 PreviewView.ScaleType.FILL_CENTER 对齐：等比放大填满并居中裁剪
            val scale = maxOf(vw / frameWidth, vh / frameHeight)
            val drawnW = frameWidth * scale
            val drawnH = frameHeight * scale
            val dx = (vw - drawnW) / 2f
            val dy = (vh - drawnH) / 2f
            output2OverlayTransform.setScale(scale, scale)
            output2OverlayTransform.postTranslate(dx, dy)
            areDimsInit = true
        }
        for (face in predictions) {
            val r = RectF(face.bbox).also { output2OverlayTransform.mapRect(it) }
            canvas.drawRoundRect(r, 24f, 24f, boxPaint)
            canvas.drawRoundRect(r, 24f, 24f, borderPaint)
            val label = face.maskLabel.ifEmpty { face.label }
            if (label.isNotEmpty()) {
                val labelWidth = textPaint.measureText(label) + 24f
                val labelHeight = textPaint.textSize + 16f
                val lx = (r.left + (r.width() - labelWidth) / 2f).coerceIn(0f, (width - labelWidth))
                val ly = (r.top - labelHeight - 8f).coerceAtLeast(4f)
                canvas.drawRoundRect(
                    RectF(lx, ly, lx + labelWidth, ly + labelHeight),
                    labelHeight / 2f, labelHeight / 2f, labelBgPaint
                )
                canvas.drawText(
                    label,
                    lx + 12f,
                    ly + textPaint.textSize,
                    textPaint
                )
            }
        }
    }

    fun resetTransform() {
        areDimsInit = false
    }
}