package com.tward.client.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.client.model.ChessClient
import com.tward.client.view.Utils.Companion.subtle

@Composable
fun ConnectScreen(client: ChessClient) {
    var host by remember { mutableStateOf("localhost") }
    var port by remember { mutableStateOf("8080") }
    var name by remember { mutableStateOf("Player") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Connect to a chess server", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(host, { host = it }, label = { Text("Host") })
        OutlinedTextField(port, { port = it }, label = { Text("Port") })
        OutlinedTextField(name, { name = it }, label = { Text("Your name") })
        Button(onClick = { client.connect(host.trim(), port.trim().toIntOrNull() ?: 8080, name.trim()) }) {
            Text("Connect")
        }
        Text(client.status, color = subtle())
    }
}
