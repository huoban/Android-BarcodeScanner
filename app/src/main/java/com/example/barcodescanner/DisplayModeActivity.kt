package com.example.barcodescanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisplayModeActivity : AppCompatActivity() {

    private lateinit var binding: DisplayModeLayoutBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var barcodeScannerManager: BarcodeScannerManager
    private lateinit var cameraManager: CameraManager
    private lateinit var database: AppDatabase

    private var isFlashOn = false
    private var isRapidScanning = false
    private var isScanningPaused = false
    private var zoomLevel = 1.0f
    private var scanStartTime = 0L
    private var scanCount = 0
    private var rapidScanDelayMs: Long = 1500L

    private val mainHandler = Handler(Looper.getMainLooper())

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
        setupCamera()
        showTipCard()
    }

    private fun setupUI() {
        binding.rlResultView.visibility = View.GONE
        binding.rlRapidText.visibility = View.GONE

        // 动态设置取景框尺寸：76mm × 130mm，根据屏幕分辨率等比缩放
        setupViewfinderSize()

        val initialZoom = preferencesManager.getString(PreferencesManager.KEY_INITIAL_ZOOM, "0").toFloatOrNull() ?: 0f
        if (initialZoom > 0) {
            zoomLevel = initialZoom
        }

        if (preferencesManager.getBoolean(PreferencesManager.KEY_USE_RAPID_SCANNING, false)) {
            isRapidScanning = true
            binding.rlRapidText.visibility = View.VISIBLE
        }

        // 从设置读取快速扫描延迟，默认 1500ms
        rapidScanDelayMs = preferencesManager.getString(PreferencesManager.KEY_SCANNER_DELAY, "1500").toLongOrNull() ?: 1500L

        // 点击屏幕四周关闭结果对话框并恢复扫描
        binding.previewView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && isScanningPaused) {
                hideResultView()
                true
            } else {
                false
            }
        }

        binding.scanningOverlay.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN && isScanningPaused) {
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

        binding.btnClearRapid.setOnClickListener {
            binding.rapidTextView.text = ""
            scanCount = 0
        }

        binding.btnSearch.setOnClickListener {
            submitToWebhook(binding.tvBarcodeResult.text.toString())
        }

        binding.btnCopy.setOnClickListener {
            Util.copyToClipboard(this, binding.tvBarcodeResult.text.toString())
        }
    }

    private fun setupCamera() {
        val useFrontCamera = preferencesManager.getBoolean(PreferencesManager.KEY_USE_FRONT_CAMERA, false)

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
            onFrameAvailable = { imageProxy ->
                processFrame(imageProxy)
            }
        )

        cameraManager.startCamera(useFrontCamera)
        scanStartTime = System.currentTimeMillis()
    }

    private fun processFrame(imageProxy: androidx.camera.core.ImageProxy) {
        // 扫码成功后暂停继续解码，直到用户关闭对话框
        if (isScanningPaused) {
            imageProxy.close()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = barcodeScannerManager.scanFrame(imageProxy)
                var imagePath: String? = null
                if (result != null) {
                    // 扫码成功后保存图片，在 imageProxy 关闭前保存
                    try {
                        imagePath = ImageSaver.save(
                            context = this@DisplayModeActivity,
                            imageProxy = imageProxy,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                            timestamp = result.scanTimestamp
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "保存图片失败", e)
                    }

                    withContext(Dispatchers.Main) {
                        onBarcodeDetected(result)
                    }

                    if (imagePath != null) {
                        saveImagePathToHistory(result.scanTimestamp, imagePath)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "扫码处理异常", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun onBarcodeDetected(result: BarcodeResult) {
        if (preferencesManager.getBoolean(PreferencesManager.KEY_NOTIFICATION_SOUND_ENABLED, true)) {
            SoundPlayer.playScanSound(this)
        }

        if (preferencesManager.getBoolean(PreferencesManager.KEY_NOTIFICATION_VIBRATE_ENABLED, true)) {
            vibrate()
        }

        saveToHistory(result)

        if (preferencesManager.getBoolean(PreferencesManager.KEY_AUTO_COPY_TO_CLIPBOARD, false)) {
            Util.copyToClipboard(this, result.text)
        }

        if (preferencesManager.getBoolean(PreferencesManager.KEY_OPEN_URL_AUTOMATICALLY, false)) {
            submitToWebhook(result.text)
            return
        }

        showResult(result)

        if (isRapidScanning) {
            appendToRapidText(result)
            mainHandler.postDelayed({
                hideResultView()
            }, rapidScanDelayMs)
        }
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

    private fun appendToRapidText(result: BarcodeResult) {
        val newText = "[${scanCount}] ${result.barcodeType}: ${result.text}\n"
        binding.rapidTextView.append(newText)
    }

    private fun saveToHistory(result: BarcodeResult) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entity = HistoryEntity(
                barcodeType = result.barcodeType,
                barcodeFormat = result.barcodeFormat.toDisplayName(),
                resultText = result.text,
                isGS1 = result.isGS1,
                rawBytes = result.rawBytes,
                orientation = result.orientation,
                timestamp = result.scanTimestamp
            )
            database.historyDao().insert(entity)
        }
    }

    private fun saveImagePathToHistory(timestamp: Long, imagePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            database.historyDao().updateImagePath(timestamp, imagePath)
        }
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        cameraManager.toggleFlash(isFlashOn)
        updateFlashButtonIcon()
    }

    private fun updateFlashButtonIcon() {
        val iconRes = android.R.drawable.ic_menu_gallery
        binding.flashButton.setImageResource(iconRes)
    }

    private fun toggleZoom() {
        val maxZoom = cameraManager.getMaxZoom()
        val minZoom = cameraManager.getMinZoom()
        zoomLevel = if (zoomLevel <= minZoom) maxZoom else minZoom
        cameraManager.setZoomLevel(zoomLevel)
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
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
        val useFrontCamera = preferencesManager.getBoolean(PreferencesManager.KEY_USE_FRONT_CAMERA, false)
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
            onFrameAvailable = { imageProxy ->
                processFrame(imageProxy)
            }
        )
        cameraManager.startCamera(useFrontCamera)
        scanStartTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        cameraManager.stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScannerManager.release()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
