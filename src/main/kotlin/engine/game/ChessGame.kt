package com.tward.engine.game

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move

class ChessGame(val board: Board) {

    var result: GameResult? = null

    fun legalMoves(): List<Move> {
        return MoveGenerator(board).generateLegalMoves()
    }

    fun makeMove(move: Move) {
        if (move.piece?.colour != board.activeColour) {
            throw IllegalArgumentException("Cannot move piece of colour ${move.piece?.colour}, active colour is ${board.activeColour}")
        }
        board.makeMove(move)
    }

    fun isGameOver(): Boolean {

        if (legalMoves().isEmpty()) {
            result = if (board.activeColour == Colour.WHITE) {
                GameResult.BLACK_WINS
            } else {
                GameResult.WHITE_WINS
            }
            return true
        }

        // TODO draw logic

        result = null
        return false
    }

}

enum class GameResult {
    WHITE_WINS {
        override fun toString(): String {
            return "White Wins!"
        }
    },
    BLACK_WINS {
        override fun toString(): String {
            return "Black Wins!"
        }
    },
    DRAW {
        override fun toString(): String {
            return "Draw"
        }
    }
}
