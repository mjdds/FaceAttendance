package com.face.attendance.face

import android.graphics.Rect

data class Prediction(
    val bbox: Rect,
    val label: String,
    val maskLabel: String = ""
)