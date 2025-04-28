package com.example.pillware

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.pillware.R
import com.example.pillware.databinding.ActivityMainBinding
import com.example.pillware.ui.home.HomeFragment
import com.example.pillware.ui.locations.LocationsFragment
import com.example.pillware.ui.notifications.NotificationsFragment
import com.example.pillware.ui.calendar.CalendarFragment // Import CalendarFragment
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
                R.id.navigation_location -> {
                    loadFragment(LocationsFragment())
                    return@setOnNavigationItemSelectedListener true
                }
                R.id.navigation_notifications -> {
                    // Assuming "title_calendar" in your strings.xml corresponds to the notifications fragment
                    loadFragment(NotificationsFragment())
                    return@setOnNavigationItemSelectedListener true
                }
                R.id.navigation_calendar -> {
                    loadFragment(CalendarFragment()) // Load CalendarFragment
                    return@setOnNavigationItemSelectedListener true
                }
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