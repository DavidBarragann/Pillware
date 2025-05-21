package com.example.pillware.ui.home

import android.content.Intent
import android.os.Bundle
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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MedicamentoAdapter
    private val listaMedicamentos = mutableListOf<Medicamento>()
    private lateinit var textViewNombreUsuario: TextView
    private lateinit var textViewMensajeCompletarPerfil: TextView

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
                listaMedicamentos,
                onCheckClickListener = { medicamento, position ->
                    toggleMedicamentoTomado(medicamento) // Ya no pasamos 'position' aquí
                },
                onDeleteClickListener = { medicamento, position ->
                    eliminarMedicamento(medicamento) // Ya no pasamos 'position' aquí
                }
            )
            binding.recyclerViewMedicamentos.adapter = adapter

            db.collection("Perfil").document(userUid).collection("Medicamentos")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("HomeFragment", "Error escuchando cambios en medicamentos.", e)
                        return@addSnapshotListener
                    }

                    // *** ESTA ES LA PARTE CLAVE ***
                    // Limpia la lista y la reconstruye completamente con los datos más recientes de Firestore.
                    // Esto maneja automáticamente las adiciones, eliminaciones y modificaciones.
                    listaMedicamentos.clear()
                    snapshots?.forEach { document ->
                        val id = document.id // Obtener el ID del documento
                        val nombreMed = document.getString("Nombre") ?: ""
                        val horariosList = document.get("Horas") as? List<String> ?: emptyList()
                        val dosis = document.getString("Dosis") ?: ""
                        val detalles = document.getString("Detalles") ?: ""
                        val isTaken = document.getBoolean("isTaken") ?: false // Recuperar el estado 'isTaken'

                        val medicamento = Medicamento(id, nombreMed, horariosList, dosis, detalles, isTaken)
                        listaMedicamentos.add(medicamento)
                    }
                    adapter.notifyDataSetChanged() // Notifica al RecyclerView que los datos han cambiado por completo
                    Log.d("HomeFragment", "Lista de medicamentos actualizada por SnapshotListener, total: ${listaMedicamentos.size}")
                }

        } else {
            Log.d("HomeFragment", "Ningún usuario logueado.")
            textViewNombreUsuario.text = ""
            textViewMensajeCompletarPerfil.visibility = View.GONE
            listaMedicamentos.clear()
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
        }

        return root
    }

    // --- Funciones para manejar los eventos de los botones ---

    // Eliminamos 'position' del parámetro ya que el listener se encargará de la UI
    private fun toggleMedicamentoTomado(medicamento: Medicamento) {
        val newIsTakenState = !medicamento.isTaken
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Perfil").document(uid).collection("Medicamentos").document(medicamento.id)
            .update("isTaken", newIsTakenState)
            .addOnSuccessListener {
                // *** ELIMINAR ESTAS LÍNEAS ***
                // val updatedMedicamento = medicamento.copy(isTaken = newIsTakenState)
                // listaMedicamentos[position] = updatedMedicamento // ESTO CAUSA IndexOutOfBoundsException
                // adapter.notifyItemChanged(position) // Ya no es necesario aquí

                Toast.makeText(requireContext(), "${medicamento.nombre} marcado como ${if (newIsTakenState) "tomado" else "no tomado"}", Toast.LENGTH_SHORT).show()

                if (newIsTakenState && auth.currentUser?.email != null) {
                    val mensaje = "¡Has tomado tu ${medicamento.nombre} a las ${medicamento.horario.firstOrNull() ?: ""}!"
                    CorreoHelper.enviarCorreo(requireContext(), mensaje, auth.currentUser!!.email!!)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error al actualizar estado del medicamento: ${e.message}", e)
            }
    }

    // Eliminamos 'position' del parámetro ya que el listener se encargará de la UI
    private fun eliminarMedicamento(medicamento: Medicamento) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Perfil").document(uid).collection("Medicamentos").document(medicamento.id)
            .delete()
            .addOnSuccessListener {
                // *** ELIMINAR ESTAS LÍNEAS ***
                // listaMedicamentos.removeAt(position) // ESTO CAUSA IndexOutOfBoundsException
                // adapter.notifyItemRemoved(position) // Ya no es necesario aquí

                Toast.makeText(requireContext(), "${medicamento.nombre} eliminado.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error al eliminar medicamento: ${e.message}", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}