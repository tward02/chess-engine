package com.tward.engine.game

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType

class ChessGame(val board: Board) {

    var result: GameResult? = null

    private var positionHistory = mutableListOf<String>()

    fun getLegalMoves(): List<Move> {
        return MoveGenerator(board).generateLegalMoves()
    }

    fun makeMove(move: Move) {
        if (move.piece?.colour != board.activeColour) {
            throw IllegalArgumentException("Cannot move piece of colour ${move.piece?.colour}, active colour is ${board.activeColour}")
        }
        board.makeMove(move)

        positionHistory.add(board.toFEN(isFullFEN = false))
    }

    fun undoMove(move: Move) {
        board.undoMove(move)
        positionHistory.removeLast()
    }

    fun getGameResult(): GameResult? {
        val legalMoves = getLegalMoves()

        if (legalMoves.isEmpty()) {

            val inCheck = isInCheck(board.activeColour)

            return if (inCheck) {
                if (board.activeColour == Colour.WHITE) {
                    GameResult.BLACK_WIN
                } else {
                    GameResult.WHITE_WIN
                }
            } else {
                GameResult.DRAW_STALEMATE
            }
        }

        if (isInsufficientMaterial()) {
            return GameResult.DRAW_INSUFFICIENT_MATERIAL
        }

        if (isFiftyMoveRule()) {
            return GameResult.DRAW_50_MOVE_RULE
        }

        if (isThreefoldRepetition()) {
            return GameResult.DRAW_THREEFOLD_REPETITION
        }

        return null
    }

    fun isInsufficientMaterial(): Boolean {
        val pieces = board.getPieces()

        val nonKings = pieces.filter { it.type != PieceType.KING }

        if (nonKings.isEmpty()) {
            return true
        }

        if (nonKings.size == 1 && (nonKings[0].type == PieceType.BISHOP || nonKings[0].type == PieceType.KNIGHT)) {
            return true
        }

        if (nonKings.size == 2 && nonKings.all { it.type == PieceType.BISHOP } && nonKings[0].colour != nonKings[1].colour) {
            val piece1Location = board.findPiece(nonKings[0])
            val piece2Location = board.findPiece(nonKings[1])

            if (piece1Location != null && piece2Location != null && piece1Location.getSquareType() == piece2Location.getSquareType()) {
                return true
            }
        }

        return false
    }

    fun isInCheck(colour: Colour): Boolean {
        val kingSquare = board.findKing(colour)
        return MoveGenerator(board).isSquareAttacked(kingSquare, colour.opposite())
    }

    fun isFiftyMoveRule(): Boolean {
        return board.halfMoveClock >= 100
    }

    fun isThreefoldRepetition(): Boolean {
        val current = board.toFEN(isFullFEN = false)

        return positionHistory.count {
            it == current
        } >= 3
    }

    fun isGameOver(): Boolean {

        if (result != null) {
            return true
        }

        result = getGameResult()
        return result != null
    }

    fun findMove(from: String, to: String): Move? {
        return MoveGenerator(board).generateLegalMoves().firstOrNull { it.from.toString() == from && it.to.toString() == to }
    }

    fun copy(): ChessGame {
        val game = ChessGame(board.copy())
        game.positionHistory = positionHistory.toMutableList()
        game.result = result
        return game
    }
}

enum class GameResult {

    WHITE_WIN {
        override fun toString(): String {
            return "White Wins by Checkmate!"
        }
    },
    BLACK_WIN {
        override fun toString(): String {
            return "Black Wins by Checkmate!"
        }
    },
    WHITE_TIME_WIN {
        override fun toString(): String {
            return "White Wins in Time!"
        }
    },
    BLACK_TIME_WIN {
        override fun toString(): String {
            return "Black Wins on Time!"
        }
    },
    DRAW_STALEMATE {
        override fun isDraw(): Boolean {
            return true
        }

        override fun toString(): String {
            return "Stale Mate"
        }
    },
    DRAW_INSUFFICIENT_MATERIAL {
        override fun isDraw(): Boolean {
            return true
        }

        override fun toString(): String {
            return "Insufficient Material"
        }
    },
    DRAW_50_MOVE_RULE {
        override fun isDraw(): Boolean {
            return true
        }

        override fun toString(): String {
            return "50 Move Rule"
        }
    },
    DRAW_THREEFOLD_REPETITION {
        override fun isDraw(): Boolean {
            return true
        }

        override fun toString(): String {
            return "Threefold Repetition"
        }
    },
    DRAW_AGREED {
        override fun isDraw(): Boolean {
            return true
        }

        override fun toString(): String {
            return "Draw agreed between players"
        }
    },
    WHITE_RESIGNATION {
        //Black win
        override fun toString(): String {
            return "White Resigned"
        }
    },
    BLACK_RESIGNATION {
        //White win
        override fun toString(): String {
            return "Black Resigned"
        }
    };

    open fun isDraw(): Boolean {
        return false
    }
}
