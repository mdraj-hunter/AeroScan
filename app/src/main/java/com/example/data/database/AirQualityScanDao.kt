package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AirQualityScanDao {
    @Query("SELECT * FROM air_quality_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<AirQualityScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: AirQualityScanEntity): Long

    @Query("DELETE FROM air_quality_scans WHERE id = :id")
    suspend fun deleteScanById(id: Int)

    @Query("DELETE FROM air_quality_scans")
    suspend fun clearAllScans()
}
