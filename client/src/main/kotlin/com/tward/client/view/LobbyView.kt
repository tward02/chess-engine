package com.tward.client.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.client.model.ChessClient
import com.tward.client.view.Utils.Companion.subtle
import com.tward.shared.PlayerStatus

@Composable
fun LobbyScreen(client: ChessClient) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Lobby", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(client.status, color = subtle())
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxSize()) {

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 12.dp)) {
                Text("Players online", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val others = client.players.filter { it.id != client.myId }
                if (others.isEmpty()) Text("No one else is online yet.", color = subtle())
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

            Divider(
                Modifier.width(1.dp).fillMaxSize().background(MaterialTheme.colors.onBackground.copy(alpha = 0.15f))
            )

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp)) {
                Text("Challenge a bot", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                client.bots.forEach { bot ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${bot.name}   ~${bot.approxElo}", fontWeight = FontWeight.Bold)
                            Text("${bot.style} — ${bot.description}", fontSize = 12.sp, color = subtle())
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
