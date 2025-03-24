package com.example.pillware
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: ArrayAdapter<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)//CAMBIARESTO
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //código empieza aquí.
        db = FirebaseFirestore.getInstance()
        val input = findViewById<EditText>(R.id.notaInput)
        val btn = findViewById<Button>(R.id.btnGuardar)
        val listView = findViewById<ListView>(R.id.listaNotas)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        btn.setOnClickListener {
            val texto = input.text.toString()
            if (texto.isNotEmpty()) {
                val nota = hashMapOf("texto" to texto)
                db.collection("notas").add(nota)
                input.text.clear()
            }
        }

        db.collection("notas").addSnapshotListener { snapshots, _ ->
            val lista = mutableListOf<String>()
            snapshots?.forEach {
                lista.add(it.getString("texto") ?: "")
            }
            adapter.clear()
            adapter.addAll(lista)
        }
    }
}