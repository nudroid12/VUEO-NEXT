package com.vueo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vueo.shared.core.VueoClient
import com.vueo.shared.core.VueoCore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VueoTvFoundation()
        }
    }
}

@Composable
private fun VueoTvFoundation() {
    val handshake = VueoCore.handshake(VueoClient.TV)

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "VUEO TV",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = handshake.message,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
