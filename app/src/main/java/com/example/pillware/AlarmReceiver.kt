package com.example.pillware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nombre = intent.getStringExtra("nombre") ?: return
        val horaProgramada = intent.getStringExtra("hora") ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val db = FirebaseFirestore.getInstance()
        val medicamentosRef = db.collection("Perfil").document(uid).collection("Medicamentos")

        medicamentosRef.whereEqualTo("Nombre", nombre)
            .whereEqualTo("Hora", horaProgramada)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val horaTomado = doc.getString("HoraTomado")

                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val ahora = sdf.format(Date())

                    val correo = FirebaseAuth.getInstance().currentUser?.email ?: return@addOnSuccessListener

                    // Si no existe HoraTomado o no coincide con la hora actual, no se tomó
                    if (horaTomado.isNullOrBlank() || horaTomado != ahora) {
                        val mensaje = "Usuario aún no ha tomado la medicina: $nombre"
                        CorreoHelper.enviarCorreo(context, mensaje, correo)
                        Log.d("AlarmReceiver", "Se envió correo porque no se tomó $nombre")
                    } else {
                        Log.d("AlarmReceiver", "Medicamento $nombre sí fue tomado a tiempo")
                    }
                }
            }
    }
}