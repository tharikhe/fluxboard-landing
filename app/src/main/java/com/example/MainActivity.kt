package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.local.AppDatabase
import com.example.data.pref.KeyboardPreferences
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = KeyboardPreferences(applicationContext)
        val databaseDao = AppDatabase.getDatabase(applicationContext).keyboardDao()

        setContent {
            MyApplicationTheme {
                SettingsScreen(
                    preferences = preferences,
                    dbDao = databaseDao
                )
            }
        }
    }
}
