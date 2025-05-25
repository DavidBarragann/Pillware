package com.example.pillware

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class agregar_cita : AppCompatActivity() {

    private lateinit var etDescripcion: EditText
    private lateinit var etFechaHora: EditText
    private lateinit var btnGuardarCita: Button
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private lateinit var backBtnAgregarCita: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agregar_cita)

        etDescripcion = findViewById(R.id.etDescripcion)
        etFechaHora = findViewById(R.id.etFechaHora)
        btnGuardarCita = findViewById(R.id.btnGuardarCita)
        backBtnAgregarCita = findViewById(R.id.backBtnAgregarCita)
        sharedPreferences = getSharedPreferences("citas_prefs", MODE_PRIVATE)

        btnGuardarCita.setOnClickListener {
            guardarCita()
        }

        backBtnAgregarCita.setOnClickListener {
            finish() // Volver a la actividad anterior
        }
    }

    private fun guardarCita() {
        val descripcion = etDescripcion.text.toString()
        val fechaHora = etFechaHora.text.toString()

        if (descripcion.isNotEmpty() && fechaHora.isNotEmpty()) {
            val nuevaCita = Cita(descripcion, fechaHora)
            guardarNuevaCita(nuevaCita)
            finish() // Volver a la pantalla de la lista de citas
        } else {
            Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show()
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

        Toast.makeText(this, "Cita guardada.", Toast.LENGTH_SHORT).show()
    }
}