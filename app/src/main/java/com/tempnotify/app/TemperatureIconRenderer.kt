package com.tempnotify.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat

object TemperatureIconRenderer {

    /**
     * Renders a crisp monochrome temperature icon (e.g. "72°") designed specifically
     * for the Android status bar.
     */
    fun createTemperatureIcon(tempF: Int): IconCompat {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val text = "$tempF°"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            isFakeBoldText = true
            textSize = when {
                text.length <= 2 -> 76f // e.g. "8°"
                text.length == 3 -> 68f // e.g. "72°"
                else -> 50f             // e.g. "102°" or "-12°"
            }
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        // Center tightly in the 96x96 status bar icon canvas
        val x = size / 2f
        val y = (size / 2f) - bounds.exactCenterY()
        canvas.drawText(text, x, y, paint)

        return IconCompat.createWithBitmap(bitmap)
    }
}
