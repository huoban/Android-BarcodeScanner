package com.example.barcodescanner.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.barcodescanner.data.database.entities.HistoryEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun export(context: Context, records: List<HistoryEntity>): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "barcode_history_$timestamp.csv"
        val csvFile = File(context.cacheDir, fileName)

        FileWriter(csvFile).use { writer ->
            // 写入 CSV 头
            writer.append("序号,条码类型,条码格式,扫描结果,是否GS1,扫描时间,方向\n")

            records.forEachIndexed { index, record ->
                val escapedText = record.resultText.replace(",", "，").replace("\n", " ").replace("\"", "\"\"")
                val isGS1Str = if (record.isGS1) "是" else "否"
                val timeStr = Util.formatTimestamp(record.timestamp)

                writer.append("${index + 1},")
                writer.append("${record.barcodeType},")
                writer.append("${record.barcodeFormat},")
                writer.append("\"$escapedText\",")
                writer.append("$isGS1Str,")
                writer.append("$timeStr,")
                writer.append("${record.orientation}\n")
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "导出 CSV"))

        return uri
    }
}
