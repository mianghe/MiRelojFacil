package com.mianghe.mirelojfacil.funcionesauxiliares

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

// Obtiene el nivel de batería
/**
 * Obtiene el nivel de batería actual del dispositivo.
 * @param context El contexto para registrar el BroadcastReceiver.
 * @return El nivel de batería como un entero (0-100), o -1 si no se pudo obtener.
 */
fun getBatteryLevel(context: Context): Int { // No es 'suspend' porque no bloquea
    val batteryIntent: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
        context.registerReceiver(null, ifilter)
    }

    val level: Int = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale: Int = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100

    val batteryPct = if (level != -1 && scale > 0) (level * 100.0 / scale).toInt() else -1
    // Log.d("FuncionesAuxiliares", "Nivel de batería obtenido: $batteryPct%")
    return batteryPct
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

private val thingSpeakApiKey = "KX88XIUP4Y15K1KZ" // API Key
private val thingSpeakChannelId = "2954890" // Channel ID

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