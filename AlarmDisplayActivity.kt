package com.mobdeve.s15.shaketowake

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

class AlarmDisplayActivity : AppCompatActivity() {
    private lateinit var currentTimeTextView: TextView
    private lateinit var alarmTimeTextView: TextView
    private lateinit var timeHandler: Handler
    private lateinit var timeRunnable: Runnable
    private var alarmHour = 0
    private var alarmMinute = 0

    private lateinit var sensorManager: SensorManager
    private var lastShakeTime: Long = 0L
    private val shakeThreshold = 12f
    private val shakeCooldown = 1000 // milliseconds

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
                    finish() // dismiss alarm
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_display)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Get alarm time from intent
        alarmHour = intent.getIntExtra("ALARM_HOUR", 0)
        alarmMinute = intent.getIntExtra("ALARM_MINUTE", 0)

        currentTimeTextView = findViewById(R.id.currentTimeTextView)
        alarmTimeTextView = findViewById(R.id.alarmTimeTextView)
        val backButton = findViewById<Button>(R.id.backButton)

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

        backButton.setOnClickListener {
            finish()
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
}