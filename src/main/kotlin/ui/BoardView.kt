package com.tward.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tward.engine.board.*
import com.tward.engine.game.ChessMatch
import com.tward.engine.player.BotPlayer
import kotlinx.coroutines.delay
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoardView(match: ChessMatch) {

    val version = match.moveVersion
    val squareSize = 80

    var optionalMoves by remember {
        mutableStateOf<List<Move>>(emptyList())
    }

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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(
            Color(0xff2d6e1f)
        )
    ) {

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
                                .size(squareSize.dp)
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

                                        val moves =
                                            match.game
                                                .legalMoves()
                                                .filter {
                                                    it.from == selected &&
                                                            it.to == square
                                                }


                                        if (moves.size == 1) {
                                            match.makeMove(moves[0])
                                        } else {
                                            if (moves.isNotEmpty()) {
                                                optionalMoves = moves.filter { it.promotionType != null }
                                            } else {
                                                match.clearSelection()
                                            }
                                        }
                                    }

                                },
                            contentAlignment = Alignment.Center
                        ) {

                            PieceView(piece)

                        }
                    }
                }
            }

            if (match.uiState.gameResult != null) {

                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(text = if (match.uiState.gameResult?.isDraw() ?: false) "Draw" else "Game Over")
                    },
                    text = {
                        Text(text = match.uiState.gameResult?.toString() ?: "")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                exitProcess(0)
                            }
                        ) {
                            Text("Close Game")
                        }
                    })
            }

            if (optionalMoves.isNotEmpty()) {

                Dialog(onDismissRequest = {}) {

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        elevation = 12.dp,
                        backgroundColor = Color(0xFF2B2B2B),
                        modifier = Modifier.width(420.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                text = "Choose Promotion",
                                color = Color.White
                            )

                            Spacer(Modifier.height(20.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {

                                val orderedMoves =
                                    optionalMoves.sortedBy {
                                        when (it.promotionType) {
                                            PieceType.QUEEN -> 0
                                            PieceType.ROOK -> 1
                                            PieceType.BISHOP -> 2
                                            PieceType.KNIGHT -> 3
                                            else -> 99
                                        }
                                    }

                                orderedMoves.forEach { move ->
                                    var hovered by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (hovered)
                                                    Color(0xFF505050)
                                                else
                                                    Color(0xFF3A3A3A)
                                            )

                                            .background(Color(0xFF3A3A3A))
                                            .border(
                                                2.dp,
                                                Color(0xFF606060),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                optionalMoves = emptyList()
                                                match.makeMove(move)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {

                                        move.promotionType?.let { type ->

                                            PieceView(
                                                Piece(
                                                    type,
                                                    move.piece!!.colour
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
