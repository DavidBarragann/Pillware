package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pillware.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "medicamento_channel"
    private val NOTIFICATION_ID = 101

    override fun onReceive(context: Context, intent: Intent) {
        val nombreMedicamento = intent.getStringExtra("nombre") ?: "Medicamento"
        val hora = intent.getStringExtra("hora") ?: ""
        val medicamentoId = intent.getStringExtra("medicamentoId") ?: "" // Recibir el ID

        createNotificationChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener un icono
            .setContentTitle("¡Es hora de tu medicamento!")
            .setContentText("$nombreMedicamento a las $hora")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // La notificación se cierra al hacer clic

        with(NotificationManagerCompat.from(context)) {
            val notificationId = (medicamentoId + hora).hashCode()
            notify(notificationId, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de Medicamentos"
            val descriptionText = "Canal para las notificaciones de recordatorio de medicamentos."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}