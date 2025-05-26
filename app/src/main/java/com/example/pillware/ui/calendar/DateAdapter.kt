package com.example.pillware.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateAdapter(private val onDateClickListener: (DateItem, Int) -> Unit) :
    ListAdapter<DateItem, DateAdapter.DateViewHolder>(DateDiffCallback()) {

    private var selectedPosition = RecyclerView.NO_POSITION

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_date, parent, false)
        return DateViewHolder(view)
    }

    // Dentro de tu DateAdapter
    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        val dateItem = getItem(position)
        holder.bind(dateItem)

        val isSelected = position == selectedPosition
        holder.itemView.isSelected = isSelected // Esto activará el estado 'selected' en el selector de fondo

        val textColor = if (isSelected) R.color.white else R.color.text
        val dayTextColor = if (isSelected) R.color.white else R.color.text

        holder.dateNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, textColor))
        holder.dateDay.setTextColor(ContextCompat.getColor(holder.itemView.context, dayTextColor))

        // Opcional: Resaltar el día de hoy si no está seleccionado
        if (dateItem.isToday && !isSelected) {
            holder.dateNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.azulSP))
            holder.dateDay.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.azulSP))
        }

        holder.itemView.setOnClickListener {
            onDateClickListener.invoke(dateItem, holder.adapterPosition)
        }
    }

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateNumber: TextView = itemView.findViewById(R.id.date_number)
        val dateDay: TextView = itemView.findViewById(R.id.date_day)

        fun bind(dateItem: DateItem) {
            val dayFormat = SimpleDateFormat("EEE", Locale("es", "ES")) // Day of week (e.g., LUN, MAR)
            val numberFormat = SimpleDateFormat("dd", Locale.getDefault()) // Day number

            dateNumber.text = numberFormat.format(dateItem.date)
            dateDay.text = dayFormat.format(dateItem.date).uppercase(Locale.getDefault())
        }
    }

    private class DateDiffCallback : DiffUtil.ItemCallback<DateItem>() {
        override fun areItemsTheSame(oldItem: DateItem, newItem: DateItem): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: DateItem, newItem: DateItem): Boolean {
            return oldItem == newItem
        }
    }
}