package com.mianghe.mirelojfacil.network

import android.util.Base64
import android.util.Log
import com.mianghe.mirelojfacil.models.Actividad
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

// Instancia de Json para decodificar
private val json = Json { ignoreUnknownKeys = true }

/**
 * Realiza una llamada a la API de actividades con autenticación básica.
 * @param uuid El UUID de la aplicación.
 * @param email El email para la autenticación básica.
 * @param password La contraseña para la autenticación básica.
 * @return Una lista de objetos Actividad si la llamada fue exitosa, o null en caso de error.
 */
suspend fun fetchActividadesFromApi(uuid: String, email: String, password: String): List<Actividad>? {
    val apiUrl = "https://mirelojfacil.ddns.net/api/actividades/$uuid"
    var connection: HttpURLConnection? = null
    try {
        val url = URL(apiUrl)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000 // 10 segundos
        connection.readTimeout = 10000    // 10 segundos

        // Añadir la cabecera de Autenticación Básica
        val credentials = "$email:$password"
        val authString = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        connection.setRequestProperty("Authorization", "Basic $authString")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            Log.d("ApiDataSource", "Respuesta de la API para $uuid: $response")

            // Parsear el JSON
            val actividades: List<Actividad> = json.decodeFromString(response)
            Log.d("ApiDataSource", "Actividades parseadas: $actividades")
            return actividades
        } else {
            Log.e("ApiDataSource", "Error HTTP: $responseCode - ${connection.responseMessage}")
            return null
        }
    } catch (e: Exception) {
        Log.e("ApiDataSource", "Error en la llamada a la API o al parsear JSON: ${e.message}", e)
        return null
    } finally {
        connection?.disconnect()
    }
}