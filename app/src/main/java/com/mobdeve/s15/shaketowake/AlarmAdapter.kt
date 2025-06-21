package com.mobdeve.s15.shaketowake

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
class AlarmAdapter(
    private val onAlarmClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<String, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view, onAlarmClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlarmViewHolder(
        itemView: View,
        private val onAlarmClick: (String) -> Unit,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(timeString: String) {
            itemView.findViewById<TextView>(R.id.alarmTimeText).text = timeString
            itemView.setOnClickListener { onAlarmClick(timeString) }
            itemView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                onDeleteClick(timeString)
            }
        }
    }

    class AlarmDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}