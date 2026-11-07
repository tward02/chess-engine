package com.tward.engine.board

/**
 * Bitboard conventions shared by [Board] and the code that reads its bitboards. Bit `row * 8 + col`
 * is the square at ([col], [row]), with the board's own orientation: row 0 is rank 8, so bit 0 is
 * a8 and bit 63 is h1.
 */
object Bitboards {

    fun index(col: Int, row: Int): Int = row * 8 + col

    fun index(square: Square): Int = square.row * 8 + square.col

    fun col(index: Int): Int = index and 7

    fun row(index: Int): Int = index ushr 3

    /** Visits each set bit's index, lowest first. */
    inline fun forEachBit(bits: Long, action: (index: Int) -> Unit) {
        var remaining = bits
        while (remaining != 0L) {
            action(java.lang.Long.numberOfTrailingZeros(remaining))
            remaining = remaining and (remaining - 1)
        }
    }
}
