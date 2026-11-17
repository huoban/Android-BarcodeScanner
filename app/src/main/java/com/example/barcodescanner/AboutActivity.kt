package com.example.barcodescanner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class AboutActivity : AppCompatActivity() {

    companion object {
        private const val PROJECT_URL = "https://github.com/huoban/Android-BarcodeScanner"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<TextView>(R.id.tv_app_version).text = getString(R.string.app_version, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.tv_ml_kit_version).text = getString(R.string.ml_kit_version, "17.3.0")

        findViewById<TextView>(R.id.tv_project_url).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.about_link_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
