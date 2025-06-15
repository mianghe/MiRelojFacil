package com.mianghe.mirelojfacil.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mianghe.mirelojfacil.models.Actividad // Si quieres un mapper, lo usaremos

@Entity(tableName = "actividades")
data class ActividadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // Clave primaria autogenerada para la base de datos
    val mensaje: String,
    val fechaAplicacion: String,
    val horaAplicacion: String,
    val periodicidad: String
)

// Opcional: Funciones de mapeo para convertir entre el modelo de red y el modelo de base de datos
fun Actividad.toEntity(): ActividadEntity {
    return ActividadEntity(
        mensaje = this.mensaje,
        fechaAplicacion = this.fechaAplicacion,
        horaAplicacion = this.horaAplicacion,
        periodicidad = this.periodicidad
    )
}

fun List<Actividad>.toEntityList(): List<ActividadEntity> {
    return this.map { it.toEntity() }
}