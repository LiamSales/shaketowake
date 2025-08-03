package com.mobdeve.s15.shaketowake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private var soundPool: SoundPool? = null
        private var alarmSoundId: Int = 0
        private var isSoundPoolPreloaded = false

        // Pre-load SoundPool when app starts (call from MainActivity)
        fun preloadSoundPool(context: Context) {
            if (soundPool == null) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                soundPool = SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(audioAttributes)
                    .build()

                alarmSoundId = soundPool?.load(context, R.raw.alarm_sound, 1) ?: 0

                soundPool?.setOnLoadCompleteListener { _, _, _ ->
                    isSoundPoolPreloaded = true
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (isSoundPoolPreloaded) {
            soundPool?.play(alarmSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }

        // Launch AlarmDisplayActivity
        val alarmIntent = Intent(context, AlarmDisplayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALARM_HOUR", intent.getIntExtra("ALARM_HOUR", 0))
            putExtra("ALARM_MINUTE", intent.getIntExtra("ALARM_MINUTE", 0))
        }

        context.startActivity(alarmIntent)
    }
}