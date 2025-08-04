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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import com.google.firebase.firestore.Query

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
    private val userId = "user_1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        // Initialize Firebase
        db = Firebase.firestore
        Log.d("MainActivity", "Using userId: $userId") // ADD THIS

        // Check and request exact alarm permission
        checkExactAlarmPermission()

        AlarmReceiver.preloadSoundPool(this)
        timePicker = findViewById(R.id.timePicker)
        alarmsRecyclerView = findViewById(R.id.alarmsRecyclerView)

        adapter = AlarmAdapter(
            onAlarmClick = { timeString ->
                val parts = timeString.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()

                timePicker.hour = hour
                timePicker.minute = minute

                AlertDialog.Builder(this)
                    .setTitle("Reschedule Alarm")
                    .setMessage("Do you want to reschedule the alarm for $timeString?")
                    .setPositiveButton("Yes") { _, _ ->
                        if (canScheduleExactAlarms()) {
                            saveAlarmToFirebase(hour, minute, timeString)
                            setSystemAlarm(hour, minute, timeString)
                            Toast.makeText(this, "Alarm rescheduled for $timeString", Toast.LENGTH_SHORT).show()
                        } else {
                            requestExactAlarmPermission()
                            Toast.makeText(this, "Permission needed to set exact alarm", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
                    onDeleteClick = { timeString ->
                deleteAlarmFromFirebase(timeString)
            }
        )

        alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmsRecyclerView.adapter = adapter

        Log.d("MainActivity", "Loading alarms from Firebase...")
        // Load alarms from Firebase instead of SharedPreferences
        loadAlarmsFromFirebase()

        findViewById<Button>(R.id.setAlarmButton).setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            val newTime = String.format("%02d:%02d", hour, minute)

            Log.d("MainActivity", "Setting alarm: $newTime") // ADD LOGGING

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
            } else {
                Toast.makeText(this, "Alarm already exists", Toast.LENGTH_SHORT).show() // ADD THIS LINE
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
        Log.d("Firebase", "=== SAVING ALARM DEBUG ===")
        Log.d("Firebase", "User ID: $userId")
        Log.d("Firebase", "Time String: $timeString")
        Log.d("Firebase", "Hour: $hour, Minute: $minute")

        val alarmData = AlarmData(
            id = timeString.hashCode().toString(),
            hour = hour,
            minute = minute,
            timeString = timeString,
            isActive = true,
            timestamp = System.currentTimeMillis()
        )

        Log.d("Firebase", "Alarm Data: $alarmData")
        Log.d("Firebase", "Document path: users/$userId/alarms/${alarmData.id}")

        db.collection("users")
            .document(userId)
            .collection("alarms")
            .document(alarmData.id)
            .set(alarmData)
            .addOnSuccessListener {
                Log.d("Firebase", "✅ SAVE SUCCESS: Alarm saved to Firestore")
                Log.d("Firebase", "Document ID: ${alarmData.id}")
                Toast.makeText(this, "Alarm set successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "❌ SAVE FAILED: ${e.message}", e)
                Toast.makeText(this, "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()

                // Fallback to local storage
                alarmTimes.add(0, timeString)
                adapter.submitList(alarmTimes.toList())
                saveAlarmsToLocal()
            }
    }

    private fun loadAlarmsFromFirebase() {
        Log.d("Firebase", "=== LOADING ALARMS DEBUG ===")
        Log.d("Firebase", "User ID: $userId")
        Log.d("Firebase", "Collection path: users/$userId/alarms")

        db.collection("users")
            .document(userId)
            .collection("alarms")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Firebase", "❌ LISTEN FAILED: ${e.message}", e)
                    Log.d("Firebase", "Falling back to local storage")
                    loadAlarmsFromLocal()
                    return@addSnapshotListener
                }

                Log.d("Firebase", "✅ SNAPSHOT RECEIVED")
                Log.d("Firebase", "Snapshot null: ${snapshot == null}")
                Log.d("Firebase", "Snapshot empty: ${snapshot?.isEmpty}")
                Log.d("Firebase", "Document count: ${snapshot?.documents?.size ?: 0}")

                if (snapshot != null) {
                    // Log all documents found
                    snapshot.documents.forEachIndexed { index, document ->
                        Log.d("Firebase", "Document $index: ${document.id}")
                        Log.d("Firebase", "Document $index data: ${document.data}")

                        val alarmData = document.toObject(AlarmData::class.java)
                        Log.d("Firebase", "Document $index as AlarmData: $alarmData")
                    }

                    if (!snapshot.isEmpty) {
                        Log.d("Firebase", "Processing ${snapshot.documents.size} alarms")
                        alarmTimes.clear()

                        for (document in snapshot.documents) {
                            val alarmData = document.toObject(AlarmData::class.java)
                            alarmData?.let {
                                Log.d("Firebase", "Adding alarm: ${it.timeString}")
                                alarmTimes.add(it.timeString)
                            } ?: Log.w("Firebase", "Failed to convert document to AlarmData: ${document.id}")
                        }

                        Log.d("Firebase", "Final alarm list: $alarmTimes")
                        adapter.submitList(alarmTimes.toList())
                        Log.d("Firebase", "✅ UI updated with ${alarmTimes.size} alarms")
                    } else {
                        Log.d("Firebase", "Snapshot is empty - no alarms found")
                        alarmTimes.clear()
                        adapter.submitList(alarmTimes.toList())
                    }
                } else {
                    Log.w("Firebase", "Snapshot is null")
                }
            }
    }

    private fun deleteAlarmFromFirebase(timeString: String) {
        val alarmId = timeString.hashCode().toString()
        Log.d("Firebase", "Deleting alarm: $timeString (ID: $alarmId)") // ADD LOGGING

        db.collection("users")
            .document(userId)
            .collection("alarms")
            .document(alarmId)
            .delete()
            .addOnSuccessListener {
                Log.d("Firebase", "Alarm deleted successfully: $timeString")
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()

                // Remove from list and UI
                alarmTimes.remove(timeString)
                adapter.submitList(alarmTimes.toList())

                // Cancel system alarm
                cancelSystemAlarm(timeString)
            }
            .addOnFailureListener { e ->
                Log.w("Firebase", "Error deleting alarm: ${e.message}", e)
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

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val alarmIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            timeString.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
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