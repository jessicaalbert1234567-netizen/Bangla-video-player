package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.automkvdubber.ui.DubberScreen
import com.example.automkvdubber.ui.DubberViewModel
import com.example.ui.theme.AutoMkvDubberTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DubberViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AutoMkvDubber", "Uncaught exception on thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()

        setContent {
            AutoMkvDubberTheme {
                DubberScreen(viewModel = viewModel)
            }
        }
    }
}
