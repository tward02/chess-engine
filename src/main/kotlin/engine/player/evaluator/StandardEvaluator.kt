package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.game.MoveGenerator

private const val CHECK_BONUS: Int = 50
private const val CHECK_MATE_BONUS: Int = 10000
private const val DEPTH_BONUS: Int = 100
private const val CASTLE_BONUS: Int = 60

class StandardEvaluator: Evaluator {

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val score = scorePlayer(game, Colour.WHITE, depth) - scorePlayer(game, Colour.BLACK, depth)
        return if (game.board.activeColour == Colour.WHITE) {
            score
        } else {
            -score
        }
    }

    private fun scorePlayer(game: ChessGame, colour: Colour, depth: Int): Int {
        return pieceValue(game, colour) + mobility(game, colour) + check(game, colour) + castled(game, colour)
    }

    private fun pieceValue(game: ChessGame, colour: Colour): Int {
        var total = 0
        game.board.getPieces().filter { it.colour == colour }.forEach { piece ->

            val value =
                when (piece.type) {
                    PieceType.PAWN -> 100
                    PieceType.KNIGHT -> 320
                    PieceType.BISHOP -> 330
                    PieceType.ROOK -> 500
                    PieceType.QUEEN -> 900
                    PieceType.KING -> 0
                }
            total += value
        }
        return total
    }

    private fun mobility(game: ChessGame, colour: Colour): Int {
        val boardCopy = game.board.copy()
        boardCopy.activeColour = colour

        return MoveGenerator(boardCopy)
            .generateLegalMoves()
            .size * 5
    }

    private fun check(game: ChessGame, colour: Colour): Int {
        if (game.isInCheck(colour.opposite())) {
            return CHECK_BONUS
        }
        return 0
    }

    private fun castled(game: ChessGame, colour: Colour): Int {
        //TODO need mechanism to work out if castled
        //TODO position scores on board UI
        //TODO ab pruning
        //TODO expand evaluation functions with location modifiers
//        if (colour == Colour.WHITE) {
//            game.board.
//        } else {

//        }
        return 0
    }

}
