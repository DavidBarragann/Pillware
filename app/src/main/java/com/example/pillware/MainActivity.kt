package com.example.pillware

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.pillware.databinding.ActivityMainBinding
import com.example.pillware.ui.home.HomeFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar el binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Establecer el HomeFragment como el fragmento por defecto
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        // Configurar el BottomNavigationView para manejar las selecciones de menú
        binding.navView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment())
                    return@setOnNavigationItemSelectedListener true
                }
                // Añade más casos si tienes más opciones en el menú
            }
            false
        }
    }

    // Función para cargar un fragmento en el contenedor principal
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
