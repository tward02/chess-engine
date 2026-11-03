package com.tward.engine.board

/**
 * Observes every change to a [Board]'s pieces, so derived state (an NNUE accumulator) can be kept
 * current incrementally instead of being rebuilt from the whole board. A board has at most one
 * listener ([Board.listener]); setting a new one silently replaces the old.
 *
 * Called on the search hot path (every make/undo), so implementations must be cheap and must not
 * touch the board.
 */
interface BoardListener {
    /** The piece on ([col], [row]) changed from [old] to [new] (either may be null). */
    fun pieceChanged(col: Int, row: Int, old: Piece?, new: Piece?)

    /** Every piece was removed at once ([Board.clearBoard]). */
    fun boardCleared()
}
