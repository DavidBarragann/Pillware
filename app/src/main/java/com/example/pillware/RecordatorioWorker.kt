package com.example.pillware

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker // Importar CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await // Importar para usar .await()

class RecordatorioWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) { // Cambiar a CoroutineWorker

    override suspend fun doWork(): Result { // Hacer doWork suspend
        val medicamentoId = inputData.getString("medicamentoId")
        val nombreMedicamento = inputData.getString("nombreMedicamento")
        val horaProgramada = inputData.getString("horaProgramada")
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // Verificar datos de entrada. Si algo es nulo, fallar inmediatamente.
        if (medicamentoId == null || nombreMedicamento == null || horaProgramada == null || userId == null) {
            Log.e("RecordatorioWorker", "Datos de entrada incompletos: medicamentoId=$medicamentoId, nombreMedicamento=$nombreMedicamento, horaProgramada=$horaProgramada, userId=$userId")
            return Result.failure()
        }

        val db = FirebaseFirestore.getInstance()
        val perfilRef = db.collection("Perfil").document(userId)
        val medicamentoRef = perfilRef.collection("Medicamentos").document(medicamentoId)

        return try {
            val document = medicamentoRef.get().await() // Esperar el resultado de forma síncrona
            val isTaken = document.getBoolean("isTaken") ?: false

            if (!isTaken) {
                // Si el medicamento no ha sido tomado, notificar al familiar
                val perfil = perfilRef.get().await() // Esperar el resultado
                val correoFamiliar = perfil.getString("familiar")

                if (!correoFamiliar.isNullOrEmpty()) {
                    val mensaje = "¡Alerta! Tu familiar no ha tomado su $nombreMedicamento programado a las $horaProgramada."
                    CorreoHelper.enviarCorreo(applicationContext, mensaje, correoFamiliar)
                    Log.d("RecordatorioWorker", "Correo de alerta enviado a $correoFamiliar por $nombreMedicamento no tomado a las $horaProgramada.")
                } else {
                    Log.e("RecordatorioWorker", "Correo del familiar no disponible para el usuario $userId.")
                    // Considera si esto debería ser un fallo del worker. Por ahora, lo dejaremos como éxito si no hay correo.
                }
            } else {
                Log.d("RecordatorioWorker", "$nombreMedicamento ya fue tomado a la hora programada.")
            }
            Result.success() // Éxito si todo el proceso se ejecuta sin excepciones
        } catch (e: Exception) {
            Log.e("RecordatorioWorker", "Error durante la verificación o envío de recordatorio para medicamento $medicamentoId: ${e.message}", e)
            Result.retry() // Reintentar en caso de cualquier excepción (red, Firebase, etc.)
        }
    }
}