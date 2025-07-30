package com.mobdeve.s15.shaketowake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmIntent = Intent(context, AlarmDisplayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALARM_HOUR", intent.getIntExtra("ALARM_HOUR", 0))
            putExtra("ALARM_MINUTE", intent.getIntExtra("ALARM_MINUTE", 0))
        }
        context.startActivity(alarmIntent)
    }
}
