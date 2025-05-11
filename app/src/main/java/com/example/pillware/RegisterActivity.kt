package com.example.pillware

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var editTextNombre: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var editTextFechaNacimiento: EditText
    private lateinit var buttonRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // Initialize UI elements
        val buttonBack = findViewById<ImageView>(R.id.backarrowreg)
        val buttonLogin = findViewById<TextView>(R.id.regLogin)
        editTextNombre = findViewById(R.id.usuario)
        editTextEmail = findViewById(R.id.emailedittext)
        editTextPassword = findViewById(R.id.pass)
        editTextFechaNacimiento = findViewById(R.id.fechanac_edittext)
        buttonRegister = findViewById(R.id.Registrar)

        // Botón de retroceso
        buttonBack.setOnClickListener {
            val intento = Intent(this, LoginActivity::class.java)
            startActivity(intento)
            finish() // Optional: Close the RegisterActivity
        }

        // Botón de login
        buttonLogin.setOnClickListener {
            val intento = Intent(this, LoginActivity::class.java)
            startActivity(intento)
            finish() // Optional: Close the RegisterActivity
        }

        // Campo de Fecha de Nacimiento
        val color = ContextCompat.getColor(this, R.color.lightpurpletext)
        editTextFechaNacimiento.setTextColor(color)
        editTextFechaNacimiento.setOnClickListener {
            showDatePickerDialog()
        }

        // Botón de Registrar
        buttonRegister.setOnClickListener {
            signUpWithEmailAndPassword()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
            editTextFechaNacimiento.setText(selectedDate)
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun signUpWithEmailAndPassword() {
        val name = editTextNombre.text.toString().trim()
        val email = editTextEmail.text.toString().trim()
        val password = editTextPassword.text.toString().trim()
        val confirmPassword = editTextConfirmPassword.text.toString().trim()
        val fechaNacimiento = editTextFechaNacimiento.text.toString().trim() // You might want to parse this into a Date object later

        if (name.isEmpty()) {
            editTextNombre.error = "El nombre es requerido"
            editTextNombre.requestFocus()
            return
        }

        if (email.isEmpty()) {
            editTextEmail.error = "El correo electrónico es requerido"
            editTextEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            editTextPassword.error = "La contraseña es requerida"
            editTextPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            editTextPassword.error = "La contraseña debe tener al menos 6 caracteres"
            editTextPassword.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            editTextConfirmPassword.error = "Confirme la contraseña"
            editTextConfirmPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            editTextConfirmPassword.error = "Las contraseñas no coinciden"
            editTextConfirmPassword.requestFocus()
            return
        }

        // Create user with email and password
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("RegisterActivity", "createUserWithEmail:success")
                    val user = auth.currentUser
                    // You might want to save the user's name and fechaNacimiento to Firebase as well
                    navigateToMainActivity(user?.email)
                    finish() // Close the register activity
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("RegisterActivity", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Registro fallido. Inténtelo de nuevo.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMainActivity(email: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("EXTRA_TEXTO", email)
        startActivity(intent)
    }
}