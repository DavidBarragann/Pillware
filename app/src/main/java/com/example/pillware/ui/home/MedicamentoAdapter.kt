package com.example.pillware.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.R
import android.widget.ImageView

data class Medicamento(val nombre: String, val horario: String, val capsulas: String)

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
        holder.horarioTextView.text = medicamento.horario
        holder.dosisTextView.text = medicamento.capsulas
    }

    override fun getItemCount(): Int {
        return listaMedicamentos.size
    }

    inner class MedicamentoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombreTextView: TextView = view.findViewById(R.id.nombre_medicamento)
        val horarioTextView: TextView = view.findViewById(R.id.horario_medicamento)
        val dosisTextView:TextView=view.findViewById(R.id.button_capsula)
        val checkIcon: ImageView = view.findViewById(R.id.check_icon)
    }
}
