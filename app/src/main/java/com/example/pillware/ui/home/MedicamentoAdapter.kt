package com.example.pillware.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.R
import android.widget.ImageView
import androidx.core.content.ContextCompat // Para el color del check icon
import android.graphics.Color // Para el color de texto del medicamento tomado

data class Medicamento(
    val id: String = "", // ¡NUEVO! Para almacenar el ID del documento de Firestore
    val nombre: String,
    val horario: List<String>,
    val dosis: String,
    val detalles: String? = null,
    val isTaken: Boolean = false // ¡NUEVO! Para el estado de si se ha tomado
)

// Define un listener para los eventos de clic
class MedicamentoAdapter(
    private val listaMedicamentos: List<Medicamento>,
    private val onCheckClickListener: (Medicamento, Int) -> Unit, // (medicamento, position)
    private val onDeleteClickListener: (Medicamento, Int) -> Unit // (medicamento, position)
) : RecyclerView.Adapter<MedicamentoAdapter.MedicamentoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicamentoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_view_medicamentos, parent, false)
        return MedicamentoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicamentoViewHolder, position: Int) {
        val medicamento = listaMedicamentos[position]
        holder.nombreTextView.text = medicamento.nombre

        if (medicamento.horario.isNotEmpty()) {
            holder.horarioTextView.text = medicamento.horario.joinToString(", ")
        } else {
            holder.horarioTextView.text = "Sin horarios programados"
        }

        holder.dosisTextView.text = medicamento.dosis

        // Lógica para el check_icon y el texto del medicamento
        if (medicamento.isTaken) {
            holder.checkIcon.setImageResource(R.drawable.baseline_check_circle_24) // Icono de check lleno
            holder.checkIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.verdecheck_checked)) // Un color más oscuro o diferente para indicar tomado
            holder.nombreTextView.setTextColor(Color.GRAY) // Cambiar color del texto del nombre
            holder.horarioTextView.setTextColor(Color.GRAY) // Cambiar color del texto del horario
            holder.dosisTextView.setTextColor(Color.GRAY) // Cambiar color del texto de la dosis
            // Opcional: tachar el texto si es una opción deseada
            // holder.nombreTextView.paintFlags = holder.nombreTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.checkIcon.setImageResource(R.drawable.baseline_check_24) // Icono de check vacío
            holder.checkIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.verdecheck)) // Color original
            holder.nombreTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.azulSP)) // Color original
            holder.horarioTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black)) // Color original
            holder.dosisTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.azulSP)) // Color original
            // Opcional: remover tachado
            // holder.nombreTextView.paintFlags = holder.nombreTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Listener para el check_icon (marcar como tomado)
        holder.checkIcon.setOnClickListener {
            onCheckClickListener.invoke(medicamento, position)
        }

        // Listener para el button_eliminar
        holder.eliminarButton.setOnClickListener {
            onDeleteClickListener.invoke(medicamento, position)
        }
    }

    override fun getItemCount(): Int {
        return listaMedicamentos.size
    }

    inner class MedicamentoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombreTextView: TextView = view.findViewById(R.id.nombre_medicamento)
        val horarioTextView: TextView = view.findViewById(R.id.horario_medicamento)
        val dosisTextView: TextView = view.findViewById(R.id.button_capsula)
        val checkIcon: ImageView = view.findViewById(R.id.check_icon)
        val eliminarButton: ImageView = view.findViewById(R.id.button_eliminar) // Referencia al botón eliminar
    }
}