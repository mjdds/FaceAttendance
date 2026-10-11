package com.face.attendance.face

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect

object BitmapUtils {

    fun cropRectFromBitmap(source: Bitmap, rect: Rect): Bitmap {
        var left = rect.left.coerceAtLeast(0)
        var top = rect.top.coerceAtLeast(0)
        var width = rect.width().coerceAtMost(source.width - left)
        var height = rect.height().coerceAtMost(source.height - top)
        if (width <= 0 || height <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }

    fun flipBitmap(source: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}