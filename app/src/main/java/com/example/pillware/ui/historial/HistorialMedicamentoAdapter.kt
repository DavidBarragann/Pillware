package com.example.pillware.ui.historial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.R
import com.google.android.material.button.MaterialButton


data class Medicamento(
    val id: String = "",
    val nombre: String = "",
    val horario: List<String> = emptyList(), // Asume que "Horas" es una lista de strings
    val dosis: String = "",
    val detalles: String = "",
    val isTaken: Boolean = false // Si es relevante para el historial, aunque la imagen no lo muestra
)

class HistorialMedicamentoAdapter(
    private val medicamentos: MutableList<com.example.pillware.ui.historial.Medicamento>
) : RecyclerView.Adapter<HistorialMedicamentoAdapter.MedicamentoViewHolder>() {

    private var expandedPosition: Int = -1

    inner class MedicamentoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerButton: MaterialButton = itemView.findViewById(R.id.medicamento_header_button)
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.medicamento_details_layout)
        val textDosisHorario: TextView = itemView.findViewById(R.id.text_dosis_horario)
        val textHorario: TextView = itemView.findViewById(R.id.text_horario)
        val textIndicaciones: TextView = itemView.findViewById(R.id.text_indicaciones)

        init {
            headerButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleDetails(position)
                }
            }
        }

        private fun toggleDetails(position: Int) {
            val previousExpandedPosition = expandedPosition
            if (position == expandedPosition) {
                expandedPosition = -1
                notifyItemChanged(position)
            } else {
                // Si otro elemento está expandido, lo colapsamos primero
                if (previousExpandedPosition != -1) {
                    expandedPosition = -1 // Establece -1 para que la vista anterior se contraiga correctamente
                    notifyItemChanged(previousExpandedPosition)
                }
                // Luego expandimos el nuevo elemento
                expandedPosition = position
                notifyItemChanged(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicamentoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_historial_medicamento, parent, false)
        return MedicamentoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicamentoViewHolder, position: Int) {
        val medicamento = medicamentos[position]
        holder.headerButton.text = medicamento.nombre

        // Determine if this item should be expanded
        val isExpanded = position == expandedPosition
        holder.detailsLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.headerButton.setIconResource(if (isExpanded) R.drawable.baseline_keyboard_arrow_up_24 else R.drawable.baseline_keyboard_arrow_down_24)


        // Populate details
        holder.textDosisHorario.text = medicamento.dosis
        holder.textHorario.text = medicamento.horario.joinToString(" - ") // Une la lista de horarios
        holder.textIndicaciones.text = medicamento.detalles

    }

    override fun getItemCount(): Int = medicamentos.size

    // Actualiza la lista de medicamentos y notifica al adaptador
    fun updateMedicamentos(newList: MutableList<com.example.pillware.ui.historial.Medicamento>) {
        medicamentos.clear()
        medicamentos.addAll(newList)
        notifyDataSetChanged()
    }
}