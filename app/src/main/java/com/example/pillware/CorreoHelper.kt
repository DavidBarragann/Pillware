package com.example.pillware

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object CorreoHelper {

    fun enviarCorreo(context: Context, mensajeTexto: String, correoUsuario: String) {
        val json = JSONObject().apply {
            put("service_id", "service_icn6d7u")
            put("template_id", "template_qfeq487")
            put("user_id", "c1GdWftoGV_CKIhld")
            put("template_params", JSONObject().apply {
                put("mensaje", mensajeTexto)
                put("email", correoUsuario)
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.emailjs.com/api/v1.0/email/send")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EmailJS", "Error: ${e.message}")
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Error al enviar correo", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("EmailJS", "Correo enviado")
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Correo enviado correctamente", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("EmailJS", "Error: ${response.message}")
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Error al enviar: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
