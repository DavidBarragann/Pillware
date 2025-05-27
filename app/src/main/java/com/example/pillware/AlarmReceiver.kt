package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nombreMedicamento = intent.getStringExtra("nombre")
        val hora = intent.getStringExtra("hora")
        val medicamentoId = intent.getStringExtra("medicamentoId")
        val userId = intent.getStringExtra("userId")
        val alarmType = intent.getStringExtra("alarmType") // "main_alarm", "follow_up_alarm", "mark_taken"

        Log.d("AlarmReceiver", "onReceive: Alarm Type: $alarmType, Med: $nombreMedicamento, ID: $medicamentoId, User: $userId")

        if (medicamentoId == null || userId == null) {
            Log.e("AlarmReceiver", "Medicamento ID o User ID nulo. No se puede procesar la alarma. Deteniendo Broadcast.")
            return
        }

        if (alarmType == "mark_taken") {
            val notificationId = intent.getIntExtra("notificationId", -1)
            if (notificationId != -1) {
                // Llama a la función para marcar como tomado (que ya está en el servicio).
                // Pero como estamos en el receiver, no podemos llamar directamente a un método del servicio.
                // La mejor opción es que el AlarmProcessingService se encargue de "mark_taken" si ya está iniciado,
                // o iniciar un *nuevo* servicio para esta acción si el anterior ya se detuvo.
                // Por eficiencia, el AlarmReceiver debería iniciar el servicio con la acción "mark_taken".
                val serviceIntent = Intent(context, AlarmProcessingService::class.java).apply {
                    putExtra("nombre", nombreMedicamento)
                    putExtra("hora", hora)
                    putExtra("medicamentoId", medicamentoId)
                    putExtra("userId", userId)
                    putExtra("alarmType", "mark_taken")
                    putExtra("notificationId", notificationId) // Pasa el ID de la notificación para cancelarla
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                Log.e("AlarmReceiver", "Notification ID missing for mark_taken action.")
            }
            return // Detener el procesamiento aquí para la acción "mark_taken"
        }


        // Para todas las demás alarmas (principal y de seguimiento), iniciar el servicio
        val serviceIntent = Intent(context, AlarmProcessingService::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", alarmType) // Pasa el tipo de alarma al servicio
        }

        // Iniciar el servicio en primer plano para asegurar que se ejecute
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}