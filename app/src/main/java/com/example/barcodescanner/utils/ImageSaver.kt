package com.example.barcodescanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream

object ImageSaver {

    // 图片保存目录：App 私有目录 files/images/
    private const val IMAGES_DIR = "images"
    private const val FORMAT = "webp"
    private const val COMPRESS_QUALITY = 80

    /**
     * 将相机帧保存为 WebP 图片
     * @param context 上下文
     * @param imageProxy 相机帧数据
     * @param rotationDegrees 旋转角度（来自 imageProxy.imageInfo.rotationDegrees）
     * @param timestamp 时间戳（用作文件名）
     * @return 保存后的文件绝对路径，失败返回 null
     */
    fun save(context: Context, imageProxy: ImageProxy, rotationDegrees: Int, timestamp: Long): String? {
        return try {
            val mediaImage = imageProxy.image ?: return null
            val bitmap = yuvImageToBitmap(mediaImage)
            val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
            bitmap.recycle()
            val fileName = "${timestamp}.$FORMAT"
            val file = getOutputFile(context, fileName)
            saveWebp(rotatedBitmap, file)
            rotatedBitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("ImageSaver", "保存图片失败", e)
            null
        }
    }

    // 将 YUV_420_888 格式的 Image 转换为 Bitmap
    private fun yuvImageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val argb = IntArray(width * height)
        var i = 0
        for (j in 0 until height) {
            for (k in 0 until width) {
                val yIndex = j * yRowStride + k
                val y = yBuffer.get(yIndex.toInt()).toInt() and 0xFF

                val uvIndex = (j / 2) * uvRowStride + (k / 2) * uvPixelStride
                val u = uBuffer.get(uvIndex).toInt() and 0xFF
                val v = vBuffer.get(uvIndex).toInt() and 0xFF

                val r = (y + (1.402 * (v - 128)).toInt()).coerceIn(0, 255)
                val g = (y - (0.344 * (u - 128)).toInt() - (0.714 * (v - 128)).toInt()).coerceIn(0, 255)
                val b = (y + (1.772 * (u - 128)).toInt()).coerceIn(0, 255)

                argb[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    // 按角度旋转 Bitmap，使保存的图片方向与屏幕方向一致
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getOutputFile(context: Context, fileName: String): File {
        val dir = File(context.filesDir, IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName)
    }

    private fun saveWebp(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, COMPRESS_QUALITY, fos)
            fos.flush()
        }
    }
}