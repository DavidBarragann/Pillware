package com.example.pillware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat // Para ActivityCompat.checkSelfPermission

class AgregarMedicamentoActivity : AppCompatActivity() {

    private lateinit var editNombre: EditText
    private lateinit var editDosis: EditText
    private lateinit var editDetalles: EditText
    private lateinit var btnGuardar: Button
    private lateinit var btnAddHora: Button
    private lateinit var containerHorarios: LinearLayout
    private lateinit var btnAtras: ImageView

    private val listaHorarios = mutableListOf<String>()

    private val REQUEST_CODE_SCHEDULE_EXACT_ALARM = 123
    private val REQUEST_CODE_POST_NOTIFICATIONS = 124 // Nuevo request code

    // Lanzador de resultados para el permiso de notificaciones (Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permiso de notificaciones concedido.", Toast.LENGTH_SHORT).show()
            // Intentar guardar y programar alarmas de nuevo
            trySaveAndScheduleAlarms()
        } else {
            Toast.makeText(this, "Permiso de notificaciones denegado. No podrás recibir recordatorios.", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agregar_medicamento)

        editNombre = findViewById(R.id.editNombre)
        editDosis = findViewById(R.id.editDosis)
        editDetalles = findViewById(R.id.editDetalles)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnAddHora = findViewById(R.id.btnAddHora)
        containerHorarios = findViewById(R.id.containerHorarios)
        btnAtras = findViewById(R.id.imageView5)

        btnAtras.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnAddHora.setOnClickListener {
            mostrarTimePickerDialog()
        }

        btnGuardar.setOnClickListener {
            // Llamamos a una función que encapsula la lógica de guardado
            trySaveAndScheduleAlarms()
        }
    }

    private fun trySaveAndScheduleAlarms() {
        val nombre = editNombre.text.toString().trim()
        val dosis = editDosis.text.toString().trim()
        val detalles = editDetalles.text.toString().trim()

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null && nombre.isNotEmpty() && listaHorarios.isNotEmpty()) {
            // 1. Verificar y solicitar permiso POST_NOTIFICATIONS para Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return // Salir y esperar el resultado del permiso
                }
            }

            // 2. Verificar y solicitar permiso SCHEDULE_EXACT_ALARM para Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivityForResult(intent, REQUEST_CODE_SCHEDULE_EXACT_ALARM)
                    Toast.makeText(this, "Por favor, otorga el permiso para programar alarmas exactas.", Toast.LENGTH_LONG).show()
                    return // Salir y esperar el resultado del permiso
                }
            }

            // Si todos los permisos están OK o no son necesarios para la versión de Android,
            // procedemos a guardar y programar las alarmas.
            guardarMedicamentoYProgramarAlarmas(uid, nombre, dosis, detalles, listaHorarios)

        } else {
            Toast.makeText(this, "Por favor, completa el nombre y agrega al menos una hora.", Toast.LENGTH_SHORT).show()
        }
    }


    @Suppress("DEPRECATION") // Para startActivityForResult en API 30+
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCHEDULE_EXACT_ALARM) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (alarmManager.canScheduleExactAlarms()) {
                    // Permiso de alarma concedido, intentar guardar y programar de nuevo
                    trySaveAndScheduleAlarms()
                } else {
                    Toast.makeText(this, "El permiso para programar alarmas exactas no fue concedido. Las alarmas pueden no funcionar correctamente.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun guardarMedicamentoYProgramarAlarmas(uid: String, nombre: String, dosis: String, detalles: String, horarios: List<String>) {
        val medicamento = hashMapOf(
            "Nombre" to nombre,
            "Horas" to horarios,
            "Dosis" to dosis,
            "Detalles" to detalles,
            "isTaken" to false // Inicializar como no tomado
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("Perfil").document(uid)
            .collection("Medicamentos")
            .add(medicamento)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Medicamento guardado", Toast.LENGTH_SHORT).show()
                val medicamentoId = documentReference.id
                for (hora in horarios) {
                    programarAlarma(nombre, hora, medicamentoId, uid) // ¡Pasar el UID aquí!
                }
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun mostrarTimePickerDialog() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            if (!listaHorarios.contains(formattedTime)) {
                listaHorarios.add(formattedTime)
                listaHorarios.sort()
                actualizarVistaHorarios()
            } else {
                Toast.makeText(this, "Esta hora ya ha sido agregada.", Toast.LENGTH_SHORT).show()
            }
        }, hour, minute, true)

        timePickerDialog.show()
    }

    private fun actualizarVistaHorarios() {
        containerHorarios.removeAllViews()

        for (hora in listaHorarios) {
            val horaView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
                    rightMargin = resources.getDimensionPixelSize(R.dimen.spacing_medium)
                }
                text = hora
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.chip_text_size))
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setBackgroundResource(R.drawable.chip_background)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.padding_horizontal),
                    resources.getDimensionPixelSize(R.dimen.padding_vertical),
                    resources.getDimensionPixelSize(R.dimen.padding_horizontal),
                    resources.getDimensionPixelSize(R.dimen.padding_vertical)
                )
                gravity = Gravity.CENTER
                setOnClickListener { view ->
                    listaHorarios.remove(hora)
                    actualizarVistaHorarios()
                    Toast.makeText(context, "Hora $hora eliminada.", Toast.LENGTH_SHORT).show()
                }
            }
            containerHorarios.addView(horaView)
        }
    }

    // ... (tu código existente en AgregarMedicamentoActivity)

    private fun programarAlarma(nombreMedicamento: String, hora: String, medicamentoId: String, userId: String) {
        val parts = hora.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Si la hora ya pasó hoy, programarla para mañana
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val requestCode = (medicamentoId + hora).hashCode()

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId) // Pasamos el userId
            putExtra("alarmType", "main_alarm") // Definimos el tipo de alarma inicial
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this, "Permiso para alarmas exactas no concedido. La alarma podría no programarse.", Toast.LENGTH_LONG).show()
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Toast.makeText(this, "Alarma para $nombreMedicamento a las $hora programada.", Toast.LENGTH_SHORT).show()
    }
}