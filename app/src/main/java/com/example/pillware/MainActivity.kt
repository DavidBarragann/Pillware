package com.example.pillware

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pillware.databinding.ActivityMainBinding
import com.example.pillware.ui.home.HomeFragment
import com.example.pillware.ui.locations.LocationsFragment
import com.example.pillware.ui.historial.HistorialFragment
import com.example.pillware.ui.calendar.CalendarFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 1. Registra el ActivityResultLauncher para manejar la solicitud de permiso
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido
                Toast.makeText(this, "Permiso de notificaciones concedido.", Toast.LENGTH_SHORT).show()
            } else {
                // Permiso denegado
                Toast.makeText(this, "Las notificaciones son necesarias para los recordatorios de medicamentos.", Toast.LENGTH_LONG).show()
                // Opcional: Aquí podrías mostrar un diálogo explicando por qué el permiso es necesario
                // y guiar al usuario a la configuración de la aplicación.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Establecer el HomeFragment como el fragmento por defecto
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        // 2. Llama a la función para verificar y solicitar el permiso al inicio
        checkNotificationPermission()

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
                R.id.navigation_historial -> {
                    loadFragment(HistorialFragment())
                    return@setOnNavigationItemSelectedListener true
                }
                R.id.navigation_calendar -> {
                    loadFragment(CalendarFragment())
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

    // 3. Función para verificar y solicitar el permiso de notificaciones
    private fun checkNotificationPermission() {
        // Solo para Android 13 (API 33) y versiones superiores
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
            } else {
                // El permiso no está concedido, solicitarlo al usuario
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}