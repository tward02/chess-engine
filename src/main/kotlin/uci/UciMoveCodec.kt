package com.tward.uci

import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame

/**
 * Translates between the engine's [Move] model and UCI long algebraic notation
 * (e.g. "e2e4", "e7e8q", "e1g1" for castling). UCI never carries piece/captured
 * metadata, so [findMove] resolves a string against the position's legal moves —
 * the single move whose from/to (and promotion, if any) match.
 */
object UciMoveCodec {

    /** Encodes a [Move] to a UCI string the GUI/Lichess expects. */
    fun encode(move: Move): String {
        val promotion = move.promotionType?.let { promotionChar(it) } ?: ""
        return "${move.from}${move.to}$promotion"
    }

    /**
     * Resolves a UCI move string against the legal moves of [game]. Returns null
     * if the string is malformed or no legal move matches (e.g. illegal input).
     */
    fun findMove(game: ChessGame, uci: String): Move? {
        if (uci.length < 4) {
            return null
        }

        val from = uci.substring(0, 2)
        val to = uci.substring(2, 4)
        val promotion = uci.getOrNull(4)?.let { promotionType(it) }

        return game.getLegalMoves().firstOrNull { move ->
            move.from.toString() == from &&
                move.to.toString() == to &&
                move.promotionType == promotion
        }
    }

    private fun promotionChar(type: PieceType): Char = when (type) {
        PieceType.QUEEN -> 'q'
        PieceType.ROOK -> 'r'
        PieceType.BISHOP -> 'b'
        PieceType.KNIGHT -> 'n'
        else -> 'q'
    }

    private fun promotionType(char: Char): PieceType? = when (char.lowercaseChar()) {
        'q' -> PieceType.QUEEN
        'r' -> PieceType.ROOK
        'b' -> PieceType.BISHOP
        'n' -> PieceType.KNIGHT
        else -> null
    }
}