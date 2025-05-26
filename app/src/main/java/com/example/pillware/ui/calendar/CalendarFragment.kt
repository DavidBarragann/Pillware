package com.example.pillware.ui.calendar

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.Cita
import com.example.pillware.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var monthText: TextView
    private lateinit var recyclerViewDates: RecyclerView
    private lateinit var dateAdapter: DateAdapter
    private lateinit var patientNameText: TextView
    private lateinit var appointmentTimeText: TextView
    private lateinit var importantIndicationsText: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val calendar = Calendar.getInstance()
    private var selectedDatePosition: Int = -1 // <-- Esta es la variable correcta
    private var currentSelectedDate: DateItem? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        monthText = view.findViewById(R.id.month_text)
        recyclerViewDates = view.findViewById(R.id.recycler_view_dates)
        patientNameText = view.findViewById(R.id.patient_name_text)
        appointmentTimeText = view.findViewById(R.id.appointment_time_text)
        importantIndicationsText = view.findViewById(R.id.important_indications_text)

        recyclerViewDates.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        dateAdapter = DateAdapter(::onDateSelected)
        recyclerViewDates.adapter = dateAdapter

        view.findViewById<ImageView>(R.id.back_arrow).setOnClickListener {
            activity?.onBackPressed()
        }

        view.findViewById<LinearLayout>(R.id.add_cita_button)?.setOnClickListener {
            val intent = android.content.Intent(requireContext(), com.example.pillware.agregar_cita::class.java)
            startActivity(intent)
        }

        view.findViewById<ImageView>(R.id.search_icon).setOnClickListener {
            Toast.makeText(context, "Funcionalidad de búsqueda en desarrollo", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<LinearLayout>(R.id.month_selector_layout).setOnClickListener {
            showMonthYearPicker()
        }

        updateCalendarDates()
    }

    override fun onResume() {
        super.onResume()
        currentSelectedDate?.let {
            // Pasamos la posición guardada, que debería ser selectedDatePosition
            onDateSelected(it, selectedDatePosition)
        } ?: run {
            val todayIndex = dateAdapter.currentList.indexOfFirst { it.isToday }
            if (todayIndex != -1) {
                onDateSelected(dateAdapter.currentList[todayIndex], todayIndex)
            }
        }
    }

    private fun updateCalendarDates() {
        val dates = mutableListOf<DateItem>()
        val tempCalendar = Calendar.getInstance()
        tempCalendar.time = calendar.time

        tempCalendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK)
        val daysBefore = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

        val prevMonthCalendar = tempCalendar.clone() as Calendar
        prevMonthCalendar.add(Calendar.MONTH, -1)
        val maxDaysInPrevMonth = prevMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in daysBefore downTo 0) {
            val day = maxDaysInPrevMonth - i
            val date = prevMonthCalendar.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, day)
            dates.add(DateItem(date.time, isToday = false))
        }

        val maxDaysInMonth = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDaysInMonth) {
            val date = tempCalendar.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, i)
            val isToday = isSameDay(date, Calendar.getInstance())
            dates.add(DateItem(date.time, isToday))
            if (isToday && selectedDatePosition == -1) {
                selectedDatePosition = dates.size - 1
            }
        }

        val nextMonthCalendar = tempCalendar.clone() as Calendar
        nextMonthCalendar.add(Calendar.MONTH, 1)
        var dayCounter = 1
        while (dates.size < 42 && dates.size % 7 != 0) {
            val date = nextMonthCalendar.clone() as Calendar
            date.set(Calendar.DAY_OF_MONTH, dayCounter++)
            dates.add(DateItem(date.time, isToday = false))
        }

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
        monthText.text = monthFormat.format(calendar.time).replaceFirstChar { it.uppercase() }

        dateAdapter.submitList(dates) {
            if (selectedDatePosition != -1) {
                recyclerViewDates.scrollToPosition(selectedDatePosition)
                dateAdapter.setSelectedPosition(selectedDatePosition)
                dateAdapter.notifyItemChanged(selectedDatePosition)
                currentSelectedDate = dates[selectedDatePosition]
                loadAppointmentsForSelectedDate(currentSelectedDate!!.date)
            } else {
                updateAppointmentDetails(null)
            }
        }
    }

    // CORRECCIÓN AQUÍ: Usar selectedDatePosition
    private fun onDateSelected(dateItem: DateItem, position: Int) {
        currentSelectedDate = dateItem

        if (selectedDatePosition != position) { // <-- Se usaba 'selectedPosition', ahora es 'selectedDatePosition'
            val oldSelectedPosition = selectedDatePosition // <-- Igual aquí
            selectedDatePosition = position // <-- Y aquí
            dateAdapter.setSelectedPosition(selectedDatePosition)

            if (oldSelectedPosition != RecyclerView.NO_POSITION) {
                dateAdapter.notifyItemChanged(oldSelectedPosition)
            }
            dateAdapter.notifyItemChanged(selectedDatePosition)

            loadAppointmentsForSelectedDate(dateItem.date)
        }
    }


    private fun loadAppointmentsForSelectedDate(selectedDate: Date) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "Usuario no autenticado para cargar citas.", Toast.LENGTH_SHORT).show()
            updateAppointmentDetails(null)
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(selectedDate)

        patientNameText.text = "Cargando citas..."
        appointmentTimeText.text = ""
        importantIndicationsText.text = ""
        view?.findViewById<LinearLayout>(R.id.appointment_details_layout)?.visibility = View.VISIBLE


        firestore.collection("Perfil").document(uid)
            .collection("Citas")
            .whereEqualTo("fecha", dateString)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val citasDelDia = mutableListOf<Cita>()
                for (document in querySnapshot.documents) {
                    val cita = document.toObject(Cita::class.java)
                    cita?.let {
                        citasDelDia.add(it)
                    }
                }
                updateAppointmentDetails(citasDelDia)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar citas: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CalendarFragment", "Error loading appointments", e)
                updateAppointmentDetails(emptyList())
            }
    }

    private fun updateAppointmentDetails(citas: List<Cita>?) {
        val appointmentDetailsLayout = view?.findViewById<LinearLayout>(R.id.appointment_details_layout)

        if (citas.isNullOrEmpty()) {
            patientNameText.text = "No hay citas para este día."
            appointmentTimeText.text = ""
            importantIndicationsText.text = ""
            appointmentDetailsLayout?.visibility = View.GONE
        } else {
            appointmentDetailsLayout?.visibility = View.VISIBLE
            val primeraCita = citas.first()
            patientNameText.text = primeraCita.nombreCita
            importantIndicationsText.text = primeraCita.indicaciones

            if (primeraCita.horarios.isNotEmpty()) {
                appointmentTimeText.text = primeraCita.horarios.joinToString(" - ")
            } else {
                appointmentTimeText.text = "Sin horario especificado"
            }
        }
    }


    private fun showAddAppointmentDialog() {
        val intent = android.content.Intent(requireContext(), com.example.pillware.agregar_cita::class.java)
        startActivity(intent)
    }

    private fun showMonthYearPicker() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(Calendar.YEAR, selectedYear)
            calendar.set(Calendar.MONTH, selectedMonth)
            updateCalendarDates()
        }, year, month, day)

        // Este código puede causar un crash en algunas versiones de Android o temas si el ID no existe.
        // Es mejor probarlo o usar una alternativa más robusta si se necesita.
        // Para simplemente seleccionar mes y año, un DatePickerDialog por defecto es suficiente si no se oculta el día.
        // Ocultar el día:
        // datePickerDialog.datePicker.findViewById<View>(resources.getIdentifier("android:id/day", null, null))?.visibility = View.GONE

        datePickerDialog.show()
    }


    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
}