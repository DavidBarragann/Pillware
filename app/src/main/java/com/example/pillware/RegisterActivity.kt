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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore // Import Firestore
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore // Declare Firestore instance
    private lateinit var editTextNombre: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var editTextFechaNacimiento: EditText
    private lateinit var buttonRegister: Button
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance() // Initialize Firestore

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.client_web)) // Make sure this string resource exists
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize UI elements
        val buttonBack = findViewById<ImageView>(R.id.backarrowreg)
        val buttonLogin = findViewById<TextView>(R.id.regLogin)
        editTextNombre = findViewById(R.id.usuario)
        editTextEmail = findViewById(R.id.emailedittext)
        editTextPassword = findViewById(R.id.pass)
        editTextConfirmPassword = findViewById(R.id.confirm_pass) // Assuming you have this in your layout
        editTextFechaNacimiento = findViewById(R.id.fechanac_edittext)
        buttonRegister = findViewById(R.id.Registrar)
        val googleSignInButton = findViewById<ImageView>(R.id.googleSignIn)

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

        // Botón de Google Sign-In
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
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
        val fechaNacimiento = editTextFechaNacimiento.text.toString().trim()

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

        if (password != confirmPassword) {
            editTextConfirmPassword.error = "Las contraseñas no coinciden"
            editTextConfirmPassword.requestFocus()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("RegisterActivity", "createUserWithEmail:success")
                    val user = auth.currentUser
                    user?.let {
                        // Save user data to Firestore
                        saveUserDataToFirestore(it.uid, name, email, fechaNacimiento)
                    }
                    navigateToMainActivity(user?.email)
                    finish()
                } else {
                    Log.w("RegisterActivity", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Registro fallido. Inténtelo de nuevo.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account?.idToken)
            } catch (e: ApiException) {
                Log.w("RegisterActivity", "Google sign in failed", e)
                Toast.makeText(baseContext, "Error al registrarse con Google.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String?) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d("RegisterActivity", "firebaseAuthWithGoogle:success")
                    user?.let {
                        // Save Google user email to Firestore
                        saveGoogleUserEmailToFirestore(it.uid, it.email)
                    }
                    navigateToMainActivity(user?.email)
                    finish()
                } else {
                    Log.w("RegisterActivity", "firebaseAuthWithGoogle:failure", task.exception)
                    Toast.makeText(baseContext, "Registro fallido con Google.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- Firestore Functions ---
    private fun saveUserDataToFirestore(uid: String, name: String, email: String, fechaNacimiento: String) {
        val userProfile = hashMapOf(
            "nombre" to name,
            "email" to email,
            "fecha_nacimiento" to fechaNacimiento,
            "otros_datos_completados" to false // Flag to indicate if other data needs to be filled
        )

        firestore.collection("Perfil")
            .document(uid)
            .set(userProfile)
            .addOnSuccessListener {
                Log.d("RegisterActivity", "User data saved to Firestore for email/password user: $uid")
                Toast.makeText(baseContext, "Datos de usuario guardados.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("RegisterActivity", "Error saving user data to Firestore", e)
                Toast.makeText(baseContext, "Error al guardar datos del usuario.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveGoogleUserEmailToFirestore(uid: String, email: String?) {
        val userProfile = hashMapOf(
            "email" to (email ?: "N/A"), // Use "N/A" if email is null
            "otros_datos_completados" to false // Flag to indicate if other data needs to be filled
        )

        firestore.collection("Perfil")
            .document(uid)
            .set(userProfile) // Use set() to create or overwrite the document
            .addOnSuccessListener {
                Log.d("RegisterActivity", "Google user email saved to Firestore for user: $uid")
                Toast.makeText(baseContext, "Correo de Google guardado.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("RegisterActivity", "Error saving Google user email to Firestore", e)
                Toast.makeText(baseContext, "Error al guardar el correo de Google.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToMainActivity(email: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("EXTRA_TEXTO", email)
        startActivity(intent)
    }
}