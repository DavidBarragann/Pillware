package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val nombreMedicamento = intent.getStringExtra("nombre")
        val hora = intent.getStringExtra("hora")
        val medicamentoId = intent.getStringExtra("medicamentoId")
        val userId = intent.getStringExtra("userId") // Recibir el ID del usuario

        Log.d(TAG, "Alarm received for: $nombreMedicamento at $hora, ID: $medicamentoId, UserID: $userId")

        // Iniciar el AlarmProcessingService para manejar la lógica de Firestore y la notificación
        val serviceIntent = Intent(context, AlarmProcessingService::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId) // Pasar el ID del usuario al servicio
        }
        context.startService(serviceIntent)
    }
}