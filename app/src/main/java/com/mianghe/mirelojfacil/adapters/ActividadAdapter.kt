package com.mianghe.mirelojfacil.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mianghe.mirelojfacil.R
import com.mianghe.mirelojfacil.database.ActividadEntity
import java.time.LocalTime // Para manejar solo la hora
import java.time.format.DateTimeFormatter // Para formatear la hora
import java.util.Locale // Importar Locale

class ActividadAdapter(private var actividades: List<ActividadEntity>) :
    RecyclerView.Adapter<ActividadAdapter.ActividadViewHolder>() {

    class ActividadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMensaje: TextView = itemView.findViewById(R.id.tv_item_mensaje)
        //val tvFecha: TextView = itemView.findViewById(R.id.tv_item_fecha) // ¡ACTUALIZADO ID!
        val tvHora: TextView = itemView.findViewById(R.id.tv_item_hora)     // ¡NUEVO ID!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActividadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_actividad, parent, false)
        return ActividadViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActividadViewHolder, position: Int) {
        val actividad = actividades[position]
        holder.tvMensaje.text = actividad.mensaje
        //holder.tvFecha.text = actividad.fechaAplicacion // La fecha se asigna directamente

        // Formatear la hora a HH:MM
        try {
            // Asumimos que actividad.horaAplicacion ya está en formato "HH:MM" (e.g., "22:00")
            // Si viene en otro formato (e.g., "21:18:00"), puedes parsearlo primero.
            // Para "HH:MM" directamente:
            val horaFormateada = actividad.horaAplicacion // Ya debería estar en HH:MM
            holder.tvHora.text = horaFormateada
        } catch (e: Exception) {
            // En caso de que el formato de hora no sea el esperado
            holder.tvHora.text = actividad.horaAplicacion // Muestra el valor original como fallback
            //Log.e("ActividadAdapter", "Error al formatear hora: ${actividad.horaAplicacion}", e)
        }
    }

    override fun getItemCount(): Int = actividades.size

    fun updateActividades(newActividades: List<ActividadEntity>) {
        this.actividades = newActividades
        notifyDataSetChanged()
    }
}