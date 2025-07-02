package com.hiennv.flutter_callkit_incoming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.*
import android.text.TextUtils
import java.io.IOException

class CallkitSoundPlayerService : Service() {

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSoundAndVibration() // Pastikan tidak overlap
        playRingtone(intent)
        playVibration()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSoundAndVibration()
    }

    private fun stopSoundAndVibration() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    private fun playVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun playRingtone(intent: Intent?) {
        val soundPath = intent?.getStringExtra(CallkitConstants.EXTRA_CALLKIT_RINGTONE_PATH)
        val ringtoneUri = resolveRingtoneUri(soundPath)

        try {
            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_RING)
                }

                setDataSourceFromUri(this, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setDataSourceFromUri(player: MediaPlayer, uri: Uri?) {
        if (uri == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val afd = contentResolver.openAssetFileDescriptor(uri, "r")
                afd?.use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            } else {
                player.setDataSource(applicationContext, uri)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun resolveRingtoneUri(fileName: String?): Uri? {
        if (fileName.isNullOrEmpty()) {
            return RingtoneManager.getActualDefaultRingtoneUri(
                this, RingtoneManager.TYPE_RINGTONE
            )
        }

        val resId = resources.getIdentifier(fileName, "raw", packageName)
        return if (resId != 0) {
            Uri.parse("android.resource://$packageName/$resId")
        } else {
            RingtoneManager.getActualDefaultRingtoneUri(
                this, RingtoneManager.TYPE_RINGTONE
            )
        }
    }
}
