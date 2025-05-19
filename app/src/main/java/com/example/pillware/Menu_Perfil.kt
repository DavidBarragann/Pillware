package com.example.pillware

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pillware.databinding.ActivityMenuPerfilBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Menu_Perfil : AppCompatActivity() {

    private lateinit var binding: ActivityMenuPerfilBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMenuPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val atrasButton: ImageView = binding.atras
        val nombreEditText: EditText = binding.usuario
        val edadEditText: EditText = binding.edad
        val emailEditText: EditText = binding.emailedittext
        val telefonoEditText: EditText = binding.teledittext
        val familiarEditText: EditText = binding.FamiliarEdit
        val actualizarPerfilButton: Button = binding.aceptar

        // Cargar datos del perfil si existen
        loadUserProfile()

        // Listener para el botón "Atrás"
        atrasButton.setOnClickListener {
            finish() // Simplemente regresa a la actividad anterior
        }

        // Listener para el botón "Actualizar perfil"
        actualizarPerfilButton.setOnClickListener {
            updateUserProfile()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadUserProfile() {
        currentUser?.uid?.let { uid ->
            db.collection("Perfil").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val nombre = document.getString("nombre")
                        val edad = document.getString("edad")
                        val email = document.getString("email")
                        val telefono = document.getString("telefono")
                        val familiar = document.getString("familiar")

                        binding.usuario.hint = nombre ?: "Nombre Completo"
                        binding.edad.hint = edad ?: "Ingresa tu edad"
                        binding.emailedittext.hint = email ?: "example@example.com"
                        binding.teledittext.hint = telefono ?: "+52 9999999999"
                        binding.FamiliarEdit.hint = familiar ?: "Ingresa el correo de tu familiar"
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al cargar el perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateUserProfile() {
        currentUser?.uid?.let { uid ->
            val nombre = binding.usuario.text.toString().trim()
            val edad = binding.edad.text.toString().trim()
            val email = binding.emailedittext.text.toString().trim()
            val telefono = binding.teledittext.text.toString().trim()
            val familiar = binding.FamiliarEdit.text.toString().trim()

            val userProfile = hashMapOf(
                "nombre" to (if (nombre.isNotEmpty()) nombre else binding.usuario.hint.toString()),
                "edad" to (if (edad.isNotEmpty()) edad else binding.edad.hint.toString()),
                "email" to (if (email.isNotEmpty()) email else binding.emailedittext.hint.toString()),
                "telefono" to (if (telefono.isNotEmpty()) telefono else binding.teledittext.hint.toString()),
                "familiar" to (if (familiar.isNotEmpty()) familiar else binding.FamiliarEdit.hint.toString())
            )

            db.collection("Perfil").document(uid)
                .set(userProfile)
                .addOnSuccessListener {
                    Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                    loadUserProfile() // Recargar los hints para reflejar los cambios
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al actualizar el perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }
    }
}