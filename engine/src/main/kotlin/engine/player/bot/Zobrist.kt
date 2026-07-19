package com.tward.engine.player.bot

import com.tward.engine.board.Board

/**
 * Zobrist hashing: a 64-bit fingerprint of a position used as the transposition-table key in
 * [AdvancedNegamaxBot] and its descendants. The hash itself is maintained incrementally by
 * [Board] (see [Board.zobristKey] and `ZobristKeys`), so probing here is O(1) — this object
 * survives as the search-side entry point.
 */
internal object Zobrist {

    fun hash(board: Board): Long = board.zobristKey
}
