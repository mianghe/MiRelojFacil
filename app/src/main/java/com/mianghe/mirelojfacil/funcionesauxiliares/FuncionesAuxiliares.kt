package com.mianghe.mirelojfacil.funcionesauxiliares

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.mianghe.mirelojfacil.adapters.ActividadAdapter
import com.mianghe.mirelojfacil.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carga las actividades desde la base de datos Room y actualiza el RecyclerView a través del adaptador.
 * Debe llamarse desde una corrutina en Dispatchers.IO para la operación de base de datos,
 * y luego cambiar a Dispatchers.Main para la actualización del adaptador.
 *
 * @param context El contexto de la aplicación para acceder a la base de datos.
 * @param actividadAdapter El adaptador del RecyclerView a actualizar.
 */
/*suspend fun loadActividadesFromDatabase(context: Context, actividadAdapter: ActividadAdapter) {
    // La operación de base de datos debe hacerse en un hilo de fondo (Dispatchers.IO)
    withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val actividades = database.actividadDao().getAllActividades()

        // La actualización del adaptador (operación de UI) debe hacerse en el hilo principal (Dispatchers.Main)
        withContext(Dispatchers.Main) {
            actividadAdapter.updateActividades(actividades)
            Log.d("FuncionesAuxiliares", "Actividades cargadas desde DB: ${actividades.size}")
        }
    }
}*/
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