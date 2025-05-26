package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val nombreMedicamento = intent.getStringExtra("nombre")
        val hora = intent.getStringExtra("hora")
        val medicamentoId = intent.getStringExtra("medicamentoId")
        val userId = intent.getStringExtra("userId")
        val alarmType = intent.getStringExtra("alarmType") // Obtener el tipo de alarma

        Log.d(TAG, "Alarm received for: $nombreMedicamento at $hora, ID: $medicamentoId, UserID: $userId, Type: $alarmType")

        // Iniciar el AlarmProcessingService para manejar la lógica
        val serviceIntent = Intent(context, AlarmProcessingService::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", alarmType) // Pasar el tipo de alarma al servicio
        }
        // Para Android O (API 26) y superior, usar startForegroundService()
        // si el servicio va a realizar trabajo que afecta al usuario inmediatamente
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}