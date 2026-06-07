package com.tward.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tward.engine.board.Colour
import com.tward.engine.board.Square
import com.tward.engine.board.SquareType
import com.tward.engine.game.ChessMatch
import com.tward.engine.player.BotPlayer
import kotlinx.coroutines.delay
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BoardView(match: ChessMatch) {

    val version = match.moveVersion

    LaunchedEffect(match.game.board.activeColour) {

        val currentPlayer =
            if (match.game.board.activeColour == Colour.WHITE)
                match.whitePlayer
            else
                match.blackPlayer

        if (currentPlayer is BotPlayer && match.uiState.gameResult == null) {

            delay(200.milliseconds)

            val move =
                currentPlayer.bot.chooseMove(match.game)

            match.makeMove(move)
        }
    }

    Column {
        for (row in 0..7) {

            Row {

                for (col in 0..7) {

                    val square = Square(col, row)
                    val piece = match.game.board.getPiece(square)
                    val isSelected =
                        match.uiState.selectedSquare == square

                    val isLegalTarget =
                        square in match.uiState.legalTargets

                    val type = square.getSquareType()

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                when {
                                    isSelected -> Color.Yellow
                                    isLegalTarget -> Color.Green
                                    type == SquareType.LIGHT ->
                                        Color(0xFFF0D9B5)
                                    else ->
                                        Color(0xFFB58863)
                                }
                            ).clickable(enabled = match.uiState.gameResult == null) {

                                val selected =
                                    match.uiState.selectedSquare

                                if (selected == null) {

                                    val piece =
                                        match.game.board.getPiece(square)

                                    if (
                                        piece != null &&
                                        piece.colour ==
                                        match.game.board.activeColour
                                    ) {
                                        match.select(square)
                                    }

                                } else {

                                    val move =
                                        match.game
                                            .legalMoves()
                                            .firstOrNull {
                                                it.from == selected &&
                                                        it.to == square
                                            }

                                    if (move != null) {
                                        match.makeMove(move)
                                    } else {
                                        match.clearSelection()
                                    }
                                }

                            },
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = piece?.toUnicode() ?: "",
                            fontSize = 40.sp
                        )
                    }
                }
            }
        }

        if (match.uiState.gameResult != null) {


            AlertDialog(onDismissRequest = { }, text = {
                Text(text = match.uiState.gameResult?.toString() ?: "")
            }, confirmButton = {
                TextButton(
                    onClick = {
                        exitProcess(0)
                    }
                ) {
                    Text("Close Game")
                }
            })

        }
    }
}
