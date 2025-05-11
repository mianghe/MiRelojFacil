package com.mianghe.mirelojfacil.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import com.mianghe.mirelojfacil.funcionesauxiliares.playOkGoogleCommand
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MedioPlazoWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val thingSpeakApiKey = "KX88XIUP4Y15K1KZ" // Reemplaza con tu API Key
    private val thingSpeakChannelId = "2954890" // Reemplaza con tu Channel ID

    // Hace el trabajo en segundo plano
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("MedioPlazoWorker", "Ejecutando tareas de medio plazo")
        try {
            val batteryLevel = getBatteryLevel()
            sendBatteryLevelToThingSpeak(batteryLevel)
            Result.success()
        } catch (e: Exception) {
            Log.e("MedioPlazoWorker", "Error during battery check or data sending", e)
            Result.failure()
        }
    }

    // Obtiene el nivel de batería
    private fun getBatteryLevel(): Int {
        val batteryIntent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level != -1 && scale > 0) (level * 100.0 / scale).toInt() else -1
    }

    // Envía el nivel de batería a ThingSpeak
    private fun sendBatteryLevelToThingSpeak(level: Int) {
        //encenderApagarEnchufe(level)
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

    private fun encenderApagarEnchufe(level: Int) {
        if (level < 21) {
            playOkGoogleCommand(1)
        } else if (level < 80) {
            playOkGoogleCommand(0)
        }
    }


}