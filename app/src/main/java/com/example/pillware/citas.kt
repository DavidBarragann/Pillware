package com.example.pillware

import com.google.firebase.firestore.DocumentId

data class Cita(
    @DocumentId
    var id: String? = null, // ID único de la cita asignado por Firestore
    val nombreCita: String = "", // Nuevo campo: Nombre de la Cita
    val horarios: List<String> = emptyList(), // Nuevo campo: Lista de horarios
    val fecha: String = "", // Nuevo campo: Fecha de la cita (YYYY-MM-DD)
    val indicaciones: String = "" // Nuevo campo: Indicaciones importantes
)