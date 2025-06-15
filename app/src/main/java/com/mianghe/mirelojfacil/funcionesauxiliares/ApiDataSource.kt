package com.mianghe.mirelojfacil.network

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.mianghe.mirelojfacil.database.ActividadEntity
import com.mianghe.mirelojfacil.database.AppDatabase
import com.mianghe.mirelojfacil.database.toEntityList
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
suspend fun fetchActividadesFromApi(context: Context, uuid: String, email: String, password: String): List<Actividad>? {
    val database = AppDatabase.getDatabase(context) // Obtener la instancia de la DB
    val actividadDao = database.actividadDao() // Obtener el DAO

    val apiUrl = "https://mirelojfacil.ddns.net/api/actividades/$uuid"
    var connection: HttpURLConnection? = null
    try {
        val url = URL(apiUrl)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000 // 10 segundos
        connection.readTimeout = 10000    // 10 segundos

        val credentials = "$email:$password"
        val authString = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        connection.setRequestProperty("Authorization", "Basic $authString")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            Log.d("ApiDataSource", "Respuesta de la API para $uuid: $response")

            val actividadesApi: List<Actividad> = json.decodeFromString(response)
            Log.d("ApiDataSource", "Actividades parseadas de la API: $actividadesApi")

            // *** Lógica para vaciar y actualizar la base de datos ***
            val actividadesEntity: List<ActividadEntity> = actividadesApi.toEntityList()

            try {
                // Iniciar una transacción para asegurar atomicidad
                database.withTransaction { // Room KTX extension for transactions
                    actividadDao.deleteAll() // Vaciar la tabla
                    actividadDao.insertAll(actividadesEntity) // Insertar los nuevos datos
                }
                Log.d("ApiDataSource", "Base de datos Room actualizada exitosamente con ${actividadesEntity.size} actividades.")
                return actividadesApi // Devolver los datos de la API
            } catch (dbError: Exception) {
                Log.e("ApiDataSource", "Error al actualizar la base de datos Room: ${dbError.message}", dbError)
                // Si falla la DB, consideramos que la operación completa falló
                return null
            }
        } else {
            Log.e("ApiDataSource", "Error HTTP de la API: $responseCode - ${connection.responseMessage}")
            return null // Si la API falla, no se actualiza la DB
        }
    } catch (e: Exception) {
        Log.e("ApiDataSource", "Error en la llamada a la API o al parsear JSON: ${e.message}", e)
        return null // Si hay una excepción, no se actualiza la DB
    } finally {
        connection?.disconnect()
    }
}