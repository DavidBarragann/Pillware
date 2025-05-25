package com.example.pillware

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class citas : AppCompatActivity() {

    private lateinit var btnAddCita: Button
    private lateinit var tvListaCitas: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private lateinit var backBtnCitas: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_citas)

        btnAddCita = findViewById(R.id.btnAddCita)
        tvListaCitas = findViewById(R.id.tvListaCitas)
        backBtnCitas = findViewById(R.id.backBtnCitas)
        sharedPreferences = getSharedPreferences("citas_prefs", MODE_PRIVATE)

        btnAddCita.setOnClickListener {
            val intent = Intent(this, agregar_cita::class.java)
            startActivity(intent)
        }

        backBtnCitas.setOnClickListener {
            finish() // Volver a la actividad anterior
        }

        mostrarCitas()
    }

    override fun onResume() {
        super.onResume()
        mostrarCitas() // Actualizar la lista cuando se vuelve a esta actividad
    }

    private fun mostrarCitas() {
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
}

data class Cita(val descripcion: String, val fechaHora: String)