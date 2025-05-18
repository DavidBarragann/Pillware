package com.example.pillware.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pillware.R
import com.example.pillware.databinding.FragmentHomeBinding
import com.example.pillware.opciones
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MedicamentoAdapter
    private val listaMedicamentos = mutableListOf<Medicamento>()
    private lateinit var textViewNombreUsuario: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // --- Establecer el Listener para el LinearLayout 'perfil' ---
        val perfilLayout: LinearLayout = binding.perfil // Acceder al LinearLayout por su ID
        perfilLayout.setOnClickListener {
            val intent = Intent(requireContext(), opciones::class.java)
            startActivity(intent)
        }

        textViewNombreUsuario = root.findViewById(R.id.nombreusuario) // Asegúrate de tener este ID en tu XML

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
                        } else {
                            Log.d("HomeFragment", "El nombre del perfil es nulo o vacío.")
                        }
                    } else {
                        Log.d("HomeFragment", "El documento del perfil no existe para el UID: $userUid")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Error al obtener el perfil del usuario: ${e.message}", e)
                }

            // Inicializar el RecyclerView
            binding.recyclerViewMedicamentos.layoutManager = LinearLayoutManager(requireContext())
            adapter = MedicamentoAdapter(listaMedicamentos)
            binding.recyclerViewMedicamentos.adapter = adapter
            db.collection("Medicamento").addSnapshotListener { snapshots, _ ->
                listaMedicamentos.clear()
                snapshots?.forEach {
                    val nombreMed = it.getString("Nombre") ?: ""
                    val horario = it.getString("Hora") ?: ""
                    val capsulas = it.getString("Dosis") ?:""
                    val medicamento = Medicamento(nombreMed, horario, capsulas)
                    listaMedicamentos.add(medicamento)
                }
                adapter.notifyDataSetChanged()
            }

        } else {
            Log.d("HomeFragment", "Ningún usuario logueado.")
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}