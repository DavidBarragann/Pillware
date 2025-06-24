package com.example.pillware

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class AgregarMedicamentoActivity : AppCompatActivity() {

    private lateinit var editNombre: EditText
    private lateinit var editDosis: EditText
    private lateinit var editDetalles: EditText
    private lateinit var btnGuardar: Button
    private lateinit var btnAddHora: Button
    private lateinit var btnPrimeraToma: Button
    private lateinit var containerHorarios: LinearLayout
    private lateinit var btnAtras: ImageView
    private lateinit var radioGroupHorario: RadioGroup
    private lateinit var radioFijo: RadioButton
    private lateinit var radioDinamico: RadioButton
    private lateinit var btnFechaInicio: Button
    private lateinit var editCantidad: EditText
    private lateinit var spinnerUnidad: Spinner
    private lateinit var spinnerFrecuencia: Spinner
    private lateinit var primeraToma: TextView
    private lateinit var txtFechaInicio: TextView
    private lateinit var containerRepeticionAutomatica: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var frecuenciaValor: EditText

    private var fechaInicio: Calendar? = null

    private val listaHorarios = mutableListOf<String>()

    private val REQUEST_CODE_SCHEDULE_EXACT_ALARM = 123
    private val REQUEST_CODE_POST_NOTIFICATIONS = 124


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
        btnPrimeraToma = findViewById(R.id.btnSelectDynamicFirstTakeTime)
        containerHorarios = findViewById(R.id.containerHorarios)
        btnAtras = findViewById(R.id.imageView5)
        radioGroupHorario = findViewById(R.id.radioGroupHorario)
        radioFijo = findViewById(R.id.radioFijo)
        radioDinamico = findViewById(R.id.radioDinamico)
        btnFechaInicio = findViewById(R.id.btnFechaInicio)
        editCantidad = findViewById(R.id.editCantidad)
        containerRepeticionAutomatica = findViewById(R.id.containerRepeticionAutomatica)
        spinnerUnidad = findViewById(R.id.spinnerUnidad)
        spinnerFrecuencia = findViewById(R.id.spinnerFrecuenciaUnidad)
        primeraToma = findViewById(R.id.txtDynamicFirstTakeTime)
        txtFechaInicio = findViewById(R.id.txtFechaInicio)
        frecuenciaValor = findViewById(R.id.editFrecuenciaValor)

        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.unidades_medicamento,
            R.layout.spinner_item
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerUnidad.adapter = adapter
        btnPrimeraToma.setOnClickListener {
            timepickerDinamico()
        }
        btnFechaInicio.setOnClickListener {
            mostrarDatePickerDialog()
        }

        btnAtras.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnAddHora.setOnClickListener {
            mostrarTimePickerDialog()
        }
        createNotificactionChannel()
        btnGuardar.setOnClickListener {
            trySaveAndScheduleAlarms()
            scheduleNotification()
        }

        radioGroupHorario.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.radioDinamico) {
                containerHorarios.visibility = View.GONE
                btnAddHora.visibility = View.GONE
                containerRepeticionAutomatica.visibility = View.VISIBLE
            } else if (checkedId == R.id.radioFijo) {
                containerHorarios.visibility = View.VISIBLE
                btnAddHora.visibility=View.VISIBLE
                containerRepeticionAutomatica.visibility = View.GONE
            }
        }
    }

    //notificaciones desde firebase
    private fun trySaveAndScheduleAlarms() {
        val nombre = editNombre.text.toString().trim()
        val dosis = editDosis.text.toString().trim()
        val detalles = editDetalles.text.toString().trim()
        val esHorarioDinamico=radioDinamico.isChecked
        var horaPrim="0"

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (esHorarioDinamico==false){
            horaPrim=calcularSiguienteTomaGeneral("",esHorarioDinamico,listaHorarios=listaHorarios)
            if (uid != null && nombre.isNotEmpty() && listaHorarios.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        return // Salir y esperar el resultado del permiso
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        startActivityForResult(intent, REQUEST_CODE_SCHEDULE_EXACT_ALARM)
                        Toast.makeText(this, "Por favor, otorga el permiso para programar alarmas exactas.", Toast.LENGTH_LONG).show()
                        return
                    }
                }
            } else {
                Toast.makeText(this, "Por favor, completa el nombre y agrega al menos una hora.", Toast.LENGTH_SHORT).show()
            }
        }else{
            val frecuenciaValor = editCantidad.text.toString().toIntOrNull() ?: 1
            val frecuenciaUnidad = spinnerFrecuencia.selectedItem.toString()
            horaPrim=calcularSiguienteTomaGeneral(primeraToma.text.toString(),esHorarioDinamico,frecuenciaValor,frecuenciaUnidad)
            listaHorarios.add(horaPrim)
            if (uid != null && nombre.isNotEmpty()) {
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        return
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        startActivityForResult(intent, REQUEST_CODE_SCHEDULE_EXACT_ALARM)
                        Toast.makeText(this, "Por favor, otorga el permiso para programar alarmas exactas.", Toast.LENGTH_LONG).show()
                        return
                    }
                }
            }
        }
        guardarMedicamentoYProgramarAlarmas(uid!!, nombre, dosis, detalles, listaHorarios,horaPrim)
    }
    //notificaciones fijas
    private fun scheduleNotification()
    {
        val intent=Intent(applicationContext,Notification::class.java)
        val title= "Es hora de tomar tu medicamento"
        val message="Recuerda tomar tu medicamento "+ editNombre.text.toString()
        intent.putExtra(titleExtra,title)
        intent.putExtra(messageExtra,message)

        val pendingIntent= PendingIntent.getBroadcast(
            applicationContext,
            notificationID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager=getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private fun createNotificactionChannel()
    {
        val name="Notificaciones Med"
        val desc="A la hora de tomar tu medicamento"
        val importance=NotificationManager.IMPORTANCE_DEFAULT
        val chanel= NotificationChannel(channelID,name,importance)
        chanel.description=desc
        val notificationManager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(chanel)
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

    private fun guardarMedicamentoYProgramarAlarmas(uid: String, nombre: String, dosis: String, detalles: String, horarios: List<String>, siguienteToma: String) {
        val esHorarioDinamico = radioDinamico.isChecked
        val cantidad = editCantidad.text.toString().toIntOrNull() ?: 0 // Default a 0 si no es un número válido

        val medicamento = hashMapOf(
            "Nombre" to nombre,
            "Horas" to horarios,
            "Dosis" to dosis,
            "Detalles" to detalles,
            "isTaken" to false,
            "esHorarioDinamico" to esHorarioDinamico,
            "fechaInicio" to fechaInicio?.timeInMillis, // Guarda la fecha como timestamp (milisegundos)
            "cantidad" to cantidad,
            "siguienteToma" to siguienteToma
        )
            val db = FirebaseFirestore.getInstance()
            db.collection("Perfil").document(uid)
                .collection("Medicamentos")
                .add(medicamento)
                .addOnSuccessListener { documentReference ->
                    Toast.makeText(this, "Medicamento guardado", Toast.LENGTH_SHORT).show()
                    val medicamentoId = documentReference.id
                    for (hora in horarios) {
                        programarAlarma(nombre, hora, medicamentoId, uid, esHorarioDinamico, fechaInicio) // Pasa esHorarioDinamico y fechaInicio
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

    private fun timepickerDinamico(){
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            primeraToma.text = formattedTime
        }, hour, minute, true)
        timePickerDialog.show()
    }

    private fun mostrarDatePickerDialog() {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            fechaInicio = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
                txtFechaInicio.text = "${selectedDay}/${selectedMonth + 1}/${selectedYear}"
            }
        }, year, month, day)
        datePickerDialog.show()
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

    fun calcularSiguienteTomaGeneral(
        primeraTomaStr: String,
        esHorarioDinamico: Boolean,
        frecuenciaValor: Int = 1,
        frecuenciaUnidad: String = "horas",
        listaHorarios: List<String> = emptyList()
    ): String {
        val calendar = Calendar.getInstance()

        if (esHorarioDinamico) {
            val parts = primeraTomaStr.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            when (frecuenciaUnidad.lowercase()) {
                "horas" -> calendar.add(Calendar.HOUR_OF_DAY, frecuenciaValor)
                "días" -> calendar.add(Calendar.DAY_OF_YEAR, frecuenciaValor)
                "semanas" -> calendar.add(Calendar.WEEK_OF_YEAR, frecuenciaValor)
                else -> {
                    throw IllegalArgumentException("Unidad de frecuencia no reconocida: $frecuenciaUnidad")
                }
            }

            return if (frecuenciaUnidad.lowercase() == "horas") {
                // Solo hora y minutos
                String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
            } else {
                // Formato completo para días y semanas
                String.format(
                    "%04d-%02d-%02d %02d:%02d",
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,  // Calendar.MONTH es 0-based
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
                )
            }
        } else {
            val ahora = Calendar.getInstance()

            val siguiente = listaHorarios
                .mapNotNull {
                    val parts = it.split(":")
                    if (parts.size == 2) {
                        val h = parts[0].toIntOrNull()
                        val m = parts[1].toIntOrNull()
                        if (h != null && m != null) {
                            Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, m)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        } else null
                    } else null
                }
                .filter { it.after(ahora) }
                .minByOrNull { it.timeInMillis }

            val proxima = siguiente ?: run {
                val parts = listaHorarios.first().split(":")
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            return String.format("%02d:%02d", proxima.get(Calendar.HOUR_OF_DAY), proxima.get(Calendar.MINUTE))
        }
    }
    private fun programarAlarma(nombreMedicamento: String, hora: String, medicamentoId: String, userId: String, esHorarioDinamico: Boolean, fechaInicio: Calendar?) {
        val parts = hora.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()


        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Si hay una fecha de inicio, la usamos
            fechaInicio?.let {
                set(Calendar.YEAR, it.get(Calendar.YEAR))
                set(Calendar.MONTH, it.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, it.get(Calendar.DAY_OF_MONTH))
            } ?: run { // Si no hay fecha de inicio, programamos para hoy o mañana
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val requestCode = (medicamentoId + hora).hashCode()

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("nombre", nombreMedicamento)
            putExtra("hora", hora)
            putExtra("medicamentoId", medicamentoId)
            putExtra("userId", userId)
            putExtra("alarmType", if (esHorarioDinamico) "dynamic_alarm" else "fixed_alarm")
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