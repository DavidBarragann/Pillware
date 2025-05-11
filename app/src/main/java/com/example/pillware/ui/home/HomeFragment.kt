package com.example.pillware.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pillware.databinding.FragmentHomeBinding
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MedicamentoAdapter
    private val listaMedicamentos = mutableListOf<Medicamento>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Iniciar Firebase Firestore
        db = FirebaseFirestore.getInstance()

        // Inicializar el RecyclerView
        binding.recyclerViewMedicamentos.layoutManager = LinearLayoutManager(requireContext())
        adapter = MedicamentoAdapter(listaMedicamentos)
        binding.recyclerViewMedicamentos.adapter = adapter

        // Escuchar los cambios en la base de datos de Firebase
        db.collection("Medicamento").addSnapshotListener { snapshots, _ ->
            listaMedicamentos.clear()
            snapshots?.forEach {
                val nombre = it.getString("Nombre") ?: ""
                val horario = it.getString("Hora") ?: ""
                val capsulas = it.getString("Dosis") ?:""
                val medicamento = Medicamento(nombre, horario,capsulas)
                listaMedicamentos.add(medicamento)
            }
            adapter.notifyDataSetChanged()
        }

        return root
    }
}
