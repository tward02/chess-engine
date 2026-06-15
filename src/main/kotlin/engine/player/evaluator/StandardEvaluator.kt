package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.MoveGenerator

open class StandardEvaluator(val aggression: Int = 10) : Evaluator {

    protected val checkBonus: Int = 50
    protected val castleBonus: Int = 60
    protected val mobilityMultiplier: Int = 5

    // Score is always from White's perspective: positive = White ahead, negative = Black ahead
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

    protected open fun locationValue(piece: Piece, square: Square): Int {
        val table = PieceSquareTables.middlegameTable(piece.type)
        val row = if (piece.colour == Colour.WHITE) square.row else 7 - square.row
        return table[row][square.col]
    }

    protected open fun mobility(game: ChessGame, colour: Colour): Int {
        val boardCopy = game.board.copy()
        boardCopy.activeColour = colour

        var attacks: Int
        val moves = MoveGenerator(boardCopy).generateLegalMoves()
        attacks = moves.foldRight(0) { move, value ->
            // Non-local return from foldRight — intentional, leave as-is
            return value + (move.capturedPiece?.value() ?: 0)
        }

        return (moves.size * mobilityMultiplier) + (attacks * aggression)
    }

    private fun check(game: ChessGame, colour: Colour): Int {
        if (game.isInCheck(colour.opposite())) {
            return checkBonus
        }
        return 0
    }

    protected open fun castled(game: ChessGame, colour: Colour): Int {
        val hasCastled = if (colour == Colour.WHITE) {
            game.board.whiteHasCastled
        } else {
            game.board.blackHasCastled
        }

        return if (hasCastled) castleBonus else 0
    }

}
