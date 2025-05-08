package com.mianghe.mirelojfacil.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BatteryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val thingSpeakApiKey = "KX88XIUP4Y15K1KZ" // Reemplaza con tu API Key
    private val thingSpeakChannelId = "2954890" // Reemplaza con tu Channel ID

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val batteryLevel = getBatteryLevel()
            sendBatteryLevelToThingSpeak(batteryLevel)
            Result.success()
        } catch (e: Exception) {
            Log.e("BatteryWorker", "Error during battery check or data sending", e)
            Result.failure()
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level != -1 && scale > 0) (level * 100.0 / scale).toInt() else -1
    }

    private fun sendBatteryLevelToThingSpeak(level: Int) {
        if (level != -1) {
            val thingSpeakUrl =
                "https://api.thingspeak.com/update?api_key=$thingSpeakApiKey&field1=$level"
            try {
                val url = URL(thingSpeakUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET" // O "POST" si prefieres
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("BatteryWorker", "Battery level ($level%) sent to ThingSpeak successfully")
                } else {
                    Log.e("BatteryWorker", "Failed to send data to ThingSpeak. Response code: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("BatteryWorker", "Error sending data to ThingSpeak", e)
            }
        } else {
            Log.w("BatteryWorker", "Could not retrieve battery level.")
        }
    }
}