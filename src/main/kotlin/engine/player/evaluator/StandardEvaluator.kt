package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.MoveGenerator

private const val CHECK_BONUS: Int = 50
private const val CASTLE_BONUS: Int = 60
private const val MOBILITY_MULTIPLIER: Int = 5

// Location modifier tables, laid out to match the board grid: row 0 is rank 8, row 7 is rank 1,
// values from White's perspective (mirrored vertically for Black)
private val PAWN_TABLE = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
    intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
    intArrayOf(5, 5, 10, 25, 25, 10, 5, 5),
    intArrayOf(0, 0, 0, 20, 20, 0, 0, 0),
    intArrayOf(5, -5, -10, 0, 0, -10, -5, 5),
    intArrayOf(5, 10, 10, -20, -20, 10, 10, 5),
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
)

private val KNIGHT_TABLE = arrayOf(
    intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50),
    intArrayOf(-40, -20, 0, 0, 0, 0, -20, -40),
    intArrayOf(-30, 0, 10, 15, 15, 10, 0, -30),
    intArrayOf(-30, 5, 15, 20, 20, 15, 5, -30),
    intArrayOf(-30, 0, 15, 20, 20, 15, 0, -30),
    intArrayOf(-30, 5, 10, 15, 15, 10, 5, -30),
    intArrayOf(-40, -20, 0, 5, 5, 0, -20, -40),
    intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50)
)

private val BISHOP_TABLE = arrayOf(
    intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20),
    intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
    intArrayOf(-10, 0, 5, 10, 10, 5, 0, -10),
    intArrayOf(-10, 5, 5, 10, 10, 5, 5, -10),
    intArrayOf(-10, 0, 10, 10, 10, 10, 0, -10),
    intArrayOf(-10, 10, 10, 10, 10, 10, 10, -10),
    intArrayOf(-10, 5, 0, 0, 0, 0, 5, -10),
    intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20)
)

private val ROOK_TABLE = arrayOf(
    intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(5, 10, 10, 10, 10, 10, 10, 5),
    intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
    intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
    intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
    intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
    intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
    intArrayOf(0, 0, 0, 5, 5, 0, 0, 0)
)

private val QUEEN_TABLE = arrayOf(
    intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20),
    intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
    intArrayOf(-10, 0, 5, 5, 5, 5, 0, -10),
    intArrayOf(-5, 0, 5, 5, 5, 5, 0, -5),
    intArrayOf(0, 0, 5, 5, 5, 5, 0, -5),
    intArrayOf(-10, 5, 5, 5, 5, 5, 0, -10),
    intArrayOf(-10, 0, 5, 0, 0, 0, 0, -10),
    intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20)
)

private val KING_TABLE = arrayOf(
    intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
    intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
    intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
    intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
    intArrayOf(-20, -30, -30, -40, -40, -30, -30, -20),
    intArrayOf(-10, -20, -20, -20, -20, -20, -20, -10),
    intArrayOf(20, 20, 0, 0, 0, 0, 20, 20),
    intArrayOf(20, 30, 10, 0, 0, 10, 30, 20)
)

class StandardEvaluator(val useNew: Boolean = true) : Evaluator {

    // Score is always from White's perspective: positive favours White, negative favours Black
    override fun evaluate(game: ChessGame, depth: Int): Int {
        return scorePlayer(game, Colour.WHITE) - scorePlayer(game, Colour.BLACK)
    }

    private fun scorePlayer(game: ChessGame, colour: Colour): Int {
        return pieceValue(game, colour) + mobility(game, colour) + check(game, colour) + castled(game, colour)
    }

    private fun pieceValue(game: ChessGame, colour: Colour): Int {
        var total = 0
        game.board.getPiecesWithSquares()
            .filter { (_, piece) -> piece.colour == colour }
            .forEach { (square, piece) ->
                total += materialValue(piece.type) + locationValue(piece, square)
            }
        return total
    }

    private fun materialValue(type: PieceType): Int {
        return when (type) {
            PieceType.PAWN -> 100
            PieceType.KNIGHT -> 320
            PieceType.BISHOP -> 330
            PieceType.ROOK -> 500
            PieceType.QUEEN -> 900
            PieceType.KING -> 0
        }
    }

    private fun locationValue(piece: Piece, square: Square): Int {
        val table = when (piece.type) {
            PieceType.PAWN -> PAWN_TABLE
            PieceType.KNIGHT -> KNIGHT_TABLE
            PieceType.BISHOP -> BISHOP_TABLE
            PieceType.ROOK -> ROOK_TABLE
            PieceType.QUEEN -> QUEEN_TABLE
            PieceType.KING -> KING_TABLE
        }

        val row = if (piece.colour == Colour.WHITE) square.row else 7 - square.row
        return table[row][square.col]
    }

    private fun mobility(game: ChessGame, colour: Colour): Int {
        val boardCopy = game.board.copy()
        boardCopy.activeColour = colour

        var attacks = 0
        val moves = MoveGenerator(boardCopy).generateLegalMoves()
        if (useNew) {
            attacks = moves.foldRight(0) { move, value ->
                return value + (move.capturedPiece?.value() ?: 0)
            }
        }

        return (moves.size * MOBILITY_MULTIPLIER) + (attacks * MOBILITY_MULTIPLIER)
    }

    private fun check(game: ChessGame, colour: Colour): Int {
        if (game.isInCheck(colour.opposite())) {
            return CHECK_BONUS
        }
        return 0
    }

    private fun castled(game: ChessGame, colour: Colour): Int {
        val hasCastled = if (colour == Colour.WHITE) {
            game.board.whiteHasCastled
        } else {
            game.board.blackHasCastled
        }

        return if (hasCastled) CASTLE_BONUS else 0
    }

}
