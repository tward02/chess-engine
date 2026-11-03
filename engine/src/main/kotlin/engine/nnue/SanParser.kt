package com.tward.engine.nnue

import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import com.tward.uci.UciMoveCodec

/**
 * Resolves Standard Algebraic Notation ("Nbd7", "exd5", "e8=Q+", "O-O") against a position's legal
 * moves — what Lichess's game export uses. Annotations (`+ # ! ?`) are ignored.
 */
object SanParser {

    /** The legal move [san] denotes in [game], or null if it is malformed, illegal or ambiguous. */
    fun findMove(game: ChessGame, san: String): Move? {
        val clean = san.trim().trimEnd('+', '#', '!', '?')
        if (clean.isEmpty()) return null
        val legal = game.getLegalMoves()

        when (clean) {
            "O-O", "0-0" -> return legal.singleOrNull { it.isCastling && it.to.col == 6 }
            "O-O-O", "0-0-0" -> return legal.singleOrNull { it.isCastling && it.to.col == 2 }
        }

        var body = clean
        var promotion: PieceType? = null
        val eq = body.indexOf('=')
        if (eq >= 0) {
            promotion = pieceType(body.getOrNull(eq + 1) ?: return null) ?: return null
            body = body.substring(0, eq)
        }

        val pieceType = pieceType(body[0])?.takeIf { body[0].isUpperCase() }
        if (pieceType != null) body = body.substring(1)
        if (body.length < 2) return null
        val destination = body.takeLast(2)
        val disambiguation = body.dropLast(2).replace("x", "")

        val wanted = pieceType ?: PieceType.PAWN
        return legal.filter { move ->
            val uci = UciMoveCodec.encode(move)
            !move.isCastling &&
                    move.piece?.type == wanted &&
                    uci.substring(2, 4) == destination &&
                    move.promotionType == promotion &&
                    disambiguation.all { c -> if (c.isDigit()) uci[1] == c else uci[0] == c }
        }.singleOrNull()
    }

    private fun pieceType(c: Char): PieceType? = when (c.uppercaseChar()) {
        'N' -> PieceType.KNIGHT
        'B' -> PieceType.BISHOP
        'R' -> PieceType.ROOK
        'Q' -> PieceType.QUEEN
        'K' -> PieceType.KING
        else -> null
    }
}
