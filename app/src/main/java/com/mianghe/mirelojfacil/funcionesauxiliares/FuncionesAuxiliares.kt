package com.mianghe.mirelojfacil.funcionesauxiliares

import android.content.Context
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