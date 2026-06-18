package com.tward.ui.view.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.BotPlayer
import com.tward.engine.tournament.ContenderRecord
import com.tward.engine.tournament.MultiBotTournament
import com.tward.engine.tournament.Pairing
import com.tward.ui.model.ChessMatch
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import com.tward.ui.view.game.BoardView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// Fallback clock for the displayed game when no tournament time is set; long enough to never flag
private const val DISPLAY_TIME_MILLIS = 60L * 60 * 1000

private class DisplayedRoundGame(val pairing: Pairing, val match: ChessMatch)

@Composable
fun MultiBotTournamentView(tournament: MultiBotTournament) {

    var ranked by remember { mutableStateOf(tournament.standings().ranked) }
    var completed by remember { mutableStateOf(0) }
    var round by remember { mutableStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }

    var displayed by remember { mutableStateOf<DisplayedRoundGame?>(null) }
    var lastHandled by remember { mutableStateOf<Pairing?>(null) }

    LaunchedEffect(Unit) {
        tournament.runHeadlessWorkers()
    }

    // Poll the runner for the reserved display game and pick it up when a new round provides one
    LaunchedEffect(Unit) {
        while (true) {
            val pairing = tournament.currentDisplayPairing()
            if (pairing != null && pairing != lastHandled && displayed?.pairing != pairing) {
                displayed = DisplayedRoundGame(pairing, buildDisplayMatch(tournament, pairing))
            }
            if (tournament.isComplete) break
            delay(100.milliseconds)
        }
    }

    // Poll live standings so the table updates as games finish
    LaunchedEffect(Unit) {
        while (true) {
            ranked = tournament.standings().ranked
            completed = tournament.completedGames
            round = tournament.currentRound
            isComplete = tournament.isComplete
            if (isComplete) break
            delay(150.milliseconds)
        }
        ranked = tournament.standings().ranked
        completed = tournament.completedGames
        round = tournament.currentRound
        isComplete = tournament.isComplete
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xff1f3b16))) {

        Box(modifier = Modifier.weight(1f)) {

            val game = displayed
            if (game != null) {
                key(game.pairing.round, game.pairing.board) {
                    BoardView(
                        match = game.match,
                        showEvaluationBar = true,
                        showResultDialog = false,
                        onGameOver = { result ->
                            lastHandled = game.pairing
                            tournament.recordDisplayOutcome(game.pairing, result)
                            displayed = null
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xff2d6e1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isComplete) "Tournament complete" else "Running round…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        StandingsPanel(
            tournament = tournament,
            ranked = ranked,
            completed = completed,
            round = round,
            isComplete = isComplete
        )
    }
}

@Composable
private fun StandingsPanel(
    tournament: MultiBotTournament,
    ranked: List<ContenderRecord>,
    completed: Int,
    round: Int,
    isComplete: Boolean
) {

    val plannedRounds = tournament.plannedRounds

    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = if (isComplete) "Final Standings" else tournament.format.name,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        val roundText = when {
            isComplete -> "$completed games played"
            plannedRounds != null -> "Round ${round + 1} / $plannedRounds  ·  $completed games"
            else -> "Round ${round + 1}  ·  $completed games"
        }
        Text(
            text = roundText,
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )

        if (plannedRounds != null && plannedRounds > 0) {
            val completedRounds = if (isComplete) plannedRounds else round
            LinearProgressIndicator(
                progress = (completedRounds.toFloat() / plannedRounds).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFF0D9B5),
                backgroundColor = Color(0xFF3A3A3A)
            )
        }

        Spacer(Modifier.height(4.dp))

        StandingsHeaderRow()

        ranked.forEachIndexed { index, record ->
            StandingsRow(rank = index + 1, record = record)
        }
    }
}

@Composable
private fun StandingsHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("#", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.width(24.dp))
        Text("Bot", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Cell("Pts"); Cell("W"); Cell("D"); Cell("L")
    }
}

@Composable
private fun StandingsRow(rank: Int, record: ContenderRecord) {
    Card(
        shape = RoundedCornerShape(8.dp),
        backgroundColor = if (rank == 1) Color(0xFF38371F) else Color(0xFF2B2B2B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank",
                color = Color(0xFFBBBBBB),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(30.dp)
            )
            Text(
                text = record.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Cell("%.1f".format(record.points), bold = true)
            Cell("${record.wins}")
            Cell("${record.draws}")
            Cell("${record.losses}")
        }
    }
}

@Composable
private fun Cell(text: String, bold: Boolean = false) {
    Text(
        text = text,
        color = Color(0xFFDDDDDD),
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.width(40.dp)
    )
}

private fun buildDisplayMatch(tournament: MultiBotTournament, pairing: Pairing): ChessMatch {

    val displayTime = if (tournament.initialTimeMillis > 0) tournament.initialTimeMillis.toLong()
    else DISPLAY_TIME_MILLIS

    val white = pairing.white!!
    val black = pairing.black!!

    return ChessMatch(
        ChessGame(Board.getStartingBoard()),
        BotPlayer(white.createBot(Colour.WHITE), white.name),
        BotPlayer(black.createBot(Colour.BLACK), black.name),
        ClockManager(TimeControl(displayTime, 0))
    )
}
