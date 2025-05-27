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
import android.widget.Toast // Para mostrar Toasts si es necesario
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pillware.data.NotificationItem
import com.example.pillware.data.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.*

class AlarmProcessingService : Service() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val CHANNEL_ID = "medicamento_channel"
    private val SERVICE_NOTIFICATION_ID = 101 // ID único para la notificación del servicio en primer plano
    private val TAG = "AlarmProcessingService"

    private val ALARM_TYPE_MAIN = "main_alarm"
    private val ALARM_TYPE_FOLLOW_UP = "follow_up_alarm"
    private val ACTION_MARK_TAKEN = "mark_taken" // Nueva constante para la acción de marcar tomado

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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(SERVICE_NOTIFICATION_ID, notification)

        val nombreMedicamento = intent?.getStringExtra("nombre")
        val horaMedicamento = intent?.getStringExtra("hora")
        val medicamentoId = intent?.getStringExtra("medicamentoId")
        val userId = intent?.getStringExtra("userId")
        val alarmType = intent?.getStringExtra("alarmType") ?: ALARM_TYPE_MAIN

        Log.d(TAG, "Service started for: $nombreMedicamento at $horaMedicamento, ID: $medicamentoId, UserID: $userId, Type: $alarmType")

        if (medicamentoId == null || userId == null) {
            Log.e(TAG, "Intent data missing in service. Stopping self.")
            stopServiceAndForeground()
            return START_NOT_STICKY
        }

        when (alarmType) {
            ALARM_TYPE_MAIN -> {
                showNotification(nombreMedicamento.toString(),
                    horaMedicamento.toString(), medicamentoId, userId) // Pasar userId aquí
                scheduleFollowUpAlarm(nombreMedicamento.toString(),
                    horaMedicamento.toString(), medicamentoId, userId)

                val notificationItem = NotificationItem(
                    id = UUID.randomUUID().toString(),
                    tipo = NotificationType.PROXIMA_TOMA,
                    titulo = "¡Es hora de tu medicamento!",
                    mensaje = "$nombreMedicamento a las $horaMedicamento",
                    medicamentoNombre = nombreMedicamento,
                    fechaHora = Date(),
                    iconoResId = R.drawable.baseline_access_time_24
                )
                saveNotificationToFirestore(userId, notificationItem) { // Añadir callback de finalización
                    Log.d(TAG, "Notificación PROXIMA_TOMA guardada. Deteniendo servicio si no hay más tareas.")
                    // Aquí no detenemos el servicio porque aún hay alarmas de seguimiento por programar.
                    // El servicio se detendrá cuando la última tarea asíncrona termine.
                }
            }
            ALARM_TYPE_FOLLOW_UP -> {
                checkMedicamentoStatusAndSendEmail(nombreMedicamento.toString(),
                    horaMedicamento.toString(), medicamentoId, userId)
            }
            ACTION_MARK_TAKEN -> {
                val notificationIdToCancel = intent.getIntExtra("notificationId", -1)
                markMedicineAsTaken(medicamentoId, userId, notificationIdToCancel)
            }
        }

        return START_NOT_STICKY
    }

    private fun showNotification(
        nombreMedicamento: String,
        hora: String,
        medicamentoId: String,
        userId: String // Necesitamos userId aquí para el PendingIntent de "Marcar como tomado"
    ) {
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("¡Es hora de tu medicamento!")
            .setContentText("$nombreMedicamento a las $hora")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        val notificationId = (medicamentoId + hora).hashCode()

        // Intent para abrir la app
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingMainActivityIntent: PendingIntent = PendingIntent.getActivity(
            this,
            notificationId + 100, // Request code diferente
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        notificationBuilder.setContentIntent(pendingMainActivityIntent)

        // Intent para la acción "Marcar como tomado"
        val markTakenIntent = Intent(this, AlarmReceiver::class.java).apply { // Vuelve al AlarmReceiver
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", ACTION_MARK_TAKEN) // Tipo de acción
            putExtra("notificationId", notificationId) // Pasar el ID de la notificación para cancelarla
        }
        val pendingMarkTakenIntent = PendingIntent.getBroadcast(
            this,
            notificationId + 200, // Request code diferente
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        notificationBuilder.addAction(
            R.drawable.baseline_check_circle_24, // Asegúrate de tener este drawable
            "Marcar como tomado",
            pendingMarkTakenIntent
        )

        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are not enabled for this app. Cannot show notification.")
                return
            }
            notify(notificationId, notificationBuilder.build())
        }
        Log.d(TAG, "Notification shown for: $nombreMedicamento (ID: $notificationId)")
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
        }
    }

    private fun saveNotificationToFirestore(userId: String, notification: NotificationItem, onComplete: () -> Unit = {}) {
        val notificationMap = hashMapOf(
            "tipo" to notification.tipo.name,
            "titulo" to notification.titulo,
            "mensaje" to notification.mensaje,
            "medicamentoNombre" to notification.medicamentoNombre,
            "timestamp" to FieldValue.serverTimestamp(),
            "iconoResId" to notification.iconoResId,
            "leida" to false
        )

        db.collection("Perfil").document(userId)
            .collection("Notificaciones")
            .add(notificationMap)
            .addOnSuccessListener { documentReference ->
                Log.d("Firestore", "Notificación guardada con ID: ${documentReference.id}")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error al guardar notificación: ${e.message}", e)
                onComplete()
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

        calendar.add(Calendar.MINUTE, 10) // 10 minutos para la alarma de seguimiento

        if (calendar.before(Calendar.getInstance())) { // Si ya pasó, programar para mañana
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val followUpRequestCode = (medicamentoId + horaOriginal).hashCode() + 1 // Request code único

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", horaOriginal)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", ALARM_TYPE_FOLLOW_UP)
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
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Follow-up alarm for $nombreMedicamento scheduled for ${calendar.time}.")
    }

    private fun markMedicineAsTaken(medicamentoId: String, userId: String, notificationId: Int) {
        Log.d(TAG, "Action: Mark medicine $medicamentoId as taken by user $userId. Notif ID: $notificationId")
        val db = FirebaseFirestore.getInstance()
        val medicamentoRef = db.collection("Perfil").document(userId)
            .collection("Medicamentos").document(medicamentoId)

        medicamentoRef.update("isTaken", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Medicamento marcado como tomado.", Toast.LENGTH_SHORT).show()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationId != -1) {
                    notificationManager.cancel(notificationId) // Cancelar la notificación principal
                    Log.d(TAG, "Notification $notificationId cancelled.")
                }
                cancelFollowUpAlarm(medicamentoId) // Cancela la alarma de seguimiento

                // **GUARDAR NOTIFICACIÓN DE TOMA_COMPLETADA**
                val notificationItem = NotificationItem(
                    id = UUID.randomUUID().toString(),
                    tipo = NotificationType.TOMA_COMPLETADA,
                    titulo = "Toma Registrada",
                    mensaje = "Has marcado '$medicamentoId' como tomado.", // Puedes mejorar el mensaje con el nombre del medicamento real
                    medicamentoNombre = medicamentoId, // Aquí deberías tener el nombre real del medicamento
                    fechaHora = Date(),
                    iconoResId = R.drawable.baseline_check_circle_24 // Asegúrate de tener este drawable
                )
                // Para el nombre del medicamento real, necesitarías cargarlo desde Firestore o pasarlo por el Intent
                // Por simplicidad, aquí estoy usando medicamentoId, pero es mejor el nombre real.
                db.collection("Perfil").document(userId)
                    .collection("Medicamentos").document(medicamentoId)
                    .get()
                    .addOnSuccessListener { doc ->
                        val nombreReal = doc.getString("Nombre") ?: medicamentoId
                        val completedNotification = notificationItem.copy(
                            mensaje = "Has marcado '$nombreReal' como tomado.",
                            medicamentoNombre = nombreReal
                        )
                        saveNotificationToFirestore(userId, completedNotification) {
                            Log.d(TAG, "Notificación TOMA_COMPLETADA guardada. Deteniendo servicio.")
                            stopServiceAndForeground() // Finalizar el servicio después de esta acción
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error getting real medicine name for completed notification: ${e.message}", e)
                        saveNotificationToFirestore(userId, notificationItem) {
                            Log.d(TAG, "Notificación TOMA_COMPLETADA guardada (sin nombre real). Deteniendo servicio.")
                            stopServiceAndForeground() // Finalizar el servicio
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al marcar como tomado: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error marking medicine as taken: ${e.message}", e)
                stopServiceAndForeground() // Detener el servicio incluso en caso de error
            }
    }

    private fun cancelFollowUpAlarm(medicamentoId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val followUpRequestCode = (medicamentoId + "_follow_up").hashCode() // Usa el mismo request code de la programación

        val followUpIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("medicamentoId", medicamentoId)
            putExtra("alarmType", ALARM_TYPE_FOLLOW_UP)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            followUpRequestCode,
            followUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Follow-up alarm for $medicamentoId cancelled.")
    }

    private fun checkMedicamentoStatusAndSendEmail(
        nombreMedicamento: String,
        horaMedicamento: String,
        medicamentoId: String,
        userId: String
    ) {
        val medicamentoDocRef = db.collection("Perfil").document(userId).collection("Medicamentos").document(medicamentoId)

        medicamentoDocRef.get()
            .addOnSuccessListener { documentSnapshot ->
                val isTaken = documentSnapshot.getBoolean("isTaken") ?: false
                Log.d(TAG, "Verificando estado para $nombreMedicamento: isTaken = $isTaken")

                if (!isTaken) {
                    // El medicamento NO ha sido tomado, obtener correo del familiar y enviar.
                    db.collection("Perfil").document(userId).get()
                        .addOnSuccessListener { userProfileSnapshot ->
                            val correoFamiliar = userProfileSnapshot.getString("familiar")
                            if (!correoFamiliar.isNullOrEmpty()) {
                                Log.d(TAG, "Enviando correo al familiar: $correoFamiliar")
                                val mensaje = "¡Alerta de PillWare!\n\nEl medicamento '$nombreMedicamento' no ha sido marcado como tomado por el usuario a las $horaMedicamento."
                                CorreoHelper.enviarCorreo(applicationContext, mensaje, correoFamiliar)

                                // **GUARDAR NOTIFICACIÓN DE RECORDATORIO (con familiar)**
                                val notificationItem = NotificationItem(
                                    id = UUID.randomUUID().toString(),
                                    tipo = NotificationType.RECORDATORIO,
                                    titulo = "Recordatorio: Toma Pendiente",
                                    mensaje = "Parece que $nombreMedicamento no ha sido registrado como tomado a las $horaMedicamento. Se ha notificado al familiar.",
                                    medicamentoNombre = nombreMedicamento,
                                    fechaHora = Date(),
                                    iconoResId = R.drawable.baseline_warning_24
                                )
                                saveNotificationToFirestore(userId, notificationItem) {
                                    // Después de guardar la notificación y enviar el correo (simulado por CorreoHelper)
                                    resetAndReprogram(userId, medicamentoId, nombreMedicamento, horaMedicamento)
                                }
                            } else {
                                Log.w(TAG, "No se encontró correo de familiar para el usuario $userId. No se envía correo.")
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
                                saveNotificationToFirestore(userId, notificationItem) {
                                    // Después de guardar la notificación
                                    resetAndReprogram(userId, medicamentoId, nombreMedicamento, horaMedicamento)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error obteniendo perfil de usuario para correo de familiar: ${e.message}", e)
                            val notificationItem = NotificationItem(
                                id = UUID.randomUUID().toString(),
                                tipo = NotificationType.RECORDATORIO,
                                titulo = "Recordatorio: Toma Pendiente",
                                mensaje = "Parece que $nombreMedicamento no ha sido registrado como tomado a las $horaMedicamento.",
                                medicamentoNombre = nombreMedicamento,
                                fechaHora = Date(),
                                iconoResId = R.drawable.baseline_warning_24
                            )
                            saveNotificationToFirestore(userId, notificationItem) {
                                resetAndReprogram(userId, medicamentoId, nombreMedicamento, horaMedicamento)
                            }
                        }
                } else {
                    Log.d(TAG, "$nombreMedicamento ya fue marcado como tomado. No se envía correo. Restableciendo y reprogramando.")
                    // Si ya fue tomado, simplemente restablecer y reprogramar
                    // No necesitas guardar una notificación de "completada" aquí si ya la guardas al marcar.
                    resetAndReprogram(userId, medicamentoId, nombreMedicamento, horaMedicamento)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error obteniendo estado del medicamento: ${e.message}", e)
                // En caso de error, al menos intentar reprogramar para el día siguiente
                reprogramarAlarma(nombreMedicamento, horaMedicamento, medicamentoId, userId)
                    .addOnCompleteListener {
                        stopServiceAndForeground()
                    }
            }
    }

    private fun resetAndReprogram(userId: String, medicamentoId: String, nombreMedicamento: String, horaMedicamento: String) {
        updateIsTakenStatusToFalse(userId, medicamentoId, nombreMedicamento)
            .addOnCompleteListener { updateTask ->
                reprogramarAlarma(nombreMedicamento, horaMedicamento, medicamentoId, userId)
                    .addOnCompleteListener { rescheduleTask ->
                        Log.d(TAG, "Todas las operaciones asíncronas completadas. Deteniendo servicio.")
                        stopServiceAndForeground() // Detener el servicio aquí
                    }
            }
    }

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
            add(Calendar.DAY_OF_YEAR, 1) // Reprogramar para el día siguiente
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = (medicamentoId + hora).hashCode() // El mismo request code para sobrescribir

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
            return Tasks.forResult(null)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Main alarm for $nombreMedicamento reprogrammed for ${calendar.time}.")
        return Tasks.forResult(null)
    }

    private fun stopServiceAndForeground() {
        stopForeground(true) // Elimina la notificación y detiene el servicio en primer plano
        stopSelf() // Detiene el servicio
        Log.d(TAG, "Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }
}