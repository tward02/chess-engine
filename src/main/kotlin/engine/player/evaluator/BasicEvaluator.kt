package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame

class BasicEvaluator : Evaluator {

    //TODO implement evaluator from old chess project

    override fun evaluate(game: ChessGame, depth: Int): Int {

        var score = 0

        for (piece in game.board.getPieces()) {

            val value =
                when (piece.type) {
                    PieceType.PAWN -> 100
                    PieceType.KNIGHT -> 320
                    PieceType.BISHOP -> 330
                    PieceType.ROOK -> 500
                    PieceType.QUEEN -> 900
                    PieceType.KING -> 0
                }

            score += if (piece.colour == Colour.WHITE) {
                value
            } else {
                -value
            }
        }

        return if (game.board.activeColour == Colour.WHITE) {
            score
        } else {
            -score
        }
    }
}