package com.example.barcodescanner.scanner

import android.graphics.Point
import com.google.mlkit.vision.barcode.common.Barcode

data class BarcodeResult(
    val text: String,
    val barcodeType: String,
    val barcodeFormat: Int,
    val isGS1: Boolean = false,
    val rawBytes: ByteArray? = null,
    val cornerPoints: Array<Point>? = null,
    val orientation: Int = 0,
    val scanTimestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BarcodeResult
        if (text != other.text) return false
        if (barcodeType != other.barcodeType) return false
        if (barcodeFormat != other.barcodeFormat) return false
        if (isGS1 != other.isGS1) return false
        if (orientation != other.orientation) return false
        if (scanTimestamp != other.scanTimestamp) return false
        if (rawBytes != null) {
            if (other.rawBytes == null) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false
        } else if (other.rawBytes != null) return false
        if (cornerPoints != null) {
            if (other.cornerPoints == null) return false
            if (!cornerPoints.contentEquals(other.cornerPoints)) return false
        } else if (other.cornerPoints != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + barcodeType.hashCode()
        result = 31 * result + barcodeFormat
        result = 31 * result + isGS1.hashCode()
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        result = 31 * result + (cornerPoints?.contentHashCode() ?: 0)
        result = 31 * result + orientation
        result = 31 * result + scanTimestamp.hashCode()
        return result
    }
}

fun Int.toDisplayName(): String = when (this) {
    Barcode.FORMAT_QR_CODE -> "QR Code"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "Aztec"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_CODE_93 -> "Code 93"
    Barcode.FORMAT_CODABAR -> "Codabar"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_ITF -> "ITF"
    else -> "Unknown ($this)"
}
