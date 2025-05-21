package com.example.pillware

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pillware.databinding.ActivityPrimeraBinding

class Primera : AppCompatActivity() {

    private lateinit var binding: ActivityPrimeraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPrimeraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Listener para botón "Iniciar sesión"
        binding.IniciarSesion.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // Listener para botón "Registrar"
        binding.Registrarse.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}