package com.mianghe.mirelojfacil.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Actividad(
    @SerialName("field_mensaje") //Cambiamos los nombres para mayor claridad
    val mensaje: String,
    @SerialName("field_fecha_aplicacion")
    val fechaAplicacion: String,
    @SerialName("field_fecha_aplicacion_1")
    val horaAplicacion: String,
    @SerialName("nothing")
    val periodicidad: String
)