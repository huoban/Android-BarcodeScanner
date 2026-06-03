package com.example.barcodescanner

import android.app.Application
import com.example.barcodescanner.data.PreferencesManager
import com.example.barcodescanner.data.database.AppDatabase

class App : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this)
    }
}
