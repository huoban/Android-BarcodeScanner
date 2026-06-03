package com.example.barcodescanner.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * 支持手势拖动和缩放的图片查看控件
 */
class TouchImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_ZOOM = 1.0f
        private const val MAX_ZOOM = 5.0f
        private const val DOUBLE_TAP_ZOOM = 2.5f
    }

    private val matrix = Matrix()

    var isZoomed = false
        private set

    private var scaleFactor = 1.0f

    // 手势检测
    private val scaleDetector by lazy {
        ScaleGestureDetector(context, ScaleListener())
    }

    private val gestureDetector by lazy {
        GestureDetector(context, GestureListener())
    }

    private var viewWidth = 0f
    private var viewHeight = 0f
    private var drawableWidth = 0f
    private var drawableHeight = 0f

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (viewWidth > 0 && viewHeight > 0) {
            fitImageToView()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        fitImageToView()
    }

    private fun fitImageToView() {
        val drawable = drawable ?: return
        drawableWidth = drawable.intrinsicWidth.toFloat()
        drawableHeight = drawable.intrinsicHeight.toFloat()

        if (drawableWidth <= 0 || drawableHeight <= 0) return

        val widthScale = viewWidth / drawableWidth
        val heightScale = viewHeight / drawableHeight
        scaleFactor = kotlin.math.min(widthScale, heightScale)

        matrix.reset()
        val tx = (viewWidth - drawableWidth * scaleFactor) / 2f
        val ty = (viewHeight - drawableHeight * scaleFactor) / 2f
        matrix.postTranslate(tx, ty)
        matrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
        imageMatrix = matrix
        isZoomed = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            rectify()
        }
        return true
    }

    private fun rectify() {
        if (scaleFactor <= MIN_ZOOM) {
            scaleFactor = MIN_ZOOM
            matrix.reset()
            val tx = (viewWidth - drawableWidth * scaleFactor) / 2f
            val ty = (viewHeight - drawableHeight * scaleFactor) / 2f
            matrix.postTranslate(tx, ty)
            matrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
            isZoomed = false
        } else {
            val matrixLeft = getMatrixLeft()
            val matrixRight = getMatrixRight()
            val matrixTop = getMatrixTop()
            val matrixBottom = getMatrixBottom()

            if (matrixRight < viewWidth) {
                val offset = viewWidth - matrixRight
                matrix.postTranslate(offset / 2f, 0f)
            } else if (matrixLeft > 0) {
                matrix.postTranslate(-matrixLeft / 2f, 0f)
            }

            if (matrixBottom < viewHeight) {
                val offset = viewHeight - matrixBottom
                matrix.postTranslate(0f, offset / 2f)
            } else if (matrixTop > 0) {
                matrix.postTranslate(0f, -matrixTop / 2f)
            }

            isZoomed = true
        }
        imageMatrix = matrix
    }

    private fun getMatrixLeft(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MTRANS_X]
    }

    private fun getMatrixRight(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MTRANS_X] + drawableWidth * scaleFactor
    }

    private fun getMatrixTop(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MTRANS_Y]
    }

    private fun getMatrixBottom(): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MTRANS_Y] + drawableHeight * scaleFactor
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newScale = scaleFactor * factor

            if (newScale >= MIN_ZOOM && newScale <= MAX_ZOOM) {
                matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                scaleFactor = newScale
                imageMatrix = matrix
            }
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isZoomed) {
                matrix.postTranslate(-distanceX, -distanceY)
                imageMatrix = matrix
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isZoomed) {
                scaleFactor = MIN_ZOOM
                matrix.reset()
                val tx = (viewWidth - drawableWidth * scaleFactor) / 2f
                val ty = (viewHeight - drawableHeight * scaleFactor) / 2f
                matrix.postTranslate(tx, ty)
                matrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
                isZoomed = false
            } else {
                matrix.postScale(DOUBLE_TAP_ZOOM, DOUBLE_TAP_ZOOM, e.x, e.y)
                scaleFactor *= DOUBLE_TAP_ZOOM
                isZoomed = true
            }
            imageMatrix = matrix
            return true
        }
    }
}