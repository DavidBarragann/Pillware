package com.example.pillware.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.R
import android.widget.ImageView

data class Medicamento(
    val nombre: String,
    val horario: List<String>, // ¡CAMBIO AQUÍ! Ahora es una lista de strings
    val dosis: String,         // 'capsulas' en tu MedicamentoAdapter, pero 'Dosis' en Firestore y AgregaMedicamentoActivity. Usa el nombre de Firestore.
    val detalles: String? = null // Añade este campo si lo guardas en Firestore y quieres mostrarlo
)

class MedicamentoAdapter(private val listaMedicamentos: List<Medicamento>) :
    RecyclerView.Adapter<MedicamentoAdapter.MedicamentoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicamentoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_view_medicamentos, parent, false)
        return MedicamentoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicamentoViewHolder, position: Int) {
        val medicamento = listaMedicamentos[position]
        holder.nombreTextView.text = medicamento.nombre

        // ¡CAMBIO AQUÍ! Unir la lista de horarios en un solo String para mostrarlo
        if (medicamento.horario.isNotEmpty()) {
            holder.horarioTextView.text = medicamento.horario.joinToString(", ") // Une las horas con comas
        } else {
            holder.horarioTextView.text = "Sin horarios programados" // Mensaje si la lista está vacía
        }

        // Asumo que 'dosis' en la data class Medicamento se mapea a 'button_capsula' en tu layout
        holder.dosisTextView.text = medicamento.dosis

        // Si tienes más campos en tu layout y en la data class, actualiza aquí.
        // Por ejemplo:
        // holder.detallesTextView.text = medicamento.detalles ?: "Sin indicaciones"
        // holder.checkIcon.visibility = if (medicamento.someCondition) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int {
        return listaMedicamentos.size
    }

    inner class MedicamentoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombreTextView: TextView = view.findViewById(R.id.nombre_medicamento)
        val horarioTextView: TextView = view.findViewById(R.id.horario_medicamento)
        // Asegúrate de que R.id.button_capsula sea el ID correcto para tu TextView/Button de la dosis
        val dosisTextView: TextView = view.findViewById(R.id.button_capsula)
        val checkIcon: ImageView = view.findViewById(R.id.check_icon)
        // Si añades detalles, también necesitas un TextView para ello
        // val detallesTextView: TextView = view.findViewById(R.id.detalles_medicamento)
    }
}