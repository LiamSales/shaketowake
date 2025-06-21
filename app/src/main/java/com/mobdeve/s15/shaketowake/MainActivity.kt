package com.mobdeve.s15.shaketowake

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
class MainActivity : AppCompatActivity() {
    private lateinit var timePicker: TimePicker
    private lateinit var alarmsRecyclerView: RecyclerView
    private val alarmTimes = mutableListOf<String>()
    private lateinit var adapter: AlarmAdapter  // Make adapter a class property

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        timePicker = findViewById(R.id.timePicker)
        alarmsRecyclerView = findViewById(R.id.alarmsRecyclerView)

        // Initialize the adapter properly
        adapter = AlarmAdapter(
            onAlarmClick = { timeString ->
                val parts = timeString.split(":")
                timePicker.hour = parts[0].toInt()
                timePicker.minute = parts[1].toInt()
            },
            onDeleteClick = { timeString ->
                alarmTimes.remove(timeString)
                adapter.submitList(alarmTimes.toList())
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
            }
        )

        alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmsRecyclerView.adapter = adapter

        // Add sample alarms
        alarmTimes.addAll(listOf("08:30", "12:15", "18:45"))
        adapter.submitList(alarmTimes.toList())

        // In your MainActivity.kt
        findViewById<Button>(R.id.setAlarmButton).setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute

            // Launch AlarmDisplayActivity
            val intent = Intent(this, AlarmDisplayActivity::class.java).apply {
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
            }
            startActivity(intent)

            // Optional: Add to history
            val newTime = String.format("%02d:%02d", hour, minute)
            if (!alarmTimes.contains(newTime)) {
                alarmTimes.add(0, newTime)
                adapter.submitList(alarmTimes.toList())
            }
        }
    }
}