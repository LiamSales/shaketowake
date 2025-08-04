package com.mobdeve.s15.shaketowake

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.media.SoundPool
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager

class AlarmDisplayActivity : AppCompatActivity() {
    private lateinit var currentTimeTextView: TextView
    private lateinit var alarmTimeTextView: TextView
    private lateinit var timeHandler: Handler
    private lateinit var timeRunnable: Runnable
    private var alarmHour = 0
    private var alarmMinute = 0
    private var soundPool: SoundPool? = null
    private var alarmSoundId: Int = 0


    private lateinit var sensorManager: SensorManager
    private var lastShakeTime: Long = 0L
    private val shakeThreshold = 12f
    private val shakeCooldown = 1000 // milliseconds
    private var wakeLock: PowerManager.WakeLock? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                val currentTime = System.currentTimeMillis()

                if (acceleration > shakeThreshold && currentTime - lastShakeTime > shakeCooldown) {
                    lastShakeTime = currentTime
                    stopAlarmAndGoHome()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm_display)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Get alarm time from intent
        alarmHour = intent.getIntExtra("ALARM_HOUR", 0)
        alarmMinute = intent.getIntExtra("ALARM_MINUTE", 0)

        currentTimeTextView = findViewById(R.id.currentTimeTextView)
        alarmTimeTextView = findViewById(R.id.alarmTimeTextView)


        // Set black background
        window.decorView.setBackgroundColor(ContextCompat.getColor(this, R.color.black))

        // Display alarm time
        val alarmTime = formatTime(alarmHour, alarmMinute)
        alarmTimeTextView.text = "Alarm: $alarmTime"
        alarmTimeTextView.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Set up current time updater
        timeHandler = Handler(Looper.getMainLooper())
        timeRunnable = object : Runnable {
            override fun run() {
                updateCurrentTime()
                timeHandler.postDelayed(this, 1000)
            }
        }


        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ShakeToWake::AlarmWakeLock"
        )
        wakeLock?.isHeld()?.let {
            if (!it == true) {
                wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        }
    }

    private fun updateCurrentTime() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentSecond = calendar.get(Calendar.SECOND)

        val currentTime = formatTime(currentHour, currentMinute, currentSecond)
        currentTimeTextView.text = "Current: $currentTime"
        currentTimeTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun formatTime(hour: Int, minute: Int, second: Int = -1): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (second != -1) set(Calendar.SECOND, second)
        }
        return if (second != -1) {
            SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(calendar.time)
        } else {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        timeHandler.post(timeRunnable)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
        timeHandler.removeCallbacks(timeRunnable)
    }


    private fun stopAlarmAndGoHome() {
        soundPool?.apply {
            pause(alarmSoundId)
            setVolume(alarmSoundId, 0f, 0f)
        }
        soundPool?.stop(alarmSoundId)
        soundPool?.release()
        soundPool = null

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        // Stop the foreground service
        AlarmForegroundService.stopService(this)

        // Redirect to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}