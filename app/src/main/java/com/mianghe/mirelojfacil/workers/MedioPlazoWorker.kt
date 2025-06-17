package com.mianghe.mirelojfacil.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Base64
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mianghe.mirelojfacil.PREF_EMAIL
import com.mianghe.mirelojfacil.PREF_PASSWORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.mianghe.mirelojfacil.PREF_UUID // Importa la clave del UUID
import com.mianghe.mirelojfacil.dataStore // Importa la extensión de DataStore
//import com.mianghe.mirelojfacil.funcionesauxiliares.loadActividadesFromDatabase
import com.mianghe.mirelojfacil.models.Actividad // Importa tu nueva data class
import com.mianghe.mirelojfacil.network.fetchActividadesFromApi
import com.mianghe.mirelojfacil.network.sendBatteryLevelToApi
import kotlinx.coroutines.flow.first // Para obtener el primer valor del Flow de DataStore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json // Para el parser JSON
import kotlinx.serialization.decodeFromString // Función de extensión para decodificar JSON
import com.mianghe.mirelojfacil.funcionesauxiliares.getBatteryLevel

class MedioPlazoWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val thingSpeakApiKey = "KX88XIUP4Y15K1KZ" // API Key
    private val thingSpeakChannelId = "2954890" // Channel ID

    // Instancia de Json para decodificar, configurada para ser "lenient" si hay flexibilidad en el JSON
    //private val json = Json { ignoreUnknownKeys = true } // Importante para ignorar campos desconocidos si la API cambia

    // Hace el trabajo en segundo plano
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Obtener el UUID, Email y Contraseña de DataStore
        val preferences = try {
            applicationContext.dataStore.data.first()
        } catch (e: Exception) {
            Log.e("MedioPlazoWorker", "Error al leer preferencias de DataStore: ${e.message}")
            return@withContext Result.failure()
        }

        val uuid = preferences[PREF_UUID]
        val email = preferences[PREF_EMAIL]
        val password = preferences[PREF_PASSWORD]

        if (uuid.isNullOrEmpty() || email.isNullOrEmpty() || password.isNullOrEmpty()) {
            Log.w("MedioPlazoWorker", "UUID, email o contraseña no encontrados. No se realizará la llamada a la API.")
            // Result.failure() si las credenciales son estrictamente necesarias
            // o Result.success() si la falta de credenciales solo significa que no hay autenticación.
            // Para este caso, asumimos que son obligatorias.
            return@withContext Result.failure()
        }

        var activitiesSyncSuccess = false
        var batteryUpdateSuccess = false
        var remoteNodeUuid: String? = null
        //Llama a la API y guarda la información en ROOM
        val actividades = fetchActividadesFromApi(applicationContext, uuid, email, password)

        if (actividades != null) {
            Log.d("MedioPlazoWorker", "Sincronización de actividades exitosa. Total: ${actividades.size}")
            // Aquí obtener los datos de la DB para mostrar
            // Por ejemplo: val actividadesDB = AppDatabase.getDatabase(applicationContext).actividadDao().getAllActividades()
            // y actualizar UI con ellos.
            //Result.success()
            activitiesSyncSuccess = true
            if (actividades.isNotEmpty()) {
                remoteNodeUuid = actividades.first().uuid_nodo_remoto // Tomamos el UUID del primer elemento
            } else {
                Log.w("MedioPlazoWorker", "No hay actividades en la DB para obtener uuid_nodo_remoto. No se actualizará la batería.")
                activitiesSyncSuccess = false // Si no hay actividades para obtener el UUID del nodo, consideramos el sync fallido para el PATCH
            }
        } else {
            Log.e("MedioPlazoWorker", "Fallo en la sincronización de actividades. No se podrá obtener uuid_nodo_remoto.")
            activitiesSyncSuccess = false
        }

        if (remoteNodeUuid != null && activitiesSyncSuccess) { // Solo si el sync de actividades fue OK y tenemos el UUID
            val batteryLevel = getBatteryLevel(applicationContext)
            if (batteryLevel != -1) {
                batteryUpdateSuccess = sendBatteryLevelToApi(remoteNodeUuid, batteryLevel, email, password)
                if (batteryUpdateSuccess) {
                    Log.d("MedioPlazoWorker", "Nivel de batería ($batteryLevel%) enviado a Drupal para nodo $remoteNodeUuid.")
                } else {
                    Log.e("MedioPlazoWorker", "Fallo al enviar nivel de batería ($batteryLevel%) a Drupal para nodo $remoteNodeUuid.")
                }
            } else {
                Log.w("MedioPlazoWorker", "No se pudo obtener el nivel de batería. No se enviará a Drupal para nodo $remoteNodeUuid.")
                batteryUpdateSuccess = false
            }
        } else {
            Log.w("MedioPlazoWorker", "No se intentará enviar nivel de batería debido a falta de uuid_nodo_remoto o fallo de sincronización previa.")
            batteryUpdateSuccess = false // Consideramos que esta parte falló si no se pudo obtener el UUID
        }

        // Determinar el resultado final del doWork
        if (activitiesSyncSuccess && batteryUpdateSuccess) {
            Result.success()
        } else if (activitiesSyncSuccess || batteryUpdateSuccess) {
            // Si al menos una fue exitosa, pero la otra no. Consideramos éxito para no reintentar a menos que quieras
            Result.success()
        } else {
            Result.retry() // Si ambas fallaron, WorkManager intentará de nuevo más tarde
        }

        /*Log.d("MedioPlazoWorker", "Ejecutando tareas de medio plazo")
        try {
            val batteryLevel = getBatteryLevel()
            //sendBatteryLevelToThingSpeak(batteryLevel)
            //Podemos aprovechar aquí para enviar mediante la API el estado de la batería al servidor
            Result.success()
        } catch (e: Exception) {
            Log.e("MedioPlazoWorker", "Error during battery check or data sending", e)
            Result.failure()
        }*/
    }

    // Obtiene el nivel de batería
    /*private fun getBatteryLevel(): Int {
        val batteryIntent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level != -1 && scale > 0) (level * 100.0 / scale).toInt() else -1
    }*/

    // Envía el nivel de batería a ThingSpeak
    private fun sendBatteryLevelToThingSpeak(level: Int) {
        if (level != -1) {
            val field = "field1" // Oscal
            //val field = "field2" // Android Studio
            //val field = "field3" // Samsung
            val thingSpeakUrl =
                "https://api.thingspeak.com/update?api_key=$thingSpeakApiKey&$field=$level"
            try {
                val url = URL(thingSpeakUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET" // O "POST" si prefieres
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(
                        "MedioPlazoWorker",
                        "Battery level ($level%) sent to ThingSpeak successfully"
                    )
                } else {
                    Log.e(
                        "MedioPlazoWorker",
                        "Failed to send data to ThingSpeak. Response code: $responseCode"
                    )
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("MedioPlazoWorker", "Error sending data to ThingSpeak", e)
            }
        } else {
            Log.w("MedioPlazoWorker", "Could not retrieve battery level.")
        }
    }



}