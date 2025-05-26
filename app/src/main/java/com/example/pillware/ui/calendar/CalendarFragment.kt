package com.example.pillware

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CalendarFragment : Fragment() {

    private lateinit var btnAddCita: Button
    private lateinit var tvListaCitas: TextView
    private lateinit var layoutAgregarCita: ConstraintLayout // Cambiado a ConstraintLayout
    private lateinit var etDescripcion: EditText
    private lateinit var etFechaHora: EditText
    private lateinit var btnGuardarCita: Button
    private lateinit var btnCancelarCita: Button
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        btnAddCita = view.findViewById(R.id.btnAddCitaFragment)
        tvListaCitas = view.findViewById(R.id.tvListaCitasFragment)
        layoutAgregarCita = view.findViewById(R.id.layoutAgregarCita)
        etDescripcion = view.findViewById(R.id.etDescripcionFragment)
        etFechaHora = view.findViewById(R.id.etFechaHoraFragment)
        btnGuardarCita = view.findViewById(R.id.btnGuardarCitaFragment)
        btnCancelarCita = view.findViewById(R.id.btnCancelarCitaFragment)
        sharedPreferences = requireActivity().getSharedPreferences("citas_prefs", Context.MODE_PRIVATE)

        btnAddCita.setOnClickListener {
            layoutAgregarCita.visibility = View.VISIBLE
            btnAddCita.visibility = View.GONE
            // No es necesario ocultar la lista, ya que el layout de agregar está debajo
        }

        btnCancelarCita.setOnClickListener {
            layoutAgregarCita.visibility = View.GONE
            btnAddCita.visibility = View.VISIBLE
            limpiarCampos()
        }

        btnGuardarCita.setOnClickListener {
            guardarCita()
        }

        mostrarCitasInicial()

        return view
    }

    override fun onResume() {
        super.onResume()
        mostrarCitasInicial() // Actualizar la lista cuando el fragmento se vuelve visible
    }

    private fun mostrarCitasInicial() {
        val citasJson = sharedPreferences.getString("citas", null)
        if (citasJson != null) {
            val type = object : TypeToken<List<Cita>>() {}.type
            val listaCitas = gson.fromJson<List<Cita>>(citasJson, type)
            if (listaCitas.isNotEmpty()) {
                val textoCitas = listaCitas.joinToString("\n\n") { "${it.descripcion}\n${it.fechaHora}" }
                tvListaCitas.text = textoCitas
            } else {
                tvListaCitas.text = "No hay citas programadas."
            }
        } else {
            tvListaCitas.text = "No hay citas programadas."
        }
    }

    private fun guardarCita() {
        val descripcion = etDescripcion.text.toString()
        val fechaHora = etFechaHora.text.toString()

        if (descripcion.isNotEmpty() && fechaHora.isNotEmpty()) {
            val nuevaCita = Cita(descripcion, fechaHora)
            guardarNuevaCita(nuevaCita)
            layoutAgregarCita.visibility = View.GONE
            btnAddCita.visibility = View.VISIBLE
            mostrarCitasInicial()
            limpiarCampos()
        } else {
            Toast.makeText(requireContext(), "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarNuevaCita(cita: Cita) {
        val citasJson = sharedPreferences.getString("citas", null)
        val editor = sharedPreferences.edit()
        val listaCitas = mutableListOf<Cita>()

        if (citasJson != null) {
            val type = object : TypeToken<List<Cita>>() {}.type
            listaCitas.addAll(gson.fromJson(citasJson, type))
        }

        listaCitas.add(cita)
        val nuevoCitasJson = gson.toJson(listaCitas)
        editor.putString("citas", nuevoCitasJson)
        editor.apply()

        Toast.makeText(requireContext(), "Cita guardada.", Toast.LENGTH_SHORT).show()
    }

    private fun limpiarCampos() {
        etDescripcion.text.clear()
        etFechaHora.text.clear()
    }
}

data class cita (val descripcion: String, val fechaHora: String)