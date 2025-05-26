package com.example.pillware

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class agregar_cita : AppCompatActivity() {

    private lateinit var etNombreCita: EditText
    private lateinit var etFechaCita: EditText
    private lateinit var etIndicacionesCita: EditText
    private lateinit var btnGuardarCita: Button
    private lateinit var btnAddHoraCita: Button
    private lateinit var containerHorariosCita: LinearLayout
    private lateinit var backBtnAgregarCita: ImageView

    private val listaHorarios = mutableListOf<String>() // Para almacenar los horarios seleccionados

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agregar_cita)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Inicializar vistas
        etNombreCita = findViewById(R.id.etNombreCita)
        etFechaCita = findViewById(R.id.etFechaCita)
        etIndicacionesCita = findViewById(R.id.etIndicacionesCita)
        btnGuardarCita = findViewById(R.id.btnGuardarCita)
        btnAddHoraCita = findViewById(R.id.btnAddHoraCita)
        containerHorariosCita = findViewById(R.id.containerHorariosCita)
        backBtnAgregarCita = findViewById(R.id.backBtnAgregarCita)

        // Listeners
        backBtnAgregarCita.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnAddHoraCita.setOnClickListener {
            mostrarTimePickerDialog()
        }

        etFechaCita.setOnClickListener {
            mostrarDatePickerDialog()
        }

        btnGuardarCita.setOnClickListener {
            tryGuardarCita()
        }
    }

    private fun tryGuardarCita() {
        val nombreCita = etNombreCita.text.toString().trim()
        val fechaCita = etFechaCita.text.toString().trim()
        val indicacionesCita = etIndicacionesCita.text.toString().trim()

        val uid = auth.currentUser?.uid

        if (uid != null && nombreCita.isNotEmpty() && listaHorarios.isNotEmpty() && fechaCita.isNotEmpty()) {
            guardarCitaEnFirestore(uid, nombreCita, listaHorarios, fechaCita, indicacionesCita)
        } else if (uid == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Por favor, completa todos los campos y agrega al menos una hora.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarCitaEnFirestore(
        uid: String,
        nombreCita: String,
        horarios: List<String>,
        fecha: String,
        indicaciones: String
    ) {
        val citaData = hashMapOf(
            "nombreCita" to nombreCita,
            "horarios" to horarios, // Guardamos la lista de horarios
            "fecha" to fecha,       // Guardamos la fecha
            "indicaciones" to indicaciones
        )

        firestore.collection("Perfil").document(uid)
            .collection("Citas")
            .add(citaData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Cita guardada con ID: ${documentReference.id}", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar cita: ${e.message}", Toast.LENGTH_LONG).show()
                // Log.e("AgregarCita", "Error saving cita", e)
            }
    }

    private fun mostrarTimePickerDialog() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            if (!listaHorarios.contains(formattedTime)) {
                listaHorarios.add(formattedTime)
                listaHorarios.sort() // Mantener los horarios ordenados
                actualizarVistaHorarios()
            } else {
                Toast.makeText(this, "Esta hora ya ha sido agregada.", Toast.LENGTH_SHORT).show()
            }
        }, hour, minute, true) // 'true' para formato de 24 horas

        timePickerDialog.show()
    }

    private fun actualizarVistaHorarios() {
        containerHorariosCita.removeAllViews()

        // Usamos las mismas dimensiones y estilos que en AgregarMedicamentoActivity
        val spacingSmall = resources.getDimensionPixelSize(R.dimen.spacing_small)
        val spacingMedium = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val paddingHorizontal = resources.getDimensionPixelSize(R.dimen.padding_horizontal)
        val paddingVertical = resources.getDimensionPixelSize(R.dimen.padding_vertical)
        val chipTextSize = resources.getDimension(R.dimen.chip_text_size)

        for (hora in listaHorarios) {
            val horaView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = spacingSmall
                    rightMargin = spacingMedium
                }
                text = hora
                setTextSize(TypedValue.COMPLEX_UNIT_PX, chipTextSize)
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setBackgroundResource(R.drawable.chip_background) // Asegúrate de tener este drawable
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                gravity = Gravity.CENTER
                setOnClickListener { view ->
                    listaHorarios.remove(hora)
                    actualizarVistaHorarios()
                    Toast.makeText(context, "Hora $hora eliminada.", Toast.LENGTH_SHORT).show()
                }
            }
            containerHorariosCita.addView(horaView)
        }
    }

    private fun mostrarDatePickerDialog() {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedCalendar = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }
            // Formatear la fecha como YYYY-MM-DD
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            etFechaCita.setText(dateFormat.format(selectedCalendar.time))
        }, year, month, day)

        datePickerDialog.show()
    }
}