package com.example.barcodescanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.barcodescanner.view.TouchImageView
import java.io.File

/**
 * 图片查看页 - 支持手势拖动和缩放
 */
class ImagePreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.image_preview_title)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath.isNullOrEmpty()) {
            Toast.makeText(this, "图片路径为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(imagePath)
        if (!file.exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val touchImageView = findViewById<TouchImageView>(R.id.touchImageView)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = 1
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        if (bitmap != null) {
            touchImageView.setImageBitmap(bitmap)
        } else {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}