package com.example.pillware

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.res.colorResource
import androidx.core.content.ContextCompat
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Botón de retroceso
        val buttonback = findViewById<ImageView>(R.id.backarrowreg)
        buttonback.setOnClickListener {
            val intento = Intent(this, LoginActivity::class.java)
            startActivity(intento)
        }

        // Campo de Fecha de Nacimiento
        val fechaNacimientoEditText = findViewById<EditText>(R.id.fechanac_edittext)
        val color = ContextCompat.getColor(this, R.color.lightpurpletext)
        fechaNacimientoEditText.setTextColor(color)
        fechaNacimientoEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                fechaNacimientoEditText.setText(selectedDate)
            }, year, month, day)

            datePickerDialog.show()
        }
    }
}
