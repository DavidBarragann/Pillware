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
import com.google.firebase.auth.FirebaseUser

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val editTextUsername = findViewById<EditText>(R.id.usuario)
        val editTextPassword = findViewById<EditText>(R.id.pass)
        val buttonLogin = findViewById<Button>(R.id.aceptar)
        val buttonCancel = findViewById<Button>(R.id.cancel)
        val textViewTitulo = findViewById<TextView>(R.id.titulo)

        buttonLogin.setOnClickListener {
            val email = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                signIn(email, password)
            } else {
                textViewTitulo.text = "Por favor, ingrese sus credenciales."
            }
        }
        buttonCancel.setOnClickListener {
            editTextUsername.text.clear()
            editTextPassword.text.clear()
        }
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithEmail:success")
                    val user = auth.currentUser
                    val intento = Intent(this, MainActivity::class.java)
                    intento.putExtra("EXTRA_TEXTO",email)//donde agregamos al información
                    startActivity(intento)
                } else {
                    Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext,
                        "Error de autenticación.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
    }

    private fun reload() {
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            reload()
        }
    }
}
