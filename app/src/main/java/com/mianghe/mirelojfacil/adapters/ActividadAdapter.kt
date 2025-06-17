package com.mianghe.mirelojfacil.adapters

import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mianghe.mirelojfacil.R
import com.mianghe.mirelojfacil.database.ActividadEntity
import java.time.LocalTime // Para manejar solo la hora
import java.time.format.DateTimeFormatter // Para formatear la hora
import java.util.Locale // Importar Locale

class ActividadAdapter(private var actividades: List<ActividadEntity>,
                       private var activeHour: LocalTime? = null) :
    RecyclerView.Adapter<ActividadAdapter.ActividadViewHolder>() {

    private val timeParser = DateTimeFormatter.ofPattern("HH:mm") // Parser para la hora de la API

    class ActividadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMensaje: TextView = itemView.findViewById(R.id.tv_item_mensaje)
        //val tvFecha: TextView = itemView.findViewById(R.id.tv_item_fecha) // ¡ACTUALIZADO ID!
        val tvHora: TextView = itemView.findViewById(R.id.tv_item_hora)     // ¡NUEVO ID!
        val itemLayout: LinearLayout = itemView.findViewById(R.id.item_root_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActividadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_actividad, parent, false)
        return ActividadViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActividadViewHolder, position: Int) {
        val actividad = actividades[position]
        val context = holder.itemView.context

        // Restablecer estilos por defecto (MUY IMPORTANTE para el reciclaje de vistas)
        holder.tvMensaje.typeface = Typeface.DEFAULT // Typeface.DEFAULT o DEFAULT_BOLD
        holder.tvHora.typeface = Typeface.DEFAULT // Typeface.DEFAULT o DEFAULT_BOLD
        holder.itemLayout.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.fondo_actividad)

        holder.tvMensaje.setTextColor(ContextCompat.getColor(context, R.color.gris_oscuro)) // Color original
        holder.tvHora.setTextColor(ContextCompat.getColor(context, R.color.gris_oscuro))     // Color original
        holder.itemLayout.background = ContextCompat.getDrawable(context, R.drawable.fondo_actividad)

        holder.tvMensaje.text = actividad.mensaje
        //holder.tvFecha.text = actividad.fechaAplicacion // La fecha se asigna directamente

        // Formatear la hora a HH:MM
        try {
            // Asumimos que actividad.horaAplicacion ya está en formato "HH:MM" (e.g., "22:00")
            // Si viene en otro formato (e.g., "21:18:00"), se puede parsear primero.
            // Para "HH:MM" directamente:
            val horaFormateada = actividad.horaAplicacion // Ya debería estar en HH:MM
            holder.tvHora.text = horaFormateada
        } catch (e: Exception) {
            // En caso de que el formato de hora no sea el esperado
            holder.tvHora.text = actividad.horaAplicacion // Muestra el valor original como fallback
        }
        // Lógica de resaltado
        activeHour?.let { currentActiveHour ->
            try {
                val itemHourParsed = LocalTime.parse(actividad.horaAplicacion, timeParser)

                // Comparar solo la HORA (sin minutos/segundos) ***
                // Obtener la hora actual redondeada al minuto cero (ej. 11:32 -> 11:00)
                val currentHourTruncated = currentActiveHour.withMinute(0).withSecond(0).withNano(0)
                // Obtener la hora del ítem redondeada al minuto cero (ej. 11:00 -> 11:00)
                val itemHourTruncated = itemHourParsed.withMinute(0).withSecond(0).withNano(0)

                // La actividad está activa si su hora (sin minutos) coincide con la hora actual (sin minutos)
                val isCurrentActivityActive = itemHourTruncated.equals(currentHourTruncated)

                if (isCurrentActivityActive) {
                    holder.tvMensaje.typeface = Typeface.DEFAULT_BOLD
                    holder.tvHora.typeface = Typeface.DEFAULT_BOLD
                    holder.tvMensaje.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    holder.tvHora.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    //holder.tvFecha.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    holder.itemLayout.background = ContextCompat.getDrawable(context, R.drawable.fondo_actividad_actual)
                    Log.d("ActividadAdapter", "Resaltando actividad: ${actividad.mensaje} (Hora actual truncada: $currentHourTruncated, Hora ítem truncada: $itemHourTruncated)")
                } else {
                    //De momento nada
                }

            } catch (e: Exception) {
                Log.e("ActividadAdapter", "Error al comparar horas o parsear en onBindViewHolder: ${e.message}", e)
            }
        }
    }

    override fun getItemCount(): Int = actividades.size

    fun updateActividades(newActividades: List<ActividadEntity>) {
        this.actividades = newActividades
        notifyDataSetChanged()
    }

    // FUNCIÓN para establecer la hora activa y refrescar la lista
    fun setActiveHour(hour: LocalTime?) {
        if (activeHour != hour) { // Solo si la hora ha cambiado
            this.activeHour = hour
            notifyDataSetChanged() // Esto redibujará los ítems y aplicará el resaltado
        }
    }
}