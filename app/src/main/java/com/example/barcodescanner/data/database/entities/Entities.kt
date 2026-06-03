package com.example.barcodescanner.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["barcode_type"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "barcode_type") val barcodeType: String,
    @ColumnInfo(name = "barcode_format") val barcodeFormat: String,
    @ColumnInfo(name = "result_text") val resultText: String,
    @ColumnInfo(name = "is_gs1") val isGS1: Boolean = false,
    @ColumnInfo(name = "raw_bytes") val rawBytes: ByteArray? = null,
    @ColumnInfo(name = "orientation") val orientation: Int = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "image_path") val imagePath: String? = null
)
