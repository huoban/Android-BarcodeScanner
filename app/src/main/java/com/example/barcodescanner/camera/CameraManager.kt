package com.example.barcodescanner.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrameAvailable: (ImageProxy) -> Unit
) {
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var isStopped = false

    companion object {
        private const val TAG = "CameraManager"
    }

    fun startCamera(useFrontCamera: Boolean = false) {
        if (isStopped) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            // 异步回调期间页面可能已经停止，避免停止后重新绑定
            if (isStopped) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(useFrontCamera)
            } catch (e: Exception) {
                Log.e(TAG, "摄像头初始化失败", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(useFrontCamera: Boolean) {
        if (isStopped) return
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val selector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (isStopped) {
                        imageProxy.close()
                    } else {
                        onFrameAvailable(imageProxy)
                    }
                }
            }

        try {
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            Log.d(TAG, "摄像头绑定成功")
        } catch (e: Exception) {
            Log.e(TAG, "摄像头绑定失败", e)
        }
    }

    /**
     * 切换闪光灯。
     * @return true 表示设备支持并已执行，false 表示当前相机不支持闪光灯
     */
    fun toggleFlash(enabled: Boolean): Boolean {
        val camera = this.camera ?: return false
        if (!camera.cameraInfo.hasFlashUnit()) return false
        camera.cameraControl.enableTorch(enabled)
        return true
    }

    fun setZoomLevel(level: Float) {
        val cameraControl = camera?.cameraControl ?: return
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val clampedLevel = level.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cameraControl.setZoomRatio(clampedLevel)
    }

    fun getMaxZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1.0f
    }

    fun getMinZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1.0f
    }

    /** 幂等停止：重复调用不会重复释放线程池 */
    fun stopCamera() {
        if (isStopped) return
        isStopped = true

        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "停止摄像头失败", e)
        }

        imageAnalysis = null
        cameraProvider = null
        camera = null
        cameraExecutor.shutdown()
        Log.d(TAG, "摄像头已停止")
    }
}
