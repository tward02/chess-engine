package com.tward.client

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.client.model.ChessClient
import com.tward.client.model.Screen
import com.tward.client.view.ConnectScreen
import com.tward.client.view.GameScreen
import com.tward.client.view.LobbyScreen
import com.tward.ui.board.ChessTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Chess — Test Client",
        state = rememberWindowState(width = 1000.dp, height = 1000.dp)
    ) {
        val scope = rememberCoroutineScope()
        val client = remember { ChessClient(scope) }

        var darkOverride by remember { mutableStateOf<Boolean?>(null) }
        val dark = darkOverride ?: isSystemInDarkTheme()

        ChessTheme(darkTheme = dark) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(dark = dark, onSetDark = { darkOverride = it })
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        when (client.screen) {
                            Screen.CONNECT -> ConnectScreen(client)
                            Screen.LOBBY -> LobbyScreen(client)
                            Screen.GAME -> GameScreen(client)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(dark: Boolean, onSetDark: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("♟  Chess", color = MaterialTheme.colors.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("Dark", color = MaterialTheme.colors.onPrimary, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Switch(checked = dark, onCheckedChange = onSetDark)
    }
}
