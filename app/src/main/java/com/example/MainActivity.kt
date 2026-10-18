package com.example

import android.os.Bundle
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
        enableEdgeToEdge()

        setContent {
            AutoMkvDubberTheme {
                DubberScreen(viewModel = viewModel)
            }
        }
    }
}
