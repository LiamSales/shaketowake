package com.mobdeve.s15.shaketowake

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import androidx.core.app.NotificationCompat

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
            // Play alarm sound
            if (isSoundPoolPreloaded) {
                soundPool?.play(alarmSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            }

            // Create full-screen intent
            val fullScreenIntent = Intent(context, AlarmDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra("ALARM_HOUR", intent.getIntExtra("ALARM_HOUR", 0))
                putExtra("ALARM_MINUTE", intent.getIntExtra("ALARM_MINUTE", 0))
            }

            // Create pending intent
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager

                // Create notification channel if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        "ALARM_CHANNEL_ID",
                        "Alarms",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                // Build the notification
                val notification = NotificationCompat.Builder(context, "ALARM_CHANNEL_ID")
                    .setContentTitle("Alarm")
                    .setContentText("Your alarm is going off!")
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                // Show the notification
                notificationManager.notify(123, notification)
            }

            // Start the activity (works for all versions)
            context.startActivity(fullScreenIntent)
        }
    }
