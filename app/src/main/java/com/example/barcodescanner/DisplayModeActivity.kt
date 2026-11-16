package com.example.barcodescanner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.barcodescanner.camera.CameraManager
import com.example.barcodescanner.data.PreferencesManager
import com.example.barcodescanner.data.database.AppDatabase
import com.example.barcodescanner.data.database.entities.HistoryEntity
import com.example.barcodescanner.databinding.DisplayModeLayoutBinding
import com.example.barcodescanner.scanner.BarcodeResult
import com.example.barcodescanner.scanner.BarcodeScannerManager
import com.example.barcodescanner.scanner.toDisplayName
import com.example.barcodescanner.utils.ImageSaver
import com.example.barcodescanner.utils.SoundPlayer
import com.example.barcodescanner.utils.Util
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisplayModeActivity : AppCompatActivity() {

    private lateinit var binding: DisplayModeLayoutBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var barcodeScannerManager: BarcodeScannerManager
    private lateinit var database: AppDatabase
    private var cameraManager: CameraManager? = null

    private var isFlashOn = false

    @Volatile
    private var isScanningPaused = false

    /** 同一时间只允许一帧在解码，保证 ML Kit 与多帧确认状态串行访问 */
    private val isProcessingFrame = AtomicBoolean(false)

    /** 相机启停代次，用于丢弃页面暂停后返回的过期识别结果 */
    @Volatile
    private var scanSession = 0

    private var zoomLevel = 1.0f
    private var scanStartTime = 0L
    private var scanCount = 0

    companion object {
        private const val TAG = "DisplayModeActivity"
        private const val RESULT_PLACEHOLDER = "{result}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DisplayModeLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)
        database = (application as App).database

        barcodeScannerManager = BarcodeScannerManager(preferencesManager)
        barcodeScannerManager.applySettings()

        setupUI()
        setupClickListeners()
        showTipCard()
    }

    private fun setupUI() {
        binding.rlResultView.visibility = View.GONE
        updateFlashButtonIcon()

        // 动态设置取景框尺寸：76mm × 130mm，根据屏幕分辨率等比缩放
        setupViewfinderSize()

        val initialZoom = preferencesManager.getString(PreferencesManager.KEY_INITIAL_ZOOM, "0").toFloatOrNull() ?: 0f
        if (initialZoom > 0) {
            zoomLevel = initialZoom
        }

        // 点击屏幕四周关闭结果对话框并恢复扫描
        binding.previewView.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && isScanningPaused) {
                view.performClick()
                hideResultView()
                true
            } else {
                false
            }
        }

        binding.scanningOverlay.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && isScanningPaused) {
                view.performClick()
                hideResultView()
                true
            } else {
                false
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.zoomButton.setOnClickListener {
            toggleZoom()
        }

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }

        binding.historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnSearch.setOnClickListener {
            submitToWebhook(binding.tvBarcodeResult.text.toString())
        }

        binding.btnCopy.setOnClickListener {
            Util.copyToClipboard(this, binding.tvBarcodeResult.text.toString())
        }
    }

    private fun startCamera() {
        if (cameraManager != null) return

        val manager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
            onFrameAvailable = { imageProxy ->
                processFrame(imageProxy)
            }
        )
        cameraManager = manager
        manager.startCamera(
            preferencesManager.getBoolean(PreferencesManager.KEY_USE_FRONT_CAMERA, false)
        )
        scanStartTime = System.currentTimeMillis()
    }

    private fun stopCamera() {
        cameraManager?.stopCamera()
        cameraManager = null
        isFlashOn = false
        updateFlashButtonIcon()
    }

    private fun processFrame(imageProxy: androidx.camera.core.ImageProxy) {
        // 扫码成功后暂停继续解码，直到用户关闭对话框
        if (isScanningPaused) {
            imageProxy.close()
            return
        }

        // 上一帧仍在校验中，直接丢弃当前帧，避免多帧并行解码
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val session = scanSession
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = barcodeScannerManager.scanFrame(imageProxy)
                if (result != null) {
                    // 页面已暂停或相机已重启，丢弃过期结果
                    if (session != scanSession) return@launch

                    // 扫码成功后保存图片，必须在 imageProxy 关闭前保存
                    val imagePath = try {
                        ImageSaver.save(
                            context = this@DisplayModeActivity,
                            imageProxy = imageProxy,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                            timestamp = result.scanTimestamp
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "保存图片失败", e)
                        null
                    }

                    // 历史记录与截图路径一次性写入，避免“先插入后更新”的竞态
                    saveToHistory(result, imagePath)

                    withContext(Dispatchers.Main) {
                        onBarcodeDetected(result)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "扫码处理异常", e)
            } finally {
                isProcessingFrame.set(false)
                imageProxy.close()
            }
        }
    }

    private fun onBarcodeDetected(result: BarcodeResult) {
        if (isFinishing || isDestroyed) return

        // 先暂停解码，避免提示音/振动期间重复识别和重复入库
        isScanningPaused = true

        if (preferencesManager.getBoolean(PreferencesManager.KEY_NOTIFICATION_SOUND_ENABLED, true)) {
            SoundPlayer.playScanSound(this)
        }

        if (preferencesManager.getBoolean(PreferencesManager.KEY_NOTIFICATION_VIBRATE_ENABLED, true)) {
            vibrate()
        }

        if (preferencesManager.getBoolean(PreferencesManager.KEY_AUTO_COPY_TO_CLIPBOARD, false)) {
            Util.copyToClipboard(this, result.text)
        }

        if (preferencesManager.getBoolean(PreferencesManager.KEY_OPEN_URL_AUTOMATICALLY, false)) {
            // 自动提交期间保持暂停，返回后 onResume 再恢复扫描
            submitToWebhook(result.text)
            return
        }

        showResult(result)
    }

    private fun submitToWebhook(scanResult: String) {
        val webhookUrl = preferencesManager.getString(
            PreferencesManager.KEY_CUSTOM_WEBHOOK_URL,
            "https://example.com/api?code={result}"
        )

        val finalUrl = if (webhookUrl.contains(RESULT_PLACEHOLDER)) {
            webhookUrl.replace(RESULT_PLACEHOLDER, Uri.encode(scanResult))
        } else {
            webhookUrl + Uri.encode(scanResult)
        }

        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("url", finalUrl)
        }
        startActivity(intent)
    }

    private fun showResult(result: BarcodeResult) {
        val duration = (System.currentTimeMillis() - scanStartTime) / 1000.0
        binding.tvLastScanningDuration.text = getString(R.string.scan_duration, String.format("%.1f", duration))
        binding.tvBarcodeType.text = result.barcodeType
        binding.tvBarcodeSubType.text = result.barcodeFormat.toDisplayName()
        binding.tvBarcodeResult.text = result.text
        binding.rlResultView.visibility = View.VISIBLE
        isScanningPaused = true

        scanCount++
    }

    private fun hideResultView() {
        binding.rlResultView.visibility = View.GONE
        isScanningPaused = false
        scanStartTime = System.currentTimeMillis()
    }

    private suspend fun saveToHistory(result: BarcodeResult, imagePath: String?) {
        withContext(Dispatchers.IO) {
            val entity = HistoryEntity(
                barcodeType = result.barcodeType,
                barcodeFormat = result.barcodeFormat.toDisplayName(),
                resultText = result.text,
                isGS1 = result.isGS1,
                rawBytes = result.rawBytes,
                orientation = result.orientation,
                timestamp = result.scanTimestamp,
                imagePath = imagePath
            )
            database.historyDao().insert(entity)
        }
    }

    private fun toggleFlash() {
        val manager = cameraManager ?: return
        val targetState = !isFlashOn

        if (!manager.toggleFlash(targetState)) {
            Toast.makeText(this, R.string.flash_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        isFlashOn = targetState
        updateFlashButtonIcon()
    }

    private fun updateFlashButtonIcon() {
        binding.flashButton.alpha = if (isFlashOn) 1.0f else 0.4f
        binding.flashButton.contentDescription = getString(
            if (isFlashOn) R.string.flash_on else R.string.flash_off
        )
    }

    private fun toggleZoom() {
        val manager = cameraManager ?: return
        val maxZoom = manager.getMaxZoom()
        val minZoom = manager.getMinZoom()
        zoomLevel = if (zoomLevel <= minZoom) maxZoom else minZoom
        manager.setZoomLevel(zoomLevel)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return

        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun setupViewfinderSize() {
        // 76mm × 130mm 物理尺寸转换为 dp
        val mmToDp = 160f / 25.4f
        val targetWidthDp = (76f * mmToDp).toInt()   // ≈ 479dp
        val targetHeightDp = (130f * mmToDp).toInt()  // ≈ 819dp

        // 获取屏幕尺寸
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidthDp = (metrics.widthPixels / metrics.density).toInt()
        val screenHeightDp = (metrics.heightPixels / metrics.density).toInt()

        // 首次启动时保存屏幕分辨率信息
        val screenInfoKey = "screen_info_saved"
        if (!preferencesManager.getBoolean(screenInfoKey, false)) {
            preferencesManager.putString("screen_width_px", metrics.widthPixels.toString())
            preferencesManager.putString("screen_height_px", metrics.heightPixels.toString())
            preferencesManager.putString("screen_density", metrics.density.toString())
            preferencesManager.putString("screen_density_dpi", metrics.densityDpi.toString())
            preferencesManager.putBoolean(screenInfoKey, true)
        }

        // 取景框最大不超过屏幕 80%
        val maxWidthDp = (screenWidthDp * 0.8f).toInt()
        val maxHeightDp = (screenHeightDp * 0.8f).toInt()

        var actualWidthDp = targetWidthDp
        var actualHeightDp = targetHeightDp

        if (actualWidthDp > maxWidthDp || actualHeightDp > maxHeightDp) {
            val widthRatio = maxWidthDp.toFloat() / actualWidthDp
            val heightRatio = maxHeightDp.toFloat() / actualHeightDp
            val scale = kotlin.math.min(widthRatio, heightRatio)
            actualWidthDp = (actualWidthDp * scale).toInt()
            actualHeightDp = (actualHeightDp * scale).toInt()
        }

        val actualWidthPx = (actualWidthDp * metrics.density).toInt()
        val actualHeightPx = (actualHeightDp * metrics.density).toInt()
        val params = binding.scanningOverlay.layoutParams
        params.width = actualWidthPx
        params.height = actualHeightPx
        binding.scanningOverlay.layoutParams = params
    }

    private fun showTipCard() {
        val isFirstLaunch = preferencesManager.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            Toast.makeText(this, R.string.welcome_tip, Toast.LENGTH_LONG).show()
            preferencesManager.putBoolean("is_first_launch", false)
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeScannerManager.applySettings()

        // 结果层仍然显示（例如从历史页返回）时保持暂停，否则恢复扫描
        isScanningPaused = binding.rlResultView.visibility == View.VISIBLE

        startCamera()
        scanStartTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        // 页面不可见后不再接收旧结果
        scanSession++
        stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanSession++
        stopCamera()
        barcodeScannerManager.release()
    }
}
