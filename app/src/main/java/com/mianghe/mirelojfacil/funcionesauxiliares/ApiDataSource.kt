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
import java.time.DayOfWeek // Importar DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields // Importar WeekFields
import java.util.Locale // Importar Locale

private val json = Json { ignoreUnknownKeys = true }
private val apiDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/**
 * Realiza una llamada a la API de actividades con autenticación básica y actualiza la base de datos Room.
 * Procesa y filtra los datos antes de almacenarlos basándose en la periodicidad y la fecha.
 * @param context El contexto de la aplicación para acceder a la base de datos.
 * @param uuid El UUID de la aplicación.
 * @param email El email para la autenticación básica.
 * @param password La contraseña para la autenticación básica.
 * @return Una lista de objetos Actividad (ya procesados) si la llamada y la actualización de la DB fueron exitosas, o null en caso de error.
 */
suspend fun fetchActividadesFromApi(context: Context, uuid: String, email: String, password: String): List<Actividad>? {
    val database = AppDatabase.getDatabase(context)
    val actividadDao = database.actividadDao()

    val apiUrl = "https://mirelojfacil.ddns.net/api/actividades/$uuid"
    var connection: HttpURLConnection? = null
    try {
        val url = URL(apiUrl)
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val credentials = "$email:$password"
        val authString = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        connection.setRequestProperty("Authorization", "Basic $authString")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            Log.d("ApiDataSource", "Respuesta de la API para $uuid: $response")

            val actividadesApi: List<Actividad> = json.decodeFromString(response)
            Log.d("ApiDataSource", "Actividades parseadas de la API (RAW): $actividadesApi")

            // *** LÓGICA DE PROCESAMIENTO Y FILTRADO DE DATOS CON PERIODICIDAD ***
            val today = LocalDate.now()
            // Obtener el día de la semana actual, ajustando para que Lunes=0, Martes=1, etc.
            // DayOfWeek.MONDAY.ordinal es 0, DayOfWeek.SUNDAY.ordinal es 6
            // La periodicidad es Lunes=0, Martes=1, ..., Domingo=6
            val currentDayOfWeekIndex = today.dayOfWeek.value - 1 // Lunes (1) -> 0, Domingo (7) -> 6

            val processedActividades = actividadesApi.filter { actividad ->
                var isValid = false // Por defecto, asumimos que la actividad NO es válida hasta que se demuestre lo contrario

                // Si la periodicidad es nula o no tiene 7 caracteres, descartar (o tratar como error)
                if (actividad.periodicidad.isNullOrBlank() || actividad.periodicidad.length != 7) {
                    Log.w("ApiDataSource", "Periodicidad inválida o ausente para actividad con mensaje '${actividad.mensaje}'. Descartando.")
                    return@filter false
                }

                try {
                    val actividadDate = LocalDate.parse(actividad.fechaAplicacion, apiDateFormat)

                    // Caso especial: periodicidad es "0000000"
                    if (actividad.periodicidad == "0000000") {
                        if (actividadDate.isEqual(today)) {
                            isValid = true
                            Log.d("ApiDataSource", "Actividad '${actividad.mensaje}' mostrada: Periodicidad '0000000' y fecha coincide (${actividad.fechaAplicacion}).")
                        } else {
                            Log.d("ApiDataSource", "Actividad '${actividad.mensaje}' descartada: Periodicidad '0000000' pero fecha no coincide (${actividad.fechaAplicacion}).")
                        }
                    }
                    // Caso general: periodicidad tiene '1' para el día actual
                    else {
                        // Comprobar si el carácter correspondiente al día de la semana actual es '1'
                        if (actividad.periodicidad[currentDayOfWeekIndex] == '1') {
                            isValid = true
                            // También podemos añadir un filtro para no mostrar actividades futuras
                            // si la periodicidad es por día de la semana.
                            // Si quieres mostrar solo las actividades del día actual, que tienen '1'
                            // y que su fecha no sea en el pasado, puedes añadir:
                            /*if (!actividadDate.isBefore(today)) { // No mostrar si es fecha pasada
                                isValid = true
                                Log.d("ApiDataSource", "Actividad '${actividad.mensaje}' mostrada: Periodicidad activa para hoy y fecha válida.")
                            } else {
                                Log.d("ApiDataSource", "Actividad '${actividad.mensaje}' descartada: Periodicidad activa pero fecha ya pasó (${actividad.fechaAplicacion}).")
                            }*/
                        } else {
                            Log.d("ApiDataSource", "Actividad '${actividad.mensaje}' descartada: Periodicidad no activa para hoy.")
                        }
                    }
                } catch (e: DateTimeParseException) {
                    Log.e("ApiDataSource", "Error de formato de fecha en actividad '${actividad.mensaje}': ${actividad.fechaAplicacion}. Descartando.", e)
                    return@filter false // Descartar si el formato de fecha es incorrecto
                } catch (e: IndexOutOfBoundsException) {
                    Log.e("ApiDataSource", "Error de índice al acceder a la periodicidad '${actividad.periodicidad}' para día $currentDayOfWeekIndex. Descartando.", e)
                    return@filter false // Descartar si la periodicidad no tiene la longitud esperada
                }

                isValid // Retorna si la actividad debe ser incluida en la lista procesada
            }
            // ***************************************************************

            Log.d("ApiDataSource", "Actividades procesadas/filtradas: $processedActividades")

            // Si después del filtro no quedan actividades válidas, la base de datos se vaciará.
            // Para que no se vacíe si el filtro devuelve 0 resultados, puedes añadir un 'if' aquí
            // y solo proceder con la transacción si processedActividades no está vacía.
            /*if (processedActividades.isEmpty()) {
                Log.d("ApiDataSource", "No hay actividades válidas para guardar después del procesamiento. Vaciando la tabla de Room.")
                try {
                    database.runInTransaction {
                        actividadDao.deleteAll() // Vaciar la tabla si no hay actividades válidas
                    }
                    return emptyList() // Retorna una lista vacía para indicar que no hay actividades
                } catch (dbError: Exception) {
                    Log.e("ApiDataSource", "Error al vaciar la base de datos Room: ${dbError.message}", dbError)
                    return null // Si falla la DB, la operación completa falló
                }
            }*/

            // Mapear a entidad de Room y guardar en la base de datos
            val actividadesEntity: List<ActividadEntity> = processedActividades.toEntityList()

            try {
                database.withTransaction {
                    actividadDao.deleteAll() // Vaciar la tabla
                    actividadDao.insertAll(actividadesEntity) // Insertar los nuevos datos
                }
                Log.d("ApiDataSource", "Base de datos Room actualizada exitosamente con ${actividadesEntity.size} actividades procesadas.")
                return processedActividades // Devuelve las actividades que realmente se guardaron
            } catch (dbError: Exception) {
                Log.e("ApiDataSource", "Error al actualizar la base de datos Room: ${dbError.message}", dbError)
                return null
            }
        } else {
            Log.e("ApiDataSource", "Error HTTP de la API: $responseCode - ${connection.responseMessage}")
            return null
        }
    } catch (e: Exception) {
        Log.e("ApiDataSource", "Error en la llamada a la API o al parsear JSON: ${e.message}", e)
        return null
    } finally {
        connection?.disconnect()
    }
}