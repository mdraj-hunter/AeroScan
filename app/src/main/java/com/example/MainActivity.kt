package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.AirQualityRepository
import com.example.ui.screens.PollutionAppScreen
import com.example.ui.viewmodel.AirQualityViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Database components
    val database = AppDatabase.getDatabase(applicationContext)
    val dao = database.airQualityScanDao()
    val repository = AirQualityRepository(dao)
    
    // Instantiate ViewModel utilizing simple Constructor Factory
    val factory = AirQualityViewModel.Factory(repository)
    val viewModel: AirQualityViewModel by viewModels { factory }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          PollutionAppScreen(viewModel = viewModel)
        }
      }
    }
  }
}
