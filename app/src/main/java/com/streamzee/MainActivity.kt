package com.streamzee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.streamzee.ui.streamzeeApp
import com.streamzee.ui.theme.streamzeeTheme
import com.streamzee.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            streamzeeTheme {
                streamzeeApp(viewModel = viewModel)
            }
        }
    }
}
