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
            put("user_id", "4jvIS7_D48-d_L89T")
            put("template_params", JSONObject().apply {
                put("mensaje", mensajeTexto) // Asegúrate de que 'mensaje' coincida con tu plantilla de EmailJS
                put("email", correoUsuario) // Asegúrate de que 'email' coincida con tu plantilla de EmailJS
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
                Log.e("EmailJS", "Error de red: ${e.message}")
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Error de red al enviar correo", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // response.use{} asegura que el cuerpo de la respuesta se cierre automáticamente
                response.use {
                    if (response.isSuccessful) {
                        Log.d("EmailJS", "Correo enviado con éxito. Código: ${response.code}")
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Correo enviado correctamente", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorBody = response.body?.string() // Captura el cuerpo del error para más detalles
                        Log.e("EmailJS", "Error al enviar correo: Código ${response.code}, Mensaje: ${response.message}, Cuerpo: $errorBody")
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Error al enviar correo: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }
}
