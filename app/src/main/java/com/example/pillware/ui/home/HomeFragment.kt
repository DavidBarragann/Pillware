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
import com.example.pillware.R
import com.example.pillware.databinding.FragmentHomeBinding
import com.example.pillware.opciones
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.pillware.AgregarMedicamentoActivity
import com.example.pillware.CorreoHelper
import com.example.pillware.NotificationsActivity
import java.util.Locale

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

        if (!textWatcherAttached) {
            binding.searchText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    // Asegúrate de que el fragmento está adjunto y el binding no es nulo antes de filtrar
                    if (isAdded && _binding != null) {
                        val query = s.toString().trim()
                        filterMedicamentos(query)
                    }
                }
            })
            textWatcherAttached = true
        }

        binding.searchIcon.setOnClickListener {
            // Asegúrate de que el binding no sea nulo antes de limpiar
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
                listaMedicamentosFiltrada,
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

                    listaMedicamentosOriginal.clear()
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
                    // Antes de llamar a applyCurrentFilter(), verifica si el fragmento está adjunto
                    if (isAdded) {
                        applyCurrentFilter()
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
        // *** CAMBIO CLAVE AQUÍ: Verificar si el fragmento está adjunto antes de mostrar Toast ***
        if (listaMedicamentosFiltrada.isEmpty() && query.isNotEmpty() && isAdded) {
            Toast.makeText(requireContext(), "No se encontraron medicamentos para '$query'", Toast.LENGTH_SHORT).show()
        }
    }

    // Función para aplicar el filtro actual cada vez que la lista original cambia
    private fun applyCurrentFilter() {
        // Ya no necesitamos la comprobación de _binding aquí porque el isAdded en el listener lo filtra
        val currentQuery = _binding?.searchText?.text.toString().trim()
        filterMedicamentos(currentQuery)
    }

    // --- Funciones para manejar los eventos de los botones de medicamento ---

    private fun toggleMedicamentoTomado(medicamento: Medicamento) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Verifica si el fragmento está adjunto antes de mostrar Toast
            if (isAdded) Toast.makeText(requireContext(), "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }
        val newIsTakenState = !medicamento.isTaken

        db.collection("Perfil").document(uid).collection("Medicamentos").document(medicamento.id)
            .update("isTaken", newIsTakenState)
            .addOnSuccessListener {
                // Verifica si el fragmento está adjunto antes de mostrar Toast
                if (isAdded) Toast.makeText(requireContext(), "${medicamento.nombre} marcado como ${if (newIsTakenState) "tomado" else "no tomado"}", Toast.LENGTH_SHORT).show()

                // Si se marca como tomado y el usuario tiene email, enviar correo de confirmación
                if (newIsTakenState && auth.currentUser?.email != null) {
                    val mensaje = "¡Has tomado tu ${medicamento.nombre} a las ${medicamento.horario.firstOrNull() ?: ""}!"
                    CorreoHelper.enviarCorreo(requireContext(), mensaje, auth.currentUser!!.email!!)
                }
            }
            .addOnFailureListener { e ->
                // Verifica si el fragmento está adjunto antes de mostrar Toast
                if (isAdded) Toast.makeText(requireContext(), "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error al actualizar estado del medicamento: ${e.message}", e)
            }
    }

    private fun eliminarMedicamento(medicamento: Medicamento) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Verifica si el fragmento está adjunto antes de mostrar Toast
            if (isAdded) Toast.makeText(requireContext(), "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Perfil").document(uid).collection("Medicamentos").document(medicamento.id)
            .delete()
            .addOnSuccessListener {
                // Verifica si el fragmento está adjunto antes de mostrar Toast
                if (isAdded) Toast.makeText(requireContext(), "${medicamento.nombre} eliminado.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Verifica si el fragmento está adjunto antes de mostrar Toast
                if (isAdded) Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error al eliminar medicamento: ${e.message}", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cuando la vista del fragmento es destruida, limpia el binding
        _binding = null
        textWatcherAttached = false // Restablecer la bandera
    }
}