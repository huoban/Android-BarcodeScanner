package com.example.barcodescanner.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

/**
 * 支持手势拖动和缩放的图片查看控件。
 *
 * 默认将图片完整缩放并居中显示；只有放大后才允许拖动，
 * 缩放或拖动结束后会自动校正边界，避免图片被拖出视野。
 */
class TouchImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_ZOOM_FACTOR = 5.0f
        private const val DOUBLE_TAP_ZOOM_FACTOR = 2.5f
        private const val EPSILON = 0.01f
    }

    private val matrix = Matrix()

    var isZoomed = false
        private set

    /** 图片完整显示时的缩放比例 */
    private var baseScale = 1.0f

    /** 当前实际缩放比例 */
    private var currentScale = 1.0f

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
        fitImageToView()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        fitImageToView()
    }

    /** 默认完整显示并居中（等比缩放，不裁剪） */
    private fun fitImageToView() {
        val drawable = drawable ?: return
        if (viewWidth <= 0f || viewHeight <= 0f) return

        drawableWidth = drawable.intrinsicWidth.toFloat()
        drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        baseScale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)
        currentScale = baseScale
        resetToFit()
    }

    /** 恢复到“完整显示 + 居中”状态 */
    private fun resetToFit() {
        val scaledWidth = drawableWidth * currentScale
        val scaledHeight = drawableHeight * currentScale

        // 先缩放，再平移，保证平移量不会被再次缩放
        matrix.setScale(currentScale, currentScale)
        matrix.postTranslate(
            (viewWidth - scaledWidth) / 2f,
            (viewHeight - scaledHeight) / 2f
        )

        isZoomed = false
        imageMatrix = matrix
    }

    /** 校正平移：小于视图时居中，大于视图时不允许出现空白边 */
    private fun constrainMatrix() {
        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        val values = FloatArray(9)
        matrix.getValues(values)
        val scaledWidth = drawableWidth * currentScale
        val scaledHeight = drawableHeight * currentScale
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val targetX = if (scaledWidth <= viewWidth) {
            (viewWidth - scaledWidth) / 2f
        } else {
            transX.coerceIn(viewWidth - scaledWidth, 0f)
        }

        val targetY = if (scaledHeight <= viewHeight) {
            (viewHeight - scaledHeight) / 2f
        } else {
            transY.coerceIn(viewHeight - scaledHeight, 0f)
        }

        matrix.postTranslate(targetX - transX, targetY - transY)
        imageMatrix = matrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            constrainMatrix()
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (drawableWidth <= 0f || drawableHeight <= 0f) return false

            val targetScale = (currentScale * detector.scaleFactor)
                .coerceIn(baseScale, baseScale * MAX_ZOOM_FACTOR)
            val actualFactor = targetScale / currentScale
            if (abs(actualFactor - 1f) < 0.0001f) return true

            matrix.postScale(actualFactor, actualFactor, detector.focusX, detector.focusY)
            currentScale = targetScale
            isZoomed = currentScale > baseScale + EPSILON
            imageMatrix = matrix
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
            // 默认适配状态下图片完整可见，不需要拖动
            if (currentScale <= baseScale + EPSILON) return false

            matrix.postTranslate(-distanceX, -distanceY)
            constrainMatrix()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > baseScale + EPSILON) {
                // 已放大：恢复完整显示并居中
                currentScale = baseScale
                resetToFit()
            } else {
                // 默认状态：以双击位置为中心放大
                val targetScale = (baseScale * DOUBLE_TAP_ZOOM_FACTOR)
                    .coerceAtMost(baseScale * MAX_ZOOM_FACTOR)
                val factor = targetScale / currentScale
                matrix.postScale(factor, factor, e.x, e.y)
                currentScale = targetScale
                isZoomed = true
                constrainMatrix()
            }
            return true
        }
    }
}
