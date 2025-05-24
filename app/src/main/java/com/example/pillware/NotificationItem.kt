package com.example.pillware.data

import java.util.Date

data class NotificationItem(
    val id: String,
    val tipo: NotificationType,
    val titulo: String,
    val mensaje: String,
    val medicamentoNombre: String?, // Puede ser nulo si no aplica
    val fechaHora: Date,
    val iconoResId: Int // Resource ID for the drawable icon
)

enum class NotificationType {
    PROXIMA_TOMA,
    TOMA_COMPLETADA,
    RECORDATORIO
}