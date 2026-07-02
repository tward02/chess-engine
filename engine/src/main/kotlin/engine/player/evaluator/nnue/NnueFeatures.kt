package com.tward.engine.player.evaluator.nnue

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square

/**
 * Maps a (piece, square) pair to the network's one-hot input index, seen from one side's perspective.
 * Features encode friend-or-foe rather than white-or-black, and Black views a vertically mirrored
 * board, so a single set of weights serves both colours: a white knight on f3 looks to White exactly
 * like a black knight on f6 looks to Black.
 */
object NnueFeatures {

    /** 12 piece kinds (6 friendly + 6 enemy) x 64 squares. */
    const val COUNT = 12 * 64

    fun index(perspective: Colour, piece: Piece, square: Square): Int {
        val foe = if (piece.colour == perspective) 0 else 1
        val row = if (perspective == Colour.WHITE) square.row else 7 - square.row
        return (foe * 6 + typeIndex(piece.type)) * 64 + row * 8 + square.col
    }

    /** Converts a White-perspective index to the Black-perspective index of the same piece. */
    fun mirror(index: Int): Int {
        val square = index and 63
        val kind = index ushr 6                    // 0..11: friendly kinds then enemy kinds
        val flippedKind = (kind + 6) % 12          // friend <-> foe
        return flippedKind * 64 + (square xor 56)  // xor 56 flips the row, keeps the column
    }

    private fun typeIndex(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 0
        PieceType.KNIGHT -> 1
        PieceType.BISHOP -> 2
        PieceType.ROOK -> 3
        PieceType.QUEEN -> 4
        PieceType.KING -> 5
    }
}