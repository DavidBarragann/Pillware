package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MedicamentoAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicamentoId = intent.getStringExtra("medicamentoId") ?: run {
            Log.e("MedicamentoAlarmReceiver", "medicamentoId es nulo, no se puede procesar la alarma.")
            return
        }
        val medicamentoNombre = intent.getStringExtra("medicamentoNombre") ?: run {
            Log.e("MedicamentoAlarmReceiver", "medicamentoNombre es nulo, no se puede procesar la alarma.")
            return
        }
        val usuarioUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e("MedicamentoAlarmReceiver", "Usuario no autenticado, no se puede procesar la alarma.")
            return
        }
        val scheduledTime = intent.getStringExtra("scheduledTime") ?: run {
            Log.e("MedicamentoAlarmReceiver", "scheduledTime es nulo, no se puede procesar la alarma.")
            return
        }

        Log.d("MedicamentoAlarmReceiver", "Alarma activada para: $medicamentoNombre (ID: $medicamentoId) para la hora programada: $scheduledTime")

        val db = FirebaseFirestore.getInstance()

        db.collection("Perfil").document(usuarioUid)
            .collection("Medicamentos").document(medicamentoId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val isTaken = document.getBoolean("isTaken") ?: false

                    // Si el medicamento NO ha sido marcado como tomado, se envía el correo al familiar.
                    if (!isTaken) {
                        Log.d("MedicamentoAlarmReceiver", "¡$medicamentoNombre no ha sido tomado para la hora $scheduledTime! Enviando correo a familiar.")
                        obtenerYEnviarCorreoFamiliar(context, medicamentoNombre, scheduledTime)
                    } else {
                        Log.d("MedicamentoAlarmReceiver", "$medicamentoNombre para la hora $scheduledTime ya fue marcado como tomado.")
                    }
                } else {
                    Log.d("MedicamentoAlarmReceiver", "Medicamento no encontrado en Firestore para ID: $medicamentoId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MedicamentoAlarmReceiver", "Error al obtener datos del medicamento: ${e.message}", e)
            }
    }

    private fun obtenerYEnviarCorreoFamiliar(context: Context, medicamentoNombre: String, scheduledTime: String) {
        val usuarioUid = FirebaseAuth.getInstance().currentUser?.uid
        if (usuarioUid == null) {
            Log.w("MedicamentoAlarmReceiver", "No hay usuario autenticado para buscar el correo familiar.")
            return
        }

        FirebaseFirestore.getInstance().collection("Perfil").document(usuarioUid)
            .get()
            .addOnSuccessListener { document ->
                val correoFamiliar = document.getString("familiar") // Obtener el correo del campo "familiar"
                if (!correoFamiliar.isNullOrEmpty()) {
                    val mensaje = "¡Alyerta! El usuario no ha confirmado la toma de $medicamentoNombre programado para las $scheduledTime. Han pasado 10 minutos."
                    CorreoHelper.enviarCorreo(context, mensaje, correoFamiliar)
                    Log.d("MedicamentoAlarmReceiver", "Correo de alerta enviado a: $correoFamiliar")
                } else {
                    Log.w("MedicamentoAlarmReceiver", "No se encontró correo familiar en el campo 'familiar' para enviar la alerta.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MedicamentoAlarmReceiver", "Error al obtener el correo del familiar del perfil: ${e.message}", e)
            }
    }
}