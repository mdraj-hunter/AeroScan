package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "air_quality_scans")
data class AirQualityScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val aqi: Int,
    val category: String,
    val dominantPollutant: String,
    val description: String,
    val recommendations: String,
    val isSimulated: Boolean,
    val presetName: String? = null,
    val location: String = "San Francisco, CA"
)
