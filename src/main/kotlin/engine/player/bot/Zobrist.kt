package com.tward.engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import kotlin.random.Random

/**
 * Zobrist hashing: a 64-bit fingerprint of a position used as the transposition-table key in
 * [AdvancedNegamaxBot]. Each (piece, square), the side to move, the four castling rights and the
 * en-passant file get a fixed random 64-bit value; the position's hash is their XOR.
 *
 * The random table is seeded with a constant so hashes are stable across runs (reproducible search,
 * testable). [hash] recomputes from the board in one pass over the pieces — cheap next to move
 * generation, and it sidesteps having to thread incremental updates through [Board].
 */
internal object Zobrist {

    private val rng = Random(0x9E3779B97F4A7C15uL.toLong())

    // [colour * 6 + pieceType ordinal][square 0..63]
    private val pieceSquare = Array(12) { LongArray(64) { rng.nextLong() } }
    private val blackToMove = rng.nextLong()
    private val castling = LongArray(4) { rng.nextLong() }   // wK, wQ, bK, bQ
    private val enPassantFile = LongArray(8) { rng.nextLong() }

    fun hash(board: Board): Long {
        var h = 0L

        for ((square, piece) in board.getPiecesWithSquares()) {
            val pieceIndex = piece.colour.ordinal * 6 + piece.type.ordinal
            h = h xor pieceSquare[pieceIndex][square.row * 8 + square.col]
        }

        if (board.activeColour == Colour.BLACK) h = h xor blackToMove
        if (board.whiteCanCastleKingside) h = h xor castling[0]
        if (board.whiteCanCastleQueenside) h = h xor castling[1]
        if (board.blackCanCastleKingside) h = h xor castling[2]
        if (board.blackCanCastleQueenside) h = h xor castling[3]
        board.enPassantTarget?.let { h = h xor enPassantFile[it.col] }

        return h
    }
}
