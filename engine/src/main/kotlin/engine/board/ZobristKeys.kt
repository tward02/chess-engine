package com.tward.engine.board

import kotlin.random.Random

/**
 * Random key tables for the Zobrist position hash [Board] maintains incrementally: each
 * (piece, square), the side to move, the four castling rights and the en-passant file get a fixed
 * random 64-bit value, and a position's hash is their XOR. Seeded with a constant so hashes are
 * stable across runs (reproducible search, testable).
 */
internal object ZobristKeys {

    private val rng = Random(0x9E3779B97F4A7C15uL.toLong())

    // [colour * 6 + pieceType ordinal][square 0..63]
    private val pieceSquare = Array(12) { LongArray(64) { rng.nextLong() } }
    val blackToMove = rng.nextLong()
    val castling = LongArray(4) { rng.nextLong() }   // wK, wQ, bK, bQ
    val enPassantFile = LongArray(8) { rng.nextLong() }

    fun pieceKey(piece: Piece, col: Int, row: Int): Long =
        pieceSquare[piece.colour.ordinal * 6 + piece.type.ordinal][row * 8 + col]
}
