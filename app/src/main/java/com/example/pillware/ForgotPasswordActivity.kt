package com.example.pillware

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pillware.R
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var btnResetPassword: Button
    private lateinit var textViewMessage: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        editTextEmail = findViewById(R.id.editTextEmail)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        textViewMessage = findViewById(R.id.textViewMessage)
        auth = FirebaseAuth.getInstance()

        btnResetPassword.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            if (email.isEmpty()) {
                editTextEmail.error = "El correo electrónico es requerido"
                return@setOnClickListener
            }

            sendPasswordResetEmail(email)
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    textViewMessage.text = "Se ha enviado un correo electrónico a $email para restablecer tu contraseña."
                    Toast.makeText(this, "Correo electrónico de restablecimiento enviado", Toast.LENGTH_SHORT).show()
                } else {
                    textViewMessage.text = "Error al enviar el correo electrónico de restablecimiento: ${task.exception?.message}"
                    Toast.makeText(this, "Error al enviar el correo electrónico", Toast.LENGTH_SHORT).show()
                }
            }
    }
}