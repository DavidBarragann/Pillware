package com.example.pillware

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout // Importa FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pillware.databinding.ActivityOpcionesBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.facebook.login.LoginManager // Para cerrar sesión de Facebook

class opciones : AppCompatActivity() {

    private lateinit var binding: ActivityOpcionesBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var overlayView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityOpcionesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // --- Configuración del Bottom Sheet ---
        val bottomSheet: FrameLayout = binding.bottomSheet
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            peekHeight = 1
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        overlayView = binding.logout
        overlayView.setOnClickListener {
            // Oculta el bottom sheet si el overlay es clickeado (y el bottom sheet no está oculto)
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        overlayView.visibility = View.GONE
                        overlayView.alpha = 0.0f
                    }
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_COLLAPSED, // También queremos el overlay si está colapsado
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_SETTLING -> {
                        overlayView.visibility = View.VISIBLE
                        // No establecemos alpha fijo aquí, lo animamos en onSlide
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // slideOffset va de 0 a 1.
                // 0.0 cuando está completamente oculto.
                // 1.0 cuando está completamente expandido.
                // Queremos que el overlay sea 0.0 cuando el bottom sheet está oculto (slideOffset 0.0)
                // y que llegue a 0.7f cuando está completamente expandido (slideOffset 1.0).
                // Una forma simple es: alpha = slideOffset * max_alpha_desired
                overlayView.alpha = slideOffset * 0.7f
            }
        })
        // --- Fin de la configuración del Bottom Sheet ---


        // --- Listeners para los elementos de la UI ---
        val buttonback = binding.backopc
        val buttonedit: LinearLayout = binding.editarDatos
        val politicasLayout: LinearLayout = binding.politicas
        val logoutLayout: LinearLayout = binding.logout
        val btnCancelar: TextView = binding.cancelar
        val btnCerrarSesion: TextView = binding.cerrarSesion


        politicasLayout.setOnClickListener {
            val privacyPolicyUrl = "https://www.freeprivacypolicy.com/live/02f8741b-f84c-4150-9d77-e4f7b0fbfe39"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
            startActivity(browserIntent)
        }

        buttonback.setOnClickListener {
            val intento = Intent(this, MainActivity::class.java)
            startActivity(intento)
            finish()
        }

        buttonedit.setOnClickListener {
            val intent = Intent(this, Menu_Perfil::class.java)
            startActivity(intent)
        }

        // --- Click Listener para el botón de Logout (LinearLayout) ---
        logoutLayout.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED // Muestra el bottom sheet
        }

        // --- Click Listener para el botón "Cancelar" en el Bottom Sheet ---
        btnCancelar.setOnClickListener {
            val intent = Intent(this, opciones::class.java)
            startActivity(intent)
            finish()
        }

        // --- Click Listener para el botón "Cerrar Sesión" en el Bottom Sheet ---
        btnCerrarSesion.setOnClickListener {
            performLogout()
        }
        // --- Fin de los Listeners ---


        ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinatorLayout) { v, insets -> // Usa el ID del CoordinatorLayout
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Función para realizar el logout
    private fun performLogout() {
        auth.signOut()

        try {
            LoginManager.getInstance().logOut()
        } catch (e: Exception) {
            Log.e("OpcionesActivity", "Error al cerrar sesión de Facebook: ${e.message}")
        }

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Opcional: Manejar el botón de retroceso para cerrar el Bottom Sheet primero
    override fun onBackPressed() {
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            super.onBackPressed()
        }
    }
}