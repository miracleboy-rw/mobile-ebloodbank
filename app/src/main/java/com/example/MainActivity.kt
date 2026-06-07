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
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.EBloodBankDatabase
import com.example.data.EBloodBankRepository
import com.example.ui.EBloodBankApp
import com.example.ui.EBloodBankViewModel
import com.example.ui.EBloodBankViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = EBloodBankDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = EBloodBankRepository(database.dao())
        val factory = EBloodBankViewModelFactory(application, repository)
        val viewModel: EBloodBankViewModel by viewModels { factory }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val useDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EBloodBankApp(viewModel = viewModel)
                }
            }
        }
    }
}
