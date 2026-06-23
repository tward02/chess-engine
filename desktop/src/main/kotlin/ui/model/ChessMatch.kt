package com.tward.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tward.engine.board.*
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.Player
import com.tward.logging.Log

data class BoardUiState(
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val gameResult: GameResult? = null
)

data class AnimatingCapture(val square: Square, val piece: Piece)

class ChessMatch(
    val game: ChessGame,
    val whitePlayer: Player,
    val blackPlayer: Player,
    val clockManager: ClockManager
) {

    private val log = Log.of<ChessMatch>()

    var uiState by mutableStateOf(BoardUiState())
        private set

    var moveVersion by mutableIntStateOf(0)
        private set

    // Compose-observable mirror of the board's active colour; the board's own field is not Compose state
    var activeColour by mutableStateOf(game.board.activeColour)
        private set

    var animatingMove by mutableStateOf<Move?>(null)
        private set

    // The most recent move played, kept after its animation ends so the board can highlight the
    // from/to squares. Null until the first move of the game.
    var lastMove by mutableStateOf<Move?>(null)
        private set

    // Kept visible until the moving piece arrives; square differs from destination for en passant
    var animatingCapture by mutableStateOf<AnimatingCapture?>(null)
        private set

    var capturedByWhite by mutableStateOf<List<Piece>>(emptyList())
        private set

    var capturedByBlack by mutableStateOf<List<Piece>>(emptyList())
        private set

    val isAnimating: Boolean
        get() = animatingMove != null

    fun onAnimationFinished() {
        animatingMove = null
        animatingCapture = null
    }

    // Whether the side now to move is in check, i.e. the last move gave check
    fun sideToMoveInCheck(): Boolean {
        return game.isInCheck(game.board.activeColour)
    }

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

    // Unlike checkGameOver, reads the live clock (elapsed since move started) rather than the
    // stored snapshot, so flagging is detected immediately rather than on the next move.
    fun checkTimeout() {

        if (uiState.gameResult != null) return

        when {
            clockManager.currentWhite() <= 0 -> game.result = GameResult.BLACK_TIME_WIN
            clockManager.currentBlack() <= 0 -> game.result = GameResult.WHITE_TIME_WIN
            else -> return
        }

        clockManager.stopClock()
        uiState = BoardUiState(gameResult = game.result)
        log.info { "Game over: ${game.result}" }
    }


    fun makeMove(move: Move, animate: Boolean = true) {

        if (game.isGameOver()) return

        // Captured pawn is off the destination square, so detect en passant before applying
        val isEnPassant =
            move.piece?.type == PieceType.PAWN &&
                    move.capturedPiece != null &&
                    game.board.enPassantTarget == move.to

        game.makeMove(move)

        activeColour = game.board.activeColour

        val captured = move.capturedPiece

        if (captured != null) {
            if (captured.colour == Colour.BLACK) {
                capturedByWhite = capturedByWhite + captured
            } else {
                capturedByBlack = capturedByBlack + captured
            }
        }

        clockManager.onMovePlayed()

        lastMove = move
        moveVersion++

        clearSelection()

        // Drag-and-drop moves are placed directly by the player, so they don't animate
        animatingMove = if (animate) move else null

        // Keep the captured piece on screen until the moving piece reaches its square
        animatingCapture =
            if (animate && captured != null) {
                val capturedSquare =
                    if (isEnPassant) Square(move.to.col, move.from.row) else move.to
                AnimatingCapture(capturedSquare, captured)
            } else {
                null
            }

        checkGameOver()

        logMove(move, isEnPassant)
    }

    private fun logMove(move: Move, isEnPassant: Boolean) {

        val mover = move.piece?.colour ?: game.board.activeColour.opposite()
        val result = game.result
        val isCheckmate = result == GameResult.WHITE_WIN || result == GameResult.BLACK_WIN
        val gaveCheck = isCheckmate || game.isInCheck(game.board.activeColour)

        val description = MoveDescriber.describe(move, isEnPassant, gaveCheck, isCheckmate)

        log.info { "$mover played $description" }

        if (result != null) {
            log.info { "Game over: $result" }
        }
    }
}
