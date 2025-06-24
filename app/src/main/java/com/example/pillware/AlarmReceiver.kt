package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import java.util.Calendar
import com.google.firebase.firestore.FirebaseFirestore

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nombre = intent.getStringExtra("nombre") ?: "Medicamento"
        val hora = intent.getStringExtra("hora") ?: "Desconocida"
        val medicamentoId = intent.getStringExtra("medicamentoId") ?: "Desconocido"
        val userId = intent.getStringExtra("userId") ?: "Desconocido"
        val alarmType = intent.getStringExtra("alarmType") ?: "fixed_alarm" // Valor por defecto
        Log.d("AlarmReceiver", "Alarma recibida: $nombre a las $hora, Tipo: $alarmType")

        // Mostrar la notificación (esto ya lo tienes)
        showNotification(context, nombre, "Toma tu medicamento a las $hora")

        // Marcar como tomado en Firestore
        marcarMedicamentoComoTomado(medicamentoId, userId)

        // Reprogramar la alarma
        reprogramarAlarma(context, nombre, hora, medicamentoId, userId, alarmType)
    }

    private fun marcarMedicamentoComoTomado(medicamentoId: String, userId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Perfil").document(userId)
            .collection("Medicamentos").document(medicamentoId)
            .update("isTaken", true)
            .addOnSuccessListener {
                Log.d("AlarmReceiver", "Medicamento $medicamentoId marcado como tomado en Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("AlarmReceiver", "Error al marcar como tomado: ${e.message}")
            }
    }

    private fun reprogramarAlarma(context: Context, nombreMedicamento: String, horaOriginal: String, medicamentoId: String, userId: String, alarmType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", alarmType) // Pasamos el tipo de alarma
        }

        val requestCode = (medicamentoId + horaOriginal).hashCode()

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val calendar = Calendar.getInstance()

        if (alarmType == "dynamic_alarm") {
            // Recalcular la hora basada en la hora *actual* y la frecuencia (ejemplo: cada 8 horas)
            calendar.add(Calendar.HOUR_OF_DAY, 8) // Ejemplo: cada 8 horas
        } else {
            // Programar para la *siguiente* ocurrencia de la hora original
            val parts = horaOriginal.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            calendar.apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w("AlarmReceiver", "No se puede programar alarma exacta.")
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d("AlarmReceiver", "Alarma reprogramada para ${calendar.time}")
    }

    private fun showNotification(context: Context, title: String, message: String) {
        // (Tu código para mostrar la notificación)
    }
}