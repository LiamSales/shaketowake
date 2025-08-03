package com.mobdeve.s15.shaketowake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar

data class AlarmData(
    val id: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val timeString: String = "",
    val isActive: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity() {
    private lateinit var timePicker: TimePicker
    private lateinit var alarmsRecyclerView: RecyclerView
    private val alarmTimes = mutableListOf<String>()
    private lateinit var adapter: AlarmAdapter
    private lateinit var db: FirebaseFirestore
    private val userId = "user_${System.currentTimeMillis()}" // Simple user ID - replace with proper auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        // Initialize Firebase
        db = Firebase.firestore

        // Check and request exact alarm permission
        checkExactAlarmPermission()

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
                deleteAlarmFromFirebase(timeString)
            }
        )

        alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmsRecyclerView.adapter = adapter

        // Load alarms from Firebase instead of SharedPreferences
        loadAlarmsFromFirebase()

        findViewById<Button>(R.id.setAlarmButton).setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val newTime = String.format("%02d:%02d", hour, minute)

            if (!alarmTimes.contains(newTime)) {
                // Check permission before setting alarm
                if (canScheduleExactAlarms()) {
                    // Save to Firebase
                    saveAlarmToFirebase(hour, minute, newTime)

                    // Set system alarm
                    setSystemAlarm(hour, minute, newTime)
                } else {
                    Toast.makeText(this, "Please allow exact alarm permission", Toast.LENGTH_LONG).show()
                    requestExactAlarmPermission()
                }
            }

            val intent = Intent(this, AlarmDisplayActivity::class.java).apply {
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
            }
            startActivity(intent)
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission()
            }
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No permission required for older versions
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            startActivity(intent)
        }
    }

    private fun saveAlarmToFirebase(hour: Int, minute: Int, timeString: String) {
        val alarmData = AlarmData(
            id = timeString.hashCode().toString(),
            hour = hour,
            minute = minute,
            timeString = timeString,
            isActive = true,
            timestamp = System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("alarms")
            .document(alarmData.id)
            .set(alarmData)
            .addOnSuccessListener {
                Log.d("Firebase", "Alarm saved successfully")
                // Update local list and UI
                alarmTimes.add(0, timeString)
                adapter.submitList(alarmTimes.toList())
                Toast.makeText(this, "Alarm set successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("Firebase", "Error saving alarm", e)
                Toast.makeText(this, "Failed to save alarm", Toast.LENGTH_SHORT).show()
                // Fallback to local storage
                saveAlarmsToLocal()
            }
    }

    private fun loadAlarmsFromFirebase() {
        db.collection("users")
            .document(userId)
            .collection("alarms")
            .whereEqualTo("isActive", true)
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Firebase", "Listen failed.", e)
                    // Fallback to local storage
                    loadAlarmsFromLocal()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    alarmTimes.clear()
                    for (document in snapshot.documents) {
                        val alarmData = document.toObject(AlarmData::class.java)
                        alarmData?.let {
                            alarmTimes.add(it.timeString)
                        }
                    }
                    adapter.submitList(alarmTimes.toList())
                } else {
                    Log.d("Firebase", "Current data: null")
                }
            }
    }

    private fun deleteAlarmFromFirebase(timeString: String) {
        val alarmId = timeString.hashCode().toString()

        db.collection("users")
            .document(userId)
            .collection("alarms")
            .document(alarmId)
            .update("isActive", false)
            .addOnSuccessListener {
                Log.d("Firebase", "Alarm deleted successfully")
                // Update local list
                alarmTimes.remove(timeString)
                adapter.submitList(alarmTimes.toList())
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()

                // Cancel system alarm
                cancelSystemAlarm(timeString)
            }
            .addOnFailureListener { e ->
                Log.w("Firebase", "Error deleting alarm", e)
                Toast.makeText(this, "Failed to delete alarm", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setSystemAlarm(hour: Int, minute: Int, timeString: String) {
        if (!canScheduleExactAlarms()) {
            Toast.makeText(this, "Cannot set exact alarms without permission", Toast.LENGTH_LONG).show()
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, timeString.hashCode(), alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        try {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            Log.d("Alarm", "Alarm set for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("Alarm", "Security exception when setting alarm", e)
            Toast.makeText(this, "Permission denied. Please allow exact alarms.", Toast.LENGTH_LONG).show()
            requestExactAlarmPermission()
        }
    }

    private fun cancelSystemAlarm(timeString: String) {
        val alarmIntent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, timeString.hashCode(), alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    // Fallback methods for offline functionality
    private fun loadAlarmsFromLocal() {
        val sharedPrefs = getSharedPreferences("alarms", MODE_PRIVATE)
        val savedSet = sharedPrefs.getStringSet("alarm_times", emptySet())
        alarmTimes.clear()
        alarmTimes.addAll(savedSet ?: emptySet())
        adapter.submitList(alarmTimes.toList())
    }

    private fun saveAlarmsToLocal() {
        val sharedPrefs = getSharedPreferences("alarms", MODE_PRIVATE)
        sharedPrefs.edit().putStringSet("alarm_times", alarmTimes.toSet()).apply()
    }
}