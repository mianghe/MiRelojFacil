package com.mianghe.mirelojfacil.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActividadDao {

    @Query("SELECT * FROM actividades ORDER BY horaAplicacion")
    fun getAllActividades(): Flow<List<ActividadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Si existe por PK, lo reemplaza
    suspend fun insertAll(actividades: List<ActividadEntity>)

    @Query("DELETE FROM actividades")
    suspend fun deleteAll()
}