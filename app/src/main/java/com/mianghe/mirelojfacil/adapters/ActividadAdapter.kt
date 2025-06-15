package com.mianghe.mirelojfacil.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mianghe.mirelojfacil.R
import com.mianghe.mirelojfacil.database.ActividadEntity

class ActividadAdapter(private var actividades: List<ActividadEntity>) :
    RecyclerView.Adapter<ActividadAdapter.ActividadViewHolder>() {

    class ActividadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMensaje: TextView = itemView.findViewById(R.id.tv_item_mensaje)
        val tvFechaHora: TextView = itemView.findViewById(R.id.tv_item_fecha_hora)
        // val tvNothing: TextView = itemView.findViewById(R.id.tv_item_nothing) // Si se incluye en item_actividad.xml
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActividadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_actividad, parent, false)
        return ActividadViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActividadViewHolder, position: Int) {
        val actividad = actividades[position]
        holder.tvMensaje.text = actividad.mensaje
        holder.tvFechaHora.text = "Fecha: ${actividad.fechaAplicacion} Hora: ${actividad.horaAplicacion}"
        // holder.tvNothing.text = "Nothing: ${actividad.nothing}" // Si se incluye
    }

    override fun getItemCount(): Int = actividades.size

    fun updateActividades(newActividades: List<ActividadEntity>) {
        this.actividades = newActividades
        notifyDataSetChanged() // Notifica al RecyclerView que los datos han cambiado
    }
}