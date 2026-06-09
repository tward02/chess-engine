package com.tward.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tward.engine.board.Move
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.Player

data class BoardUiState(
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val gameResult: GameResult? = null
)

class ChessMatch(
    val game: ChessGame,
    val whitePlayer: Player,
    val blackPlayer: Player,
    val clockManager: ClockManager
) {

    var uiState by mutableStateOf(BoardUiState())
        private set

    var moveVersion by mutableIntStateOf(0)
        private set

    fun select(square: Square) {

        if (game.isGameOver()) return

        val moves =
            game.getLegalMoves()
                .filter { it.from == square }

        uiState =
            BoardUiState(
                selectedSquare = square,
                legalTargets =
                    moves.map { it.to }.toSet()
            )
    }

    fun clearSelection() {
        uiState = BoardUiState()
    }

    fun checkGameOver() {

        if (clockManager.whiteMillis <= 0) {
            game.result = GameResult.BLACK_TIME_WIN
        }

        if (clockManager.blackMillis <= 0) {
            game.result = GameResult.WHITE_TIME_WIN
        }

        if (game.isGameOver()) {
            clockManager.stopClock()
            uiState = BoardUiState(gameResult = game.result)
        }
    }



    fun makeMove(move: Move) {

        if (game.isGameOver()) return

        game.makeMove(move)

        clockManager.onMovePlayed()

        moveVersion++

        clearSelection()

        checkGameOver()
    }
}
