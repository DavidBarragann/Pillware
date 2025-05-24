package com.example.pillware.ui.historial

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pillware.databinding.FragmentHistorialBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HistorialFragment : Fragment() {

    private var _binding: FragmentHistorialBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var historialAdapter: HistorialMedicamentoAdapter
    private val listaMedicamentosHistorial = mutableListOf<Medicamento>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this).get(HistorialViewModel::class.java)

        _binding = FragmentHistorialBinding.inflate(inflater, container, false)
        val root: View = binding.root

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.recyclerViewHistorial.layoutManager = LinearLayoutManager(requireContext())
        historialAdapter = HistorialMedicamentoAdapter(listaMedicamentosHistorial)
        binding.recyclerViewHistorial.adapter = historialAdapter

        // Configurar botón de retroceso
        binding.historialBackButton.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        // Cargar medicamentos desde Firestore
        loadMedicamentosHistorial()

        return root
    }

    private fun loadMedicamentosHistorial() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            if (isAdded) {
                Toast.makeText(requireContext(), "Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val userUid = currentUser.uid
        db.collection("Perfil").document(userUid).collection("Medicamentos")
            .get() // Usar get() para obtener los datos una vez
            .addOnSuccessListener { querySnapshot ->
                val newMedicamentos = mutableListOf<Medicamento>()
                for (document in querySnapshot.documents) {
                    val id = document.id
                    val nombreMed = document.getString("Nombre") ?: ""
                    val horariosList = document.get("Horas") as? List<String> ?: emptyList()
                    val dosis = document.getString("Dosis") ?: ""
                    val detalles = document.getString("Detalles") ?: ""

                    val medicamento = Medicamento(id, nombreMed, horariosList, dosis, detalles, false) // isTaken puede no ser relevante aquí
                    newMedicamentos.add(medicamento)
                }
                historialAdapter.updateMedicamentos(newMedicamentos)
                Log.d("HistorialFragment", "Medicamentos cargados: ${newMedicamentos.size}")
                if (newMedicamentos.isEmpty() && isAdded) {
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistorialFragment", "Error al cargar medicamentos del historial: ${e.message}", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error al cargar historial: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}