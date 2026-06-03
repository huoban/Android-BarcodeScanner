package com.example.barcodescanner.scanner

import android.graphics.Bitmap
import android.graphics.Point
import androidx.camera.core.ImageProxy
import com.example.barcodescanner.data.PreferencesManager
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

class BarcodeScannerManager(private val preferencesManager: PreferencesManager) {

    private var barcodeScanner: BarcodeScanner = createScanner(defaultFormats)
    private var lastScanText: String = ""
    private var lastScanTime: Long = 0
    private var duplicatesTimeoutMs: Long = 400

    companion object {
        val defaultFormats = setOf(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR
        )
    }

    fun applySettings() {
        val enabledFormats = buildFormatSet()
        val prefs = preferencesManager.getSharedPreferences()
        barcodeScanner.close()
        barcodeScanner = createScanner(enabledFormats)
        duplicatesTimeoutMs = prefs.getString(PreferencesManager.KEY_DUPLICATES_TIMEOUT, "400")?.toLongOrNull() ?: 400
    }

    private fun buildFormatSet(): Set<Int> {
        val formats = mutableSetOf<Int>()

        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_QR_ENABLED)) {
            formats.add(Barcode.FORMAT_QR_CODE)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_DATAMATRIX_ENABLED)) {
            formats.add(Barcode.FORMAT_DATA_MATRIX)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_PDF_ENABLED)) {
            formats.add(Barcode.FORMAT_PDF417)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_CODE128_ENABLED)) {
            formats.add(Barcode.FORMAT_CODE_128)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_CODE39_ENABLED)) {
            formats.add(Barcode.FORMAT_CODE_39)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_UPCEAN_ENABLED)) {
            formats.add(Barcode.FORMAT_EAN_13)
            formats.add(Barcode.FORMAT_EAN_8)
            formats.add(Barcode.FORMAT_UPC_A)
            formats.add(Barcode.FORMAT_UPC_E)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_AZTEC_ENABLED)) {
            formats.add(Barcode.FORMAT_AZTEC)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_CODE93_ENABLED)) {
            formats.add(Barcode.FORMAT_CODE_93)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_CODE2OF5_ENABLED)) {
            formats.add(Barcode.FORMAT_ITF)
        }
        if (preferencesManager.isBarcodeEnabled(PreferencesManager.KEY_CODABAR_ENABLED)) {
            formats.add(Barcode.FORMAT_CODABAR)
        }

        return formats
    }

    private fun createScanner(formats: Set<Int>): BarcodeScanner {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                formats.firstOrNull() ?: Barcode.FORMAT_CODE_128,
                *formats.drop(1).toIntArray()
            )
            .build()
        return BarcodeScanning.getClient(options)
    }

    suspend fun scanFrame(imageProxy: ImageProxy): BarcodeResult? {
        val image = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        return try {
            val barcodes = barcodeScanner.process(image).await()
            val first = barcodes.firstOrNull() ?: return null
            if (isDuplicate(first.rawValue ?: "")) return null
            toBarcodeResult(first)
        } catch (e: IllegalArgumentException) {
            // 未启用任何条码格式时，ML Kit 会抛出参数异常
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun scanBitmap(bitmap: Bitmap): BarcodeResult? {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val barcodes = barcodeScanner.process(image).await()
            val first = barcodes.firstOrNull() ?: return null
            if (isDuplicate(first.rawValue ?: "")) return null
            toBarcodeResult(first)
        } catch (e: IllegalArgumentException) {
            // 未启用任何条码格式时，ML Kit 会抛出参数异常
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isDuplicate(text: String): Boolean {
        if (duplicatesTimeoutMs <= 0) return false
        val now = System.currentTimeMillis()
        if (text == lastScanText && (now - lastScanTime) < duplicatesTimeoutMs) return true
        lastScanText = text
        lastScanTime = now
        return false
    }

    private fun toBarcodeResult(barcode: Barcode): BarcodeResult {
        val points = barcode.cornerPoints?.map { point ->
            Point(point.x, point.y)
        }?.toTypedArray()

        val isGS1 = barcode.rawValue?.startsWith("]e0") == true

        return BarcodeResult(
            text = barcode.rawValue ?: "",
            barcodeType = barcode.format.toDisplayName(),
            barcodeFormat = barcode.format,
            isGS1 = isGS1,
            rawBytes = barcode.rawBytes,
            cornerPoints = points,
            orientation = 0
        )
    }

    fun release() {
        barcodeScanner.close()
    }
}
