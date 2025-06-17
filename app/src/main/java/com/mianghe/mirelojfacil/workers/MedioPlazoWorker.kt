package com.mianghe.mirelojfacil.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mianghe.mirelojfacil.PREF_EMAIL
import com.mianghe.mirelojfacil.PREF_PASSWORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mianghe.mirelojfacil.PREF_UUID // Importa la clave del UUID
import com.mianghe.mirelojfacil.dataStore // Importa la extensión de DataStore
import com.mianghe.mirelojfacil.funcionesauxiliares.fetchActividadesFromApi
import com.mianghe.mirelojfacil.funcionesauxiliares.sendBatteryLevelToApi
import kotlinx.coroutines.flow.first // Para obtener el primer valor del Flow de DataStore
import com.mianghe.mirelojfacil.funcionesauxiliares.getBatteryLevel

class MedioPlazoWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

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
    }
}