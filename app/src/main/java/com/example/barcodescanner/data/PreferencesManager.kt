package com.example.barcodescanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class PreferencesManager(context: Context) {

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_QR_ENABLED = "QR"
        const val KEY_DATAMATRIX_ENABLED = "DataMatrix"
        const val KEY_CODE39_ENABLED = "Code39"
        const val KEY_UPCEAN_ENABLED = "UPCEAN"
        const val KEY_CODE128_ENABLED = "Code128"
        const val KEY_PDF_ENABLED = "PDF"
        const val KEY_AZTEC_ENABLED = "Aztec"
        const val KEY_CODE93_ENABLED = "Code93"
        const val KEY_CODE2OF5_ENABLED = "Code2of5"
        const val KEY_CODABAR_ENABLED = "Codabar"

        const val KEY_SCAN_ORIENTATIONS = "scanOrientations"
        const val KEY_SCANNING_RESOLUTION = "scanningResolution"
        const val KEY_EFFORT_LEVEL = "effortLevel"
        const val KEY_USE_FRONT_CAMERA = "useFrontCamera"
        const val KEY_INITIAL_ZOOM = "initialZoom"

        const val KEY_NOTIFICATION_SOUND_ENABLED = "notificationSoundEnabled"
        const val KEY_NOTIFICATION_SOUND_FREQUENCY = "notificationSoundFrequencyValue"
        const val KEY_NOTIFICATION_SOUND_LENGTH = "notificationSoundLengthValue"
        const val KEY_NOTIFICATION_SOUND_MODULATION = "notificationSoundModulationValue"
        const val KEY_NOTIFICATION_SOUND_VOLUME = "notificationSoundVolumeValue"
        const val KEY_NOTIFICATION_VIBRATE_ENABLED = "notificationVibrateEnabled"

        const val KEY_AUTO_COPY_TO_CLIPBOARD = "autoCopyToClipboard"
        const val KEY_OPEN_URL_AUTOMATICALLY = "openUrlAutomatically"
        const val KEY_CUSTOM_WEBHOOK_URL = "customWebhookUrl"
        const val KEY_READ_STRING_ENCODING = "readStringEncoding"
    }

    fun isBarcodeEnabled(key: String): Boolean {
        return preferences.getBoolean(key, key == KEY_CODE128_ENABLED)
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun getSharedPreferences(): SharedPreferences = preferences
}
