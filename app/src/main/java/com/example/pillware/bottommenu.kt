package com.example.pillware

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pillware.databinding.ActivityBottommenuBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class bottommenu : AppCompatActivity() {

    private lateinit var binding: ActivityBottommenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBottommenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_bottommenu)

        navView.setupWithNavController(navController)

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home, R.id.navigation_location, R.id.navigation_notifications, R.id.navigation_calendar -> {
                    navController.navigate(item.itemId)
                    true
                }
                else -> false
            }
        }
    }
}
