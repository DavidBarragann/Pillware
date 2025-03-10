package com.example.pillware

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val editTextUsername = findViewById<EditText>(R.id.usuario)
        val editTextPassword = findViewById<EditText>(R.id.pass)
        val buttonLogin = findViewById<Button>(R.id.aceptar)
        val textViewTitulo = findViewById<TextView>(R.id.titulo)
        val buttonregister= findViewById<TextView>(R.id.registrarse)

        buttonregister.setOnClickListener{
            val intento = Intent(this,RegisterActivity::class.java)
            startActivity(intento)
        }

        buttonLogin.setOnClickListener {
            val email = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                Log.d("Contrasena",password)
                signIn(email, password)
            } else {
                textViewTitulo.text = "Por favor, ingrese sus credenciales."
            }
        }

    }

    private fun signIn(email: String, password: String) {
        Log.d("Inicio signin","Inicio del sigin exitoso")
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithEmail:success")
                    val user = auth.currentUser
                    val intento = Intent(this, MainActivity::class.java)
                    intento.putExtra("EXTRA_TEXTO", email)
                    startActivity(intento)
                    finish() // Termina la actividad de login para que no vuelva al presionar atrás
                } else {
                    Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext,
                        "Usuario o contraseña incorrectos.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
    }

    private fun reload() {
        // Aquí podrías recargar la información del usuario si fuera necesario
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            reload()
        }
    }
}
