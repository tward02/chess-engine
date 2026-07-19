package com.tward.engine.game

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType

class ChessGame(val board: Board) {

    var result: GameResult? = null

    // Zobrist hash of the position after each move, as an unboxed stack. Hash equality stands in
    // for the FEN equality the repetition rule wants: the key covers exactly the fields a
    // clock-less FEN prints (pieces, side to move, castling rights, en-passant file).
    private var positionHistory = LongArray(256)
    private var positionCount = 0

    fun getLegalMoves(): List<Move> {
        return MoveGenerator(board).generateLegalMoves()
    }

    fun makeMove(move: Move) {
        if (move.piece?.colour != board.activeColour) {
            throw IllegalArgumentException("Cannot move piece of colour ${move.piece?.colour}, active colour is ${board.activeColour}")
        }
        board.makeMove(move)

        if (positionCount == positionHistory.size) {
            positionHistory = positionHistory.copyOf(positionHistory.size * 2)
        }
        positionHistory[positionCount++] = board.zobristKey
    }

    fun undoMove(move: Move) {
        board.undoMove(move)
        positionCount--
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
        // Allocation-free scan — this runs at every search node. Draws: K vs K, K vs K+minor, and
        // KB vs KB with both bishops on the same square colour.
        var nonKings = 0
        var minors = 0
        var whiteBishops = 0
        var blackBishops = 0
        var bishopSquareParitySum = 0

        board.forEachPiece { col, row, piece ->
            when (piece.type) {
                PieceType.KING -> {}
                PieceType.BISHOP -> {
                    nonKings++
                    minors++
                    if (piece.colour == Colour.WHITE) whiteBishops++ else blackBishops++
                    bishopSquareParitySum += (col + row) and 1
                }
                PieceType.KNIGHT -> {
                    nonKings++
                    minors++
                }
                else -> nonKings++
            }
        }

        if (nonKings == 0) return true
        if (nonKings == 1 && minors == 1) return true
        // Opposite-coloured armies of one bishop each, on same-coloured squares (parity sum 0 or 2).
        return nonKings == 2 && whiteBishops == 1 && blackBishops == 1 && bishopSquareParitySum != 1
    }

    fun isInCheck(colour: Colour): Boolean {
        val kingSquare = board.findKing(colour)
        return MoveGenerator(board).isSquareAttacked(kingSquare, colour.opposite())
    }

    fun isFiftyMoveRule(): Boolean {
        return board.halfMoveClock >= 100
    }

    fun isThreefoldRepetition(): Boolean {
        val current = board.zobristKey
        var occurrences = 0
        for (i in 0 until positionCount) {
            if (positionHistory[i] == current) occurrences++
        }
        return occurrences >= 3
    }

    /**
     * Whether the current position has occurred at least once before in the game (a twofold
     * repetition). Search code treats this as a draw score: if the opponent allowed the position
     * once already, the full threefold claim is available on demand. Only the last
     * [Board.halfMoveClock] plies can repeat (any capture or pawn move is irreversible), and equal
     * positions must have the same side to move, so the scan is short and strides by two.
     */
    fun isRepetition(): Boolean {
        if (positionCount < 3) return false
        val current = positionHistory[positionCount - 1]
        val oldest = maxOf(0, positionCount - 1 - board.halfMoveClock)
        var i = positionCount - 3
        while (i >= oldest) {
            if (positionHistory[i] == current) return true
            i -= 2
        }
        return false
    }

    fun isGameOver(): Boolean {

        if (result != null) {
            return true
        }

        result = getGameResult()
        return result != null
    }

    fun findMove(from: String, to: String): Move? {
        return MoveGenerator(board).generateLegalMoves()
            .firstOrNull { it.from.toString() == from && it.to.toString() == to }
    }

    fun copy(): ChessGame {
        val game = ChessGame(board.copy())
        game.positionHistory = positionHistory.copyOf()
        game.positionCount = positionCount
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
            return "White Wins on Time!"
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
