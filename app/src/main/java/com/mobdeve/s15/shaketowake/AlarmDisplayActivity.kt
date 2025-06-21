package com.mobdeve.s15.shaketowake


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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_display)

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
        timeHandler.post(timeRunnable)
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacks(timeRunnable)
    }
}