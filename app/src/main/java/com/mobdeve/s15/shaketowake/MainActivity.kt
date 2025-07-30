package com.mobdeve.s15.shaketowake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var timePicker: TimePicker
    private lateinit var alarmsRecyclerView: RecyclerView
    private val alarmTimes = mutableListOf<String>()
    private lateinit var adapter: AlarmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)
        AlarmReceiver.preloadSoundPool(this)
        timePicker = findViewById(R.id.timePicker)
        alarmsRecyclerView = findViewById(R.id.alarmsRecyclerView)

        adapter = AlarmAdapter(
            onAlarmClick = { timeString ->
                val parts = timeString.split(":")
                timePicker.hour = parts[0].toInt()
                timePicker.minute = parts[1].toInt()
            },
            onDeleteClick = { timeString ->
                alarmTimes.remove(timeString)
                adapter.submitList(alarmTimes.toList())
                saveAlarms()
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
            }
        )

        alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmsRecyclerView.adapter = adapter

        loadAlarms()
        adapter.submitList(alarmTimes.toList())

        findViewById<Button>(R.id.setAlarmButton).setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute

            val newTime = String.format("%02d:%02d", hour, minute)
            if (!alarmTimes.contains(newTime)) {
                alarmTimes.add(0, newTime)
                adapter.submitList(alarmTimes.toList())
                saveAlarms()

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
                    putExtra("ALARM_HOUR", hour)
                    putExtra("ALARM_MINUTE", minute)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    this, newTime.hashCode(), alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }

            val intent = Intent(this, AlarmDisplayActivity::class.java).apply {
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
            }
            startActivity(intent)
        }
    }

    private fun saveAlarms() {
        val sharedPrefs = getSharedPreferences("alarms", MODE_PRIVATE)
        sharedPrefs.edit().putStringSet("alarm_times", alarmTimes.toSet()).apply()
    }

    private fun loadAlarms() {
        val sharedPrefs = getSharedPreferences("alarms", MODE_PRIVATE)
        val savedSet = sharedPrefs.getStringSet("alarm_times", emptySet())
        alarmTimes.clear()
        alarmTimes.addAll(savedSet ?: emptySet())
    }
}
