package com.example.barcodescanner.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.barcodescanner.data.PreferencesManager

object SoundPlayer {

    private var audioTrack: AudioTrack? = null

    fun playScanSound(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.getBoolean(PreferencesManager.KEY_NOTIFICATION_SOUND_ENABLED, true)) {
            return
        }

        val frequency = prefs.getString(PreferencesManager.KEY_NOTIFICATION_SOUND_FREQUENCY, "1000").toIntOrNull() ?: 1000
        val duration = prefs.getString(PreferencesManager.KEY_NOTIFICATION_SOUND_LENGTH, "150").toIntOrNull() ?: 150
        val volume = prefs.getString(PreferencesManager.KEY_NOTIFICATION_SOUND_VOLUME, "80").toIntOrNull() ?: 80
        val modulation = prefs.getString(PreferencesManager.KEY_NOTIFICATION_SOUND_MODULATION, "0").toIntOrNull() ?: 0

        val sampleRate = 44100
        val numSamples = duration * sampleRate / 1000
        val generatedSound = DoubleArray(numSamples)

        for (i in 0 until numSamples) {
            val modFreq = if (modulation > 0) {
                frequency + (Math.sin(2.0 * Math.PI * i / sampleRate * modulation) * frequency * 0.5).toInt()
            } else {
                frequency
            }
            generatedSound[i] = Math.sin(2.0 * Math.PI * i / sampleRate * modFreq)
        }

        val generatedSoundByte = ByteArray(numSamples * 2)
        for (i in generatedSound.indices) {
            val sample = (generatedSound[i] * 32767 * volume / 100).toInt().toShort()
            generatedSoundByte[i * 2] = (sample.toInt() and 0x00ff).toByte()
            generatedSoundByte[i * 2 + 1] = ((sample.toInt() and 0xff00) shr 8).toByte()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack?.play()
        audioTrack?.write(generatedSoundByte, 0, generatedSoundByte.size)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
