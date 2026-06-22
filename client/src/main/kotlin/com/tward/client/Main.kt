package com.tward.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Square
import com.tward.ui.board.ChessBoardView
import com.tward.shared.PlayerStatus

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Chess — Test Client",
        state = rememberWindowState(width = 980.dp, height = 760.dp)
    ) {
        val scope = rememberCoroutineScope()
        val client = remember { ChessClient(scope) }
        MaterialTheme {
            Surface {
                when (client.screen) {
                    Screen.CONNECT -> ConnectScreen(client)
                    Screen.LOBBY -> LobbyScreen(client)
                    Screen.GAME -> GameScreen(client)
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(client: ChessClient) {
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
        Text(client.status, color = Color.Gray)
    }
}

@Composable
private fun LobbyScreen(client: ChessClient) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Lobby", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(client.status, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxSize()) {

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 12.dp)) {
                Text("Players online", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val others = client.players.filter { it.id != client.myId }
                if (others.isEmpty()) Text("No one else is online yet.", color = Color.Gray)
                others.forEach { player ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${player.name}  ·  ${player.status}", modifier = Modifier.weight(1f))
                            if (player.status == PlayerStatus.AVAILABLE) {
                                Button(onClick = { client.challengePlayer(player.id, "white") }) { Text("Challenge") }
                            }
                        }
                    }
                }

                if (client.challenges.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Incoming challenges", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    client.challenges.forEach { challenge ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${challenge.fromName} challenges you", modifier = Modifier.weight(1f))
                                Button(onClick = { client.accept(challenge.id) }) { Text("Accept") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { client.decline(challenge.id) }) { Text("Decline") }
                            }
                        }
                    }
                }
            }

            Divider(Modifier.width(1.dp).fillMaxSize().background(Color.LightGray))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp)) {
                Text("Challenge a bot", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                client.bots.forEach { bot ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${bot.name}   ~${bot.approxElo}", fontWeight = FontWeight.Bold)
                            Text("${bot.style} — ${bot.description}", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Button(onClick = { client.challengeBot(bot.id, "white") }) { Text("Play White") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { client.challengeBot(bot.id, "black") }) { Text("Play Black") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameScreen(client: ChessClient) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { client.leaveGame() }) { Text("← Lobby") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { client.resign() }) { Text("Resign") }
            Spacer(Modifier.width(16.dp))
            Text(client.status, color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))

        val game = client.game
        if (game == null) {
            Text("Loading game…")
            return@Column
        }

        Text(
            "You are ${client.myColour}  ·  ${game.sideToMove} to move  ·  ${game.status}",
            fontWeight = FontWeight.Bold
        )
        Text("White ${formatClock(game.whiteMillis)}    Black ${formatClock(game.blackMillis)}", color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        BoardGrid(client, game.fen, game.lastMove)
    }
}

@Composable
private fun BoardGrid(client: ChessClient, fen: String, lastMove: String?) {
    val board = remember(fen) { Board.fromFEN(fen) }
    ChessBoardView(
        pieceAt = { board.getPiece(it) },
        orientation = if (client.myColour == "black") Colour.BLACK else Colour.WHITE,
        squareSize = 72,
        selected = client.selected,
        lastMoveFrom = lastMove?.let { Square.fromString(it.take(2)) },
        lastMoveTo = lastMove?.let { Square.fromString(it.substring(2, 4)) },
        onSquareClick = { client.clickSquare(it) }
    )
}

private fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
