package com.example.pillware.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.example.pillware.R
import com.example.pillware.databinding.FragmentHomeBinding
import com.example.pillware.opciones
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.pillware.AgregarMedicamentoActivity
import com.example.pillware.CorreoHelper
import com.example.pillware.NotificationsActivity // Asegúrate de que esta actividad exista
import com.example.pillware.RecordatorioWorker // Asegúrate de que esta clase exista
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MedicamentoAdapter
    private val listaMedicamentosOriginal = mutableListOf<Medicamento>()
    private val listaMedicamentosFiltrada = mutableListOf<Medicamento>()
    private lateinit var textViewNombreUsuario: TextView
    private lateinit var textViewMensajeCompletarPerfil: TextView

    private var textWatcherAttached = false

    // Constante para el prefijo de las tags de WorkManager
    private val WORK_TAG_PREFIX = "MedicamentoReminder_"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val perfilLayout: LinearLayout = binding.perfil
        perfilLayout.setOnClickListener {
            val intent = Intent(requireContext(), opciones::class.java)
            startActivity(intent)
        }

        val addMedButton: LinearLayout = binding.addMedButton
        addMedButton.setOnClickListener {
            val intent = Intent(requireContext(), AgregarMedicamentoActivity::class.java)
            startActivity(intent)
        }

        // Configuración del TextWatcher para la búsqueda
        if (!textWatcherAttached) {
            binding.searchText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (isAdded && _binding != null) {
                        val query = s.toString().trim()
                        filterMedicamentos(query)
                    }
                }
            })
            textWatcherAttached = true
        }

        binding.searchIcon.setOnClickListener {
            _binding?.searchText?.setText("")
        }

        val notificationsButton: View = binding.notifications
        notificationsButton.setOnClickListener {
            val intent = Intent(requireContext(), NotificationsActivity::class.java)
            startActivity(intent)
        }

        textViewNombreUsuario = root.findViewById(R.id.nombreusuario)
        textViewMensajeCompletarPerfil = root.findViewById(R.id.mensaje_completar)

        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userEmail = currentUser.email
            val userUid = currentUser.uid
            Log.d("HomeFragment", "Usuario logueado: UID = $userUid, Email = $userEmail")

            db.collection("Perfil").document(userUid)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val nombre = documentSnapshot.getString("nombre")
                        if (!nombre.isNullOrEmpty()) {
                            Log.d("HomeFragment", "Nombre del perfil: $nombre")
                            textViewNombreUsuario.text = nombre
                            textViewMensajeCompletarPerfil.visibility = View.GONE
                        } else {
                            Log.d("HomeFragment", "El nombre del perfil es nulo o vacío.")
                            textViewNombreUsuario.text = getString(R.string.usuario_desconocido)
                            textViewMensajeCompletarPerfil.text = getString(R.string.mensaje_completar)
                            textViewMensajeCompletarPerfil.visibility = View.VISIBLE
                        }
                    } else {
                        Log.d("HomeFragment", "El documento del perfil no existe para el UID: $userUid")
                        textViewNombreUsuario.text = getString(R.string.usuario_desconocido)
                        textViewMensajeCompletarPerfil.text = getString(R.string.mensaje_completar)
                        textViewMensajeCompletarPerfil.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Error al obtener el perfil del usuario: ${e.message}", e)
                    textViewNombreUsuario.text = getString(R.string.usuario_desconocido)
                    textViewMensajeCompletarPerfil.text = getString(R.string.error_cargar_perfil)
                    textViewMensajeCompletarPerfil.visibility = View.VISIBLE
                }

            binding.recyclerViewMedicamentos.layoutManager = LinearLayoutManager(requireContext())
            adapter = MedicamentoAdapter(
                listaMedicamentosFiltrada, // Usamos la lista filtrada para el adaptador
                onCheckClickListener = { medicamento, position ->
                    toggleMedicamentoTomado(medicamento)
                },
                onDeleteClickListener = { medicamento, position ->
                    eliminarMedicamento(medicamento)
                }
            )
            binding.recyclerViewMedicamentos.adapter = adapter

            db.collection("Perfil").document(userUid).collection("Medicamentos")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("HomeFragment", "Error escuchando cambios en medicamentos.", e)
                        return@addSnapshotListener
                    }

                    listaMedicamentosOriginal.clear() // Limpiamos la lista original
                    snapshots?.forEach { document ->
                        val id = document.id
                        val nombreMed = document.getString("Nombre") ?: ""
                        val horariosList = document.get("Horas") as? List<String> ?: emptyList()
                        val dosis = document.getString("Dosis") ?: ""
                        val detalles = document.getString("Detalles") ?: ""
                        val isTaken = document.getBoolean("isTaken") ?: false

                        val medicamento = Medicamento(id, nombreMed, horariosList, dosis, detalles, isTaken)
                        listaMedicamentosOriginal.add(medicamento)
                    }
                    if (isAdded) { // Aplicamos el filtro actual al obtener los datos
                        applyCurrentFilter()
                        programarTodosLosRecordatorios() // Reprogramar al obtener nuevos datos
                    }
                    Log.d("HomeFragment", "Lista de medicamentos actualizada por SnapshotListener, total: ${listaMedicamentosOriginal.size}")
                }

        } else {
            Log.d("HomeFragment", "Ningún usuario logueado.")
            textViewNombreUsuario.text = ""
            textViewMensajeCompletarPerfil.visibility = View.GONE
            listaMedicamentosOriginal.clear()
            listaMedicamentosFiltrada.clear()
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
        }

        return root
    }

    // --- Funciones para manejar la búsqueda ---
    private fun filterMedicamentos(query: String) {
        listaMedicamentosFiltrada.clear()
        if (query.isEmpty()) {
            listaMedicamentosFiltrada.addAll(listaMedicamentosOriginal)
        } else {
            val lowerCaseQuery = query.toLowerCase(Locale.getDefault())
            listaMedicamentosOriginal.forEach { medicamento ->
                if (medicamento.nombre.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    listaMedicamentosFiltrada.add(medicamento)
                }
            }
        }
        adapter.notifyDataSetChanged()
        if (listaMedicamentosFiltrada.isEmpty() && query.isNotEmpty() && isAdded) {
            Toast.makeText(requireContext(), "No se encontraron medicamentos para '$query'", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para aplicar el filtro actual cada vez que la lista original cambia
    private fun applyCurrentFilter() {
        val currentQuery = _binding?.searchText?.text.toString().trim()
        filterMedicamentos(currentQuery)
    }

    // --- Funciones para manejar los eventos de los botones de medicamento ---

    private fun toggleMedicamentoTomado(medicamento: Medicamento) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            if (isAdded) Toast.makeText(requireContext(), "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }
        val newIsTakenState = !medicamento.isTaken

        db.collection("Perfil").document(uid).collection("Medicamentos").document(medicamento.id)
            .update("isTaken", newIsTakenState)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "${medicamento.nombre} marcado como ${if (newIsTakenState) "tomado" else "no tomado"}", Toast.LENGTH_SHORT).show()

                if (newIsTakenState && auth.currentUser?.email != null) {
                    val mensaje = "¡Has tomado tu ${medicamento.nombre} a las ${medicamento.horario.firstOrNull() ?: ""}!"
                    CorreoHelper.enviarCorreo(requireContext(), mensaje, auth.currentUser!!.email!!)
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) Toast.makeText(requireContext(), "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error al actualizar estado del medicamento: ${e.message}", e)
            }
    }

    private fun eliminarMedicamento(medicamento: Medicamento) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            if (isAdded) Toast.makeText(requireContext(), "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Perfil").document(uid).collection("Medicamentos").document(medicamento.id)
            .delete()
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "${medicamento.nombre} eliminado.", Toast.LENGTH_SHORT).show()
                val workManager = context?.let { WorkManager.getInstance(it) }
                medicamento.horario.forEach { hora ->
                    val uniqueWorkTag = "$WORK_TAG_PREFIX${medicamento.id}_$hora"
                    workManager?.cancelAllWorkByTag(uniqueWorkTag)
                    Log.d("WorkManager", "Cancelled work for deleted medication: $uniqueWorkTag")
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error al eliminar medicamento: ${e.message}", e)
            }
    }

    // --- FUNCIONES MEJORADAS PARA WORKMANAGER ---

    private fun programarVerificacionMedicamento(medicamento: Medicamento, horario: String) {
        val currentContext = context
        if (currentContext == null) {
            Log.e(
                "WorkManager",
                "Contexto es nulo, no se puede programar WorkManager para ${medicamento.nombre}."
            )
            return
        }

        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        try {
            val horaProgramada = formatoHora.parse(horario)
            val ahora = Calendar.getInstance()

            val horaProxima = Calendar.getInstance().apply {
                time = horaProgramada!!
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Si la hora programada ya pasó hoy, programarla para el día siguiente
                if (before(ahora)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                // Suma 1 minuto a la hora programada para el recordatorio de verificación.
                add(Calendar.MINUTE, 1)
            }

            // Calcula el retraso en milisegundos desde ahora hasta la HORA PROGRAMADA + 1 MINUTO
            val delayMillis = horaProxima.timeInMillis - System.currentTimeMillis()

            // Asegurarse de que el retraso no sea negativo si la hora ya pasó hace poco.
            val finalDelayMillis = if (delayMillis < 0) {
                val proximaHoraManana = Calendar.getInstance().apply {
                    time = horaProgramada!!
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1) // Pasa al día siguiente
                    add(Calendar.MINUTE, 1) // Añade el minuto de retraso
                }
                proximaHoraManana.timeInMillis - System.currentTimeMillis()
            } else {
                delayMillis
            }

            val uniqueWorkTag = "$WORK_TAG_PREFIX${medicamento.id}_$horario"

            WorkManager.getInstance(currentContext).cancelAllWorkByTag(uniqueWorkTag)
            Log.d("WorkManager", "Cancelado trabajo existente con tag: $uniqueWorkTag")

            val datos = Data.Builder()
                .putString("medicamentoId", medicamento.id)
                .putString("nombreMedicamento", medicamento.nombre)
                .putString("userUid", auth.currentUser?.uid)
                .putString("userEmail", auth.currentUser?.email)
                .putString("horaProgramada", horario)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<RecordatorioWorker>()
                .setInitialDelay(finalDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(datos)
                .addTag(uniqueWorkTag)
                .build()

            WorkManager.getInstance(currentContext).enqueue(workRequest)

            val scheduledTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(horaProxima.time)
            Log.d(
                "WorkManager",
                "Recordatorio programado para ${medicamento.nombre} a las $scheduledTime (1 minuto después de ${horario}). (Tag: $uniqueWorkTag)"
            )
        } catch (e: Exception) {
            Log.e(
                "WorkManager",
                "Error al programar recordatorio para ${medicamento.nombre}: ${e.message}",
                e
            )
        }
    }

    private fun programarTodosLosRecordatorios() {
        listaMedicamentosOriginal.forEach { medicamento -> // Usamos la lista original para programar
            medicamento.horario.forEach { hora ->
                programarVerificacionMedicamento(medicamento, hora)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        textWatcherAttached = false // Restablecer la bandera
    }
}