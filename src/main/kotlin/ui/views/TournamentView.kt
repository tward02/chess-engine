package com.tward.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tward.engine.tournament.Tournament
import com.tward.ui.model.ChessMatch
import com.tward.ui.model.ClockManager
import com.tward.ui.model.TimeControl
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// The displayed game gets a long clock purely so it isn't ended on time; tournament
// results come from the chess outcome, not the clock
private const val DISPLAY_TIME_MILLIS = 60L * 60 * 1000

private class DisplayedGame(val index: Int, val match: ChessMatch)

@Composable
fun TournamentView(tournament: Tournament) {

    // Live snapshot of the thread-safe tally, polled for display
    var aWins by remember { mutableStateOf(0) }
    var bWins by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }
    var completed by remember { mutableStateOf(0) }
    var whiteWins by remember { mutableStateOf(0) }
    var blackWins by remember { mutableStateOf(0) }

    // The game currently shown on screen, or null once none remain to display
    var displayed by remember { mutableStateOf<DisplayedGame?>(null) }

    fun startNextDisplayedGame() {
        val index = tournament.claimGameIndex()
        displayed = if (index < 0) null else DisplayedGame(index, buildDisplayMatch(tournament, index))
    }

    LaunchedEffect(Unit) {
        // Reserve a game to watch before the headless workers drain the pool
        startNextDisplayedGame()
        tournament.runHeadlessWorkers()
    }

    LaunchedEffect(Unit) {
        while (true) {
            aWins = tournament.botAWins
            bWins = tournament.botBWins
            draws = tournament.drawCount
            completed = tournament.completedGames
            whiteWins = tournament.whiteWinsCount
            blackWins = tournament.blackWinsCount
            if (completed >= tournament.totalGames) break
            delay(150.milliseconds)
        }
        aWins = tournament.botAWins
        bWins = tournament.botBWins
        draws = tournament.drawCount
        completed = tournament.completedGames
        whiteWins = tournament.whiteWinsCount
        blackWins = tournament.blackWinsCount
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xff1f3b16))) {

        Box(modifier = Modifier.weight(1f)) {

            val game = displayed
            if (game != null) {
                // Keyed on the index so each game gets a fresh board with reset state
                key(game.index) {
                    BoardView(
                        match = game.match,
                        showEvaluationBar = true,
                        showResultDialog = false,
                        onGameOver = { result ->
                            tournament.record(game.index, result)
                            startNextDisplayedGame()
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xff2d6e1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (completed >= tournament.totalGames) "Tournament complete" else "Running games…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        ResultsPanel(
            tournament = tournament,
            aWins = aWins,
            bWins = bWins,
            draws = draws,
            completed = completed,
            whiteWins = whiteWins,
            blackWins = blackWins
        )
    }
}

@Composable
private fun ResultsPanel(
    tournament: Tournament,
    aWins: Int,
    bWins: Int,
    draws: Int,
    completed: Int,
    whiteWins: Int,
    blackWins: Int
) {

    val total = tournament.totalGames
    val isComplete = completed >= total

    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = if (isComplete) "Final Results" else "Tournament",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "$completed / $total games",
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )

        LinearProgressIndicator(
            progress = if (total == 0) 0f else completed / total.toFloat(),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Color(0xFFF0D9B5),
            backgroundColor = Color(0xFF3A3A3A)
        )

        Spacer(Modifier.height(8.dp))

        BotScoreCard(
            name = tournament.specA.name,
            wins = aWins,
            draws = draws,
            losses = bWins,
            accent = Color(0xFFF0F0F0)
        )

        BotScoreCard(
            name = tournament.specB.name,
            wins = bWins,
            draws = draws,
            losses = aWins,
            accent = Color(0xFF6E6E6E)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Draws: $draws",
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp
        )

        Text(
            text = "White wins: $whiteWins",
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp
        )

        Text(
            text = "Black wins: $blackWins",
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun BotScoreCard(name: String, wins: Int, draws: Int, losses: Int, accent: Color) {

    // A win is a point, a draw is half a point
    val score = wins + draws * 0.5

    Card(
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color(0xFF2B2B2B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = name,
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${"%.1f".format(score)} pts",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "W $wins  D $draws  L $losses",
                color = Color(0xFFBBBBBB),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun buildDisplayMatch(tournament: Tournament, index: Int): ChessMatch {

    val (whiteSpec, blackSpec) = tournament.colourAssignment(index)

    // Use tournament time if set, otherwise fall back to the long display-only clock
    val displayTime = if (tournament.initialTimeMillis > 0) tournament.initialTimeMillis.toLong()
                      else DISPLAY_TIME_MILLIS

    return ChessMatch(
        ChessGame(Board.getStartingBoard()),
        BotPlayer(whiteSpec.createBot(Colour.WHITE), whiteSpec.name),
        BotPlayer(blackSpec.createBot(Colour.BLACK), blackSpec.name),
        ClockManager(TimeControl(displayTime, 0))
    )
}
