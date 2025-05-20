package com.example.pillware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AgregarMedicamentoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agregar_medicamento)

        val editNombre = findViewById<EditText>(R.id.editNombre)
        val editHora = findViewById<EditText>(R.id.editHora)
        val editDosis = findViewById<EditText>(R.id.editDosis)
        val editDetalles = findViewById<EditText>(R.id.editDetalles)
        val btnGuardar = findViewById<Button>(R.id.btnGuardar)

        btnGuardar.setOnClickListener {
            val nombre = editNombre.text.toString()
            val hora = editHora.text.toString()
            val dosis = editDosis.text.toString()
            val detalles = editDetalles.text.toString()

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null && nombre.isNotEmpty() && hora.isNotEmpty()) {
                val medicamento = hashMapOf(
                    "Nombre" to nombre,
                    "Hora" to hora,
                    "Dosis" to dosis,
                    "Detalles" to detalles
                )

                val db = FirebaseFirestore.getInstance()
                db.collection("Perfil").document(uid)
                    .collection("Medicamentos")
                    .add(medicamento)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Medicamento guardado", Toast.LENGTH_SHORT).show()

                        // Programar alarma
                        val intent = Intent(this, AlarmReceiver::class.java).apply {
                            putExtra("nombre", nombre)
                            putExtra("hora", hora)
                        }

                        val pendingIntent = PendingIntent.getBroadcast(
                            this,
                            nombre.hashCode(), // ID único por medicamento
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val parts = hora.split(":")
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                            set(Calendar.MINUTE, parts[1].toInt())
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)

                            if (before(Calendar.getInstance())) {
                                add(Calendar.DAY_OF_YEAR, 1) // Si ya pasó la hora, programa para mañana
                            }
                        }

                        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )

                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Llena al menos nombre y hora", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
