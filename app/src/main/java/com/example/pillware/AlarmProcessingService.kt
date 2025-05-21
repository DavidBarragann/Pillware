package com.example.pillware

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmProcessingService : Service() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val CHANNEL_ID = "medicamento_channel"
    private val TAG = "AlarmProcessingService"

    override fun onCreate() {
        super.onCreate()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nombreMedicamento = intent?.getStringExtra("nombre")
        val hora = intent?.getStringExtra("hora")
        val medicamentoId = intent?.getStringExtra("medicamentoId")
        val userId = intent?.getStringExtra("userId") // Obtener el ID del usuario

        Log.d(TAG, "Service started for: $nombreMedicamento at $hora, ID: $medicamentoId, UserID: $userId")

        if (nombreMedicamento != null && hora != null && medicamentoId != null && userId != null) {
            // 1. Mostrar la notificación
            showNotification(nombreMedicamento, hora, medicamentoId)

            // 2. Actualizar isTaken a false en Firestore
            updateIsTakenStatus(userId, medicamentoId, nombreMedicamento)

            // 3. Reprogramar la alarma para el día siguiente
            reprogramarAlarma(nombreMedicamento, hora, medicamentoId, userId)
        } else {
            Log.e(TAG, "Intent data missing in service: $nombreMedicamento, $hora, $medicamentoId, $userId")
        }

        // Devolvemos START_NOT_STICKY para que el servicio no se reinicie automáticamente
        // si es terminado por el sistema después de completar su trabajo.
        return START_NOT_STICKY
    }

    private fun showNotification(nombreMedicamento: String, hora: String, medicamentoId: String) {
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener un icono adecuado
            .setContentTitle("¡Es hora de tu medicamento!")
            .setContentText("$nombreMedicamento a las $hora")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // La notificación se cierra al hacer clic
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        // Intento para abrir la app al hacer clic en la notificación
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0, // Request code 0 para el PendingIntent de la actividad
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        notificationBuilder.setContentIntent(pendingIntent)


        with(NotificationManagerCompat.from(this)) {
            val notificationId = (medicamentoId + hora).hashCode()
            // Verificar permiso POST_NOTIFICATIONS para Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are not enabled for this app. Cannot show notification.")
                // Puedes mostrar un Toast o una alerta al usuario aquí si es crítico
                // Toast.makeText(applicationContext, "Activa las notificaciones para recibir recordatorios.", Toast.LENGTH_LONG).show()
                return
            }
            notify(notificationId, notificationBuilder.build())
        }
        Log.d(TAG, "Notification shown for: $nombreMedicamento")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de Medicamentos"
            val descriptionText = "Canal para las notificaciones de recordatorio de medicamentos."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created.")
        }
    }

    private fun updateIsTakenStatus(userId: String, medicamentoId: String, nombreMedicamento: String) {
        val docRef = db.collection("Perfil").document(userId).collection("Medicamentos").document(medicamentoId)

        docRef.update("isTaken", false)
            .addOnSuccessListener {
                Log.d(TAG, "Estado isTaken de $nombreMedicamento actualizado a false en Firestore.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar isTaken para $nombreMedicamento: ${e.message}", e)
            }
    }

    private fun reprogramarAlarma(nombreMedicamento: String, hora: String, medicamentoId: String, userId: String) {
        val parts = hora.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Siempre reprogramar para mañana o el mismo día si la hora no ha pasado
            // Para alarmas diarias, la lógica es añadir un día
            add(Calendar.DAY_OF_YEAR, 1) // Reprogramar para el día siguiente
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val requestCode = (medicamentoId + hora).hashCode()

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId) // Asegurarse de pasar el userId
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

        // Verificar permiso SCHEDULE_EXACT_ALARM antes de programar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot reprogram exact alarm: SCHEDULE_EXACT_ALARM permission not granted.")
            // Considera notificar al usuario que la alarma no se pudo reprogramar.
            return
        }

        alarmManager.setExactAndAllowWhileIdle( // setExactAndAllowWhileIdle para mayor precisión
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Alarma para $nombreMedicamento a las $hora reprogramada para ${calendar.time}.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}