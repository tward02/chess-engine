package com.tward.client.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.client.model.ChessClient
import com.tward.client.view.Utils.Companion.subtle
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Square
import com.tward.shared.GameStateDto
import com.tward.shared.GameStatus
import com.tward.ui.board.*

private const val SQUARE_SIZE = 72
private const val BOARD_WIDTH = SQUARE_SIZE * 8

@Composable
fun GameScreen(client: ChessClient) {
    var showEndDialog by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val game = client.game
        if (game == null) {
            Text("Loading game…")
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { client.leaveGame() }) { Text("← Lobby") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { client.resign() }, enabled = game.status == GameStatus.IN_PROGRESS) { Text("Resign") }
            Spacer(Modifier.width(16.dp))
            Text(client.status, color = subtle())
        }
        Spacer(Modifier.height(12.dp))

        val captured = remember(game.fen) { capturedMaterial(Board.fromFEN(game.fen)) }
        val topColour = if (client.myColour == "white") "black" else "white"

        val headline = if (game.status == GameStatus.IN_PROGRESS) {
            "You are ${client.myColour}  ·  ${game.sideToMove} to move"
        } else {
            game.outcomeText()
        }
        Text(headline, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))

        Column(Modifier.width(BOARD_WIDTH.dp)) {
            PlayerPanel(topColour, game, captured)
            Spacer(Modifier.height(6.dp))
            BoardGrid(client, game.fen, game.lastMove)
            Spacer(Modifier.height(6.dp))
            PlayerPanel(client.myColour, game, captured)
        }

        if (game.status != GameStatus.IN_PROGRESS && showEndDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = if (game.status == GameStatus.DRAW) "Draw" else "Game Over")
                },
                text = {
                    Text(text = game.outcomeText())
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEndDialog = false
                        }
                    ) {
                        Text("Close")
                    }
                })
        }
    }
}

@Composable
private fun PlayerPanel(colourName: String, game: GameStateDto, captured: CapturedMaterial) {
    val isWhite = colourName == "white"
    val millis = if (isWhite) game.whiteMillis else game.blackMillis
    val running = game.status == GameStatus.IN_PROGRESS && game.sideToMove == colourName
    val capturedPieces = if (isWhite) captured.capturedByWhite else captured.capturedByBlack
    val advantage = if (isWhite) captured.whiteAdvantage else -captured.whiteAdvantage

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(colourName.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            CapturedPieces(capturedPieces, maxOf(0, advantage))
        }
        GameClock(millis = millis, running = running, label = "")
    }
}

@Composable
private fun BoardGrid(client: ChessClient, fen: String, lastMove: String?) {
    val board = remember(fen) { Board.fromFEN(fen) }
    ChessBoardView(
        pieceAt = { board.getPiece(it) },
        orientation = if (client.myColour == "black") Colour.BLACK else Colour.WHITE,
        squareSize = SQUARE_SIZE,
        selected = client.selected,
        lastMoveFrom = lastMove?.let { Square.fromString(it.take(2)) },
        lastMoveTo = lastMove?.let { Square.fromString(it.substring(2, 4)) },
        onSquareClick = { client.clickSquare(it) }
    )

    PromotionPopupView(client.promotionMoves) {
        client.makePromotionMove(it)
    }
}
