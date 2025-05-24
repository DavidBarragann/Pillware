package com.example.pillware

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pillware.data.NotificationItem
import com.example.pillware.data.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks // Importar Tasks para Tasks.forResult
import java.util.*

class AlarmProcessingService : Service() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val CHANNEL_ID = "medicamento_channel"
    private val SERVICE_NOTIFICATION_ID = 101 // ID único para la notificación del servicio en primer plano
    private val TAG = "AlarmProcessingService"

    // Constante para diferenciar el tipo de alarma en el Intent
    private val ALARM_TYPE_MAIN = "main_alarm"
    private val ALARM_TYPE_FOLLOW_UP = "follow_up_alarm"

    override fun onCreate() {
        super.onCreate()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Crea la notificación para el servicio en primer plano
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PillWare en segundo plano")
            .setContentText("Gestionando recordatorios de medicamentos...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener este ícono
            .setPriority(NotificationCompat.PRIORITY_LOW) // Generalmente LOW o MIN para servicios en segundo plano
            .setOngoing(true) // Hace que la notificación sea "ongoing"
            .build()

        // 2. Llama a startForeground()
        startForeground(SERVICE_NOTIFICATION_ID, notification)

        val nombreMedicamento = intent?.getStringExtra("nombre")
        val horaMedicamento = intent?.getStringExtra("hora") // Hora original de la pastilla (HH:MM)
        val medicamentoId = intent?.getStringExtra("medicamentoId")
        val userId = intent?.getStringExtra("userId")
        val alarmType = intent?.getStringExtra("alarmType") ?: ALARM_TYPE_MAIN // Obtener el tipo de alarma

        Log.d(TAG, "Service started for: $nombreMedicamento at $horaMedicamento, ID: $medicamentoId, UserID: $userId, Type: $alarmType")

        if (nombreMedicamento == null || horaMedicamento == null || medicamentoId == null || userId == null) {
            Log.e(TAG, "Intent data missing in service. Stopping self.")
            stopForeground(true) // Elimina la notificación y detiene el servicio en primer plano
            stopSelf() // Detener el servicio si faltan datos cruciales
            return START_NOT_STICKY
        }

        when (alarmType) {
            ALARM_TYPE_MAIN -> {
                showNotification(nombreMedicamento, horaMedicamento, medicamentoId)
                scheduleFollowUpAlarm(nombreMedicamento, horaMedicamento, medicamentoId, userId)

                // **GUARDAR NOTIFICACIÓN DE PROXIMA_TOMA**
                val notificationItem = NotificationItem(
                    id = UUID.randomUUID().toString(), // Genera un ID único
                    tipo = NotificationType.PROXIMA_TOMA,
                    titulo = "¡Es hora de tu medicamento!",
                    mensaje = "$nombreMedicamento a las $horaMedicamento",
                    medicamentoNombre = nombreMedicamento,
                    fechaHora = Date(), // Fecha y hora actual
                    iconoResId = R.drawable.baseline_access_time_24 // Asegúrate de tener este drawable
                )
                saveNotificationToFirestore(userId, notificationItem)
            }
            ALARM_TYPE_FOLLOW_UP -> {
                checkMedicamentoStatusAndSendEmail(nombreMedicamento, horaMedicamento, medicamentoId, userId)
            }
        }

        // Devolvemos START_NOT_STICKY para que el servicio no se reinicie automáticamente
        // si es terminado por el sistema después de completar su trabajo.
        // Las llamadas a stopSelf y stopForeground se manejarán en la lógica de finalización de tareas asíncronas.
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

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        notificationBuilder.setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(this)) {
            val notificationId = (medicamentoId + hora).hashCode()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are not enabled for this app. Cannot show notification.")
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

    private fun saveNotificationToFirestore(userId: String, notification: NotificationItem) {
        val notificationMap = hashMapOf(
            "tipo" to notification.tipo.name, // Guarda el nombre del enum
            "titulo" to notification.titulo,
            "mensaje" to notification.mensaje,
            "medicamentoNombre" to notification.medicamentoNombre,
            "timestamp" to FieldValue.serverTimestamp(), // Usa el timestamp del servidor
            "iconoResId" to notification.iconoResId,
            "leida" to false // Inicialmente no leída
        )

        db.collection("Perfil").document(userId)
            .collection("Notificaciones")
            .add(notificationMap)
            .addOnSuccessListener { documentReference ->
                Log.d("Firestore", "Notificación guardada con ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al guardar notificación: ${e.message}", e)
            }
    }

    private fun scheduleFollowUpAlarm(nombreMedicamento: String, horaOriginal: String, medicamentoId: String, userId: String) {
        val parts = horaOriginal.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Añadir 10 minutos para la alarma de seguimiento
        calendar.add(Calendar.MINUTE, 10)

        // Si la hora de seguimiento ya pasó, programarla para mañana a la misma hora + 10 min
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Usar un requestCode diferente para la alarma de seguimiento (e.g., hashcode + 1)
        val followUpRequestCode = (medicamentoId + horaOriginal).hashCode() + 1

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", horaOriginal) // Sigue siendo la hora original
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", ALARM_TYPE_FOLLOW_UP) // Marcar como alarma de seguimiento
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                this,
                followUpRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                this,
                followUpRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule follow-up alarm: SCHEDULE_EXACT_ALARM permission not granted.")
            // Aquí no se llama a stopSelf ya que esto es una programación, no el final del servicio.
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Follow-up alarm for $nombreMedicamento scheduled for ${calendar.time}.")
    }

    private fun checkMedicamentoStatusAndSendEmail(nombreMedicamento: String, horaMedicamento: String, medicamentoId: String, userId: String) {
        val medicamentoDocRef = db.collection("Perfil").document(userId).collection("Medicamentos").document(medicamentoId)

        medicamentoDocRef.get()
            .addOnSuccessListener { documentSnapshot ->
                val isTaken = documentSnapshot.getBoolean("isTaken") ?: false
                Log.d(TAG, "Checking status for $nombreMedicamento: isTaken = $isTaken")

                val emailSentTask: Task<Void> = if (!isTaken) {
                    // Medicamento NO ha sido tomado, obtener correo del familiar y enviar.
                    db.collection("Perfil").document(userId).get()
                        .addOnSuccessListener { userProfileSnapshot ->
                            val correoFamiliar = userProfileSnapshot.getString("familiar")
                            if (!correoFamiliar.isNullOrEmpty()) {
                                Log.d(TAG, "Sending email to family member: $correoFamiliar")
                                val mensaje = "¡Alerta! Parece que $nombreMedicamento no ha sido registrado como tomado por el usuario a las $horaMedicamento."
                                CorreoHelper.enviarCorreo(applicationContext, mensaje, correoFamiliar)

                                // **GUARDAR NOTIFICACIÓN DE RECORDATORIO (con familiar)**
                                val notificationItem = NotificationItem(
                                    id = UUID.randomUUID().toString(),
                                    tipo = NotificationType.RECORDATORIO,
                                    titulo = "Recordatorio: Toma Pendiente",
                                    mensaje = "Parece que $nombreMedicamento no ha sido registrado como tomado a las $horaMedicamento. Se ha notificado al familiar.",
                                    medicamentoNombre = nombreMedicamento,
                                    fechaHora = Date(),
                                    iconoResId = R.drawable.baseline_warning_24 // Asegúrate de tener este drawable
                                )
                                saveNotificationToFirestore(userId, notificationItem)

                            } else {
                                Log.w(TAG, "No family email found for user $userId to send reminder.")
                                // **GUARDAR NOTIFICACIÓN DE RECORDATORIO (sin familiar)**
                                val notificationItem = NotificationItem(
                                    id = UUID.randomUUID().toString(),
                                    tipo = NotificationType.RECORDATORIO,
                                    titulo = "Recordatorio: Toma Pendiente",
                                    mensaje = "Parece que $nombreMedicamento no ha sido registrado como tomado a las $horaMedicamento.",
                                    medicamentoNombre = nombreMedicamento,
                                    fechaHora = Date(),
                                    iconoResId = R.drawable.baseline_warning_24
                                )
                                saveNotificationToFirestore(userId, notificationItem)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error getting user profile for family email: ${e.message}", e)
                            // Si falla la obtención del perfil, igual guardar el recordatorio básico
                            val notificationItem = NotificationItem(
                                id = UUID.randomUUID().toString(),
                                tipo = NotificationType.RECORDATORIO,
                                titulo = "Recordatorio: Toma Pendiente",
                                mensaje = "Parece que $nombreMedicamento no ha sido registrado como tomado a las $horaMedicamento.",
                                medicamentoNombre = nombreMedicamento,
                                fechaHora = Date(),
                                iconoResId = R.drawable.baseline_warning_24
                            )
                            saveNotificationToFirestore(userId, notificationItem)
                        }
                    // Return a completed task as CorreoHelper.enviarCorreo doesn't return a Task
                    Tasks.forResult(null)
                } else {
                    Log.d(TAG, "$nombreMedicamento was marked as taken. No email sent.")
                    // **OPCIONAL: GUARDAR NOTIFICACIÓN DE TOMA_COMPLETADA desde aquí**
                    // Es más robusto si esto se hace donde el usuario interactúa para marcarlo como tomado.
                    // Pero si NO tienes un lugar donde se genere esta notificación, puedes añadirla aquí.
                    /*
                    val notificationItem = NotificationItem(
                        id = UUID.randomUUID().toString(),
                        tipo = NotificationType.TOMA_COMPLETADA,
                        titulo = "Toma Completada",
                        mensaje = "Has registrado la toma de $nombreMedicamento a las $horaMedicamento.",
                        medicamentoNombre = nombreMedicamento,
                        fechaHora = Date(),
                        iconoResId = R.drawable.baseline_check_circle_24
                    )
                    saveNotificationToFirestore(userId, notificationItem)
                    */
                    Tasks.forResult(null)
                }

                emailSentTask.addOnCompleteListener {
                    // Después de verificar el estado y potencialmente enviar el correo/guardar notificación
                    // (si emailSentTask es una tarea real)
                    updateIsTakenStatusToFalse(userId, medicamentoId, nombreMedicamento)
                        .addOnCompleteListener { updateTask ->
                            reprogramarAlarma(nombreMedicamento, horaMedicamento, medicamentoId, userId)
                                .addOnCompleteListener { rescheduleTask ->
                                    // Todas las operaciones asíncronas principales han terminado.
                                    stopForeground(true) // Oculta la notificación y saca del primer plano
                                    stopSelf() // Detiene el servicio
                                }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting medicamento status: ${e.message}", e)
                // En caso de error al obtener el estado, igual intentar reprogramar y detener el servicio
                reprogramarAlarma(nombreMedicamento, horaMedicamento, medicamentoId, userId)
                    .addOnCompleteListener {
                        stopForeground(true)
                        stopSelf()
                    }
            }
    }

    // Nueva función para actualizar isTaken a false
    private fun updateIsTakenStatusToFalse(userId: String, medicamentoId: String, nombreMedicamento: String): Task<Void> {
        val docRef = db.collection("Perfil").document(userId).collection("Medicamentos").document(medicamentoId)
        return docRef.update("isTaken", false)
            .addOnSuccessListener {
                Log.d(TAG, "Estado isTaken de $nombreMedicamento restablecido a false para el próximo ciclo.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al restablecer isTaken para $nombreMedicamento: ${e.message}", e)
            }
    }

    private fun reprogramarAlarma(nombreMedicamento: String, hora: String, medicamentoId: String, userId: String): Task<Void> {
        val parts = hora.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Reprogramar para el día siguiente
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Usar el mismo requestCode de la alarma principal para que se sobrescriba
        val requestCode = (medicamentoId + hora).hashCode()

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", ALARM_TYPE_MAIN) // Asegurarse de que es la alarma principal
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot reprogram main alarm: SCHEDULE_EXACT_ALARM permission not granted.")
            return Tasks.forResult(null) // Devuelve una tarea completada si no se puede programar
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Main alarm for $nombreMedicamento reprogrammed for ${calendar.time}.")
        return Tasks.forResult(null) // setExactAndAllowWhileIdle no devuelve una tarea directamente
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Helper para verificar el permiso de notificaciones (Android 13+)
    private fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        } else {
            true // En versiones anteriores a Android 13, las notificaciones están habilitadas por defecto si no se han deshabilitado manualmente.
        }
    }
}