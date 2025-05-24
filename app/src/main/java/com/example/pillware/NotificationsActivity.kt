package com.example.pillware

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pillware.data.NotificationItem
import com.example.pillware.data.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationsActivity : AppCompatActivity() {

    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var notificationsAdapter: NotificationsAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView)
        notificationsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Configurar el botón de retroceso
        findViewById<ImageView>(R.id.backbtnnoti).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("NotificationsActivity", "User not logged in.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("Perfil").document(userId)
                    .collection("Notificaciones")
                    .orderBy("timestamp", Query.Direction.DESCENDING) // Ordenar por fecha descendente
                    .get()
                    .await()

                val notificationList = mutableListOf<NotificationItem>()
                for (document in snapshot.documents) {
                    val id = document.id
                    val tipo = document.getString("tipo")?.let { NotificationType.valueOf(it) } ?: NotificationType.RECORDATORIO
                    val titulo = document.getString("titulo") ?: ""
                    val mensaje = document.getString("mensaje") ?: ""
                    val medicamentoNombre = document.getString("medicamentoNombre")
                    val timestamp = document.getTimestamp("timestamp")?.toDate() ?: Date()
                    val iconoResId = document.getLong("iconoResId")?.toInt() ?: R.drawable.baseline_warning_24 // Icono por defecto

                    notificationList.add(NotificationItem(id, tipo, titulo, mensaje, medicamentoNombre, timestamp, iconoResId))
                }

                withContext(Dispatchers.Main) {
                    val groupedNotifications = groupNotificationsByDate(notificationList)
                    notificationsAdapter = NotificationsAdapter(groupedNotifications)
                    notificationsRecyclerView.adapter = notificationsAdapter
                }

            } catch (e: Exception) {
                Log.e("NotificationsActivity", "Error loading notifications: ${e.message}", e)
                // Mostrar un Toast o un mensaje de error al usuario
            }
        }
    }

    private fun groupNotificationsByDate(notifications: List<NotificationItem>): List<Any> {
        val groupedList = mutableListOf<Any>()
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        val yesterday = calendar.apply { add(Calendar.DAY_OF_YEAR, -1) }.get(Calendar.DAY_OF_YEAR)

        val dateFormatDayMonth = SimpleDateFormat("d MMMM", Locale.getDefault())
        val dateFormatTime = SimpleDateFormat("H:mm", Locale.getDefault())

        var lastDate: String? = null

        for (notification in notifications) {
            calendar.time = notification.fechaHora
            val notificationDay = calendar.get(Calendar.DAY_OF_YEAR)

            val headerText = when (notificationDay) {
                today -> "Hoy"
                yesterday -> "Ayer"
                else -> dateFormatDayMonth.format(notification.fechaHora)
            }

            if (headerText != lastDate) {
                groupedList.add(headerText) // Añadir cabecera de fecha
                lastDate = headerText
            }

            // Calcular el tiempo transcurrido para mostrar "2 M", "3 H", "1 D"
            val diffInMillis = System.currentTimeMillis() - notification.fechaHora.time
            val timeAgo = when {
                diffInMillis < TimeUnit.MINUTES.toMillis(60) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
                    if (minutes <= 1) "Ahora" else "$minutes M"
                }
                diffInMillis < TimeUnit.HOURS.toMillis(24) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
                    "$hours H"
                }
                else -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                    "$days D"
                }
            }
            // Puedes modificar el NotificationItem para incluir este campo temporal si es necesario
            // O pasarlo directamente al adaptador/ViewHolder
            groupedList.add(Pair(notification, timeAgo)) // Agrupar notificación con su "tiempo hace"
        }
        return groupedList
    }

    // --- Adaptador para el RecyclerView ---
    class NotificationsAdapter(private val items: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_HEADER = 0
        private val VIEW_TYPE_NOTIFICATION = 1

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is String) VIEW_TYPE_HEADER else VIEW_TYPE_NOTIFICATION
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_group_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
                NotificationViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder.itemViewType) {
                VIEW_TYPE_HEADER -> {
                    val headerText = items[position] as String
                    (holder as HeaderViewHolder).bind(headerText)
                }
                VIEW_TYPE_NOTIFICATION -> {
                    val (notification, timeAgo) = items[position] as Pair<NotificationItem, String>
                    (holder as NotificationViewHolder).bind(notification, timeAgo)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        // ViewHolder para las cabeceras de fecha
        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val headerDateTextView: TextView = itemView.findViewById(R.id.headerDateTextView)

            fun bind(date: String) {
                headerDateTextView.text = date
            }
        }

        // ViewHolder para los ítems de notificación
        class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val notificationIcon: ImageView = itemView.findViewById(R.id.notificationIcon)
            private val notificationTitle: TextView = itemView.findViewById(R.id.notificationTitle)
            private val notificationMessage: TextView = itemView.findViewById(R.id.notificationMessage)
            private val notificationTime: TextView = itemView.findViewById(R.id.notificationTime)

            fun bind(notification: NotificationItem, timeAgo: String) {
                notificationIcon.setImageResource(notification.iconoResId)
                notificationTitle.text = notification.titulo
                notificationMessage.text = notification.mensaje
                notificationTime.text = timeAgo

                // Opcional: Cambiar el fondo del icono o el color del texto según el tipo de notificación
                when (notification.tipo) {
                    NotificationType.PROXIMA_TOMA -> {
                        notificationIcon.setBackgroundResource(R.drawable.circle_blue_background)
                        notificationIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white))
                    }
                    NotificationType.TOMA_COMPLETADA -> {
                        // Podrías tener otro color o drawable para "completado"
                        notificationIcon.setBackgroundResource(R.drawable.circle_green_background)
                        notificationIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white))
                    }
                    NotificationType.RECORDATORIO -> {
                        // Otro color para "recordatorio"
                        notificationIcon.setBackgroundResource(R.drawable.circle_orange_background)
                        notificationIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white))
                    }
                }
            }
        }
    }
}