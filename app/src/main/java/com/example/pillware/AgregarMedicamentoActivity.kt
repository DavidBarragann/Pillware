package com.example.pillware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.app.TimePickerDialog
import android.view.ViewGroup
import androidx.core.content.ContextCompat // Para compatibilidad de colores

class AgregarMedicamentoActivity : AppCompatActivity() {

    private lateinit var editNombre: EditText
    private lateinit var editDosis: EditText
    private lateinit var editDetalles: EditText
    private lateinit var btnGuardar: Button
    private lateinit var btnAddHora: Button
    private lateinit var containerHorarios: LinearLayout
    private lateinit var btnAtras: ImageView // Agregado para el botón de retroceso

    private val listaHorarios = mutableListOf<String>() // Lista para almacenar las horas (HH:MM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agregar_medicamento)

        editNombre = findViewById(R.id.editNombre)
        editDosis = findViewById(R.id.editDosis)
        editDetalles = findViewById(R.id.editDetalles)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnAddHora = findViewById(R.id.btnAddHora)
        containerHorarios = findViewById(R.id.containerHorarios)
        btnAtras = findViewById(R.id.imageView5) // Inicializar el botón de retroceso

        // Listener para el botón de retroceso
        btnAtras.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Vuelve a la actividad anterior
        }

        // Listener para el botón de agregar hora
        btnAddHora.setOnClickListener {
            mostrarTimePickerDialog()
        }

        btnGuardar.setOnClickListener {
            val nombre = editNombre.text.toString().trim()
            val dosis = editDosis.text.toString().trim()
            val detalles = editDetalles.text.toString().trim()

            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid != null && nombre.isNotEmpty() && listaHorarios.isNotEmpty()) {
                val medicamento = hashMapOf(
                    "Nombre" to nombre,
                    "Horas" to listaHorarios,
                    "Dosis" to dosis,
                    "Detalles" to detalles,
                )

                val db = FirebaseFirestore.getInstance()
                db.collection("Perfil").document(uid)
                    .collection("Medicamentos")
                    .add(medicamento)
                    .addOnSuccessListener { documentReference ->
                        Toast.makeText(this, "Medicamento guardado", Toast.LENGTH_SHORT).show()
                        val medicamentoId = documentReference.id
                        for (hora in listaHorarios) {
                            programarAlarma(nombre, hora, medicamentoId)
                        }

                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Por favor, completa el nombre y agrega al menos una hora.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarTimePickerDialog() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            if (!listaHorarios.contains(formattedTime)) { // Evita horarios duplicados
                listaHorarios.add(formattedTime)
                listaHorarios.sort() // Opcional: ordenar las horas
                actualizarVistaHorarios()
            } else {
                Toast.makeText(this, "Esta hora ya ha sido agregada.", Toast.LENGTH_SHORT).show()
            }
        }, hour, minute, true) // `true` para formato de 24 horas

        timePickerDialog.show()
    }

    // ... dentro de actualizarVistaHorarios()
    private fun actualizarVistaHorarios() {
        containerHorarios.removeAllViews()

        for (hora in listaHorarios) {
            val horaView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
                    rightMargin = resources.getDimensionPixelSize(R.dimen.spacing_medium)
                }
                text = hora
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.chip_text_size))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.chip_text_size))


                setTextColor(ContextCompat.getColor(context, R.color.text))
                setBackgroundResource(R.drawable.chip_background)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.padding_horizontal),
                    resources.getDimensionPixelSize(R.dimen.padding_vertical),
                    resources.getDimensionPixelSize(R.dimen.padding_horizontal),
                    resources.getDimensionPixelSize(R.dimen.padding_vertical)
                )
                gravity = Gravity.CENTER
                setOnClickListener { view ->
                    listaHorarios.remove(hora)
                    actualizarVistaHorarios()
                    Toast.makeText(context, "Hora $hora eliminada.", Toast.LENGTH_SHORT).show()
                }
            }
            containerHorarios.addView(horaView)
        }
    }


    private fun programarAlarma(nombreMedicamento: String, hora: String, medicamentoId: String) {
        val parts = hora.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Si la hora ya pasó hoy, programarla para mañana
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Crear un PendingIntent único para cada alarma
        // El request code debe ser único por alarma, combina medicamentoId y hora
        val requestCode = (medicamentoId + hora).hashCode()

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora) // Pasa la hora específica de la alarma
            putExtra("medicamentoId", medicamentoId) // Pasa el ID del medicamento
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // Programar la alarma
        alarmManager.setExactAndAllowWhileIdle( // setExactAndAllowWhileIdle para mayor precisión
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Toast.makeText(this, "Alarma para $nombreMedicamento a las $hora programada.", Toast.LENGTH_SHORT).show()
    }
}