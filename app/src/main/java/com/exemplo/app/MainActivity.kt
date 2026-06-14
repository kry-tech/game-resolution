package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            AppTela()
        }
    }
}

@Composable
fun AppTela() {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Text(text = "Olá, Android em Kotlin!")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTela() {
    AppTela()
}
