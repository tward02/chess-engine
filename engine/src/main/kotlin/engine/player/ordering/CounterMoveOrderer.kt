package com.tward.engine.player.ordering

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Square

/**
 * [KillerHistoryMoveOrderer]'s scheme plus the **countermove heuristic**: the quiet move that last
 * refuted a specific opponent move is tried early whenever that opponent move is played again, even
 * at plies where it never earned killer status. Refutations tend to work wherever the move they
 * answer appears, so this catches what per-ply killers miss.
 *
 * The orderer cannot see the search stack, so the searching bot must keep [previousMove] pointing at
 * the move that led to the node currently being ordered (set it before recursing into a child,
 * restore it after). With [previousMove] null the countermove band is simply inactive and ordering
 * degrades gracefully to killers + history.
 *
 * Score bands: captures (1,000,000+) > killer1 (900k) > killer2 (800k) > countermove (750k) >
 * history (≤700k) > untouched quiets (0).
 *
 * Stateful and NOT thread-safe — each bot needs its own instance.
 */
class CounterMoveOrderer(
    private val mvvLva: MvvLvaMoveOrderer = MvvLvaMoveOrderer()
) : MoveOrderer {

    /** The move that led to the node being ordered; maintained by the searching bot. */
    var previousMove: Move? = null

    private val killers = Array(MAX_PLY) { arrayOfNulls<Move>(KILLER_SLOTS) }

    // history[colourIndex][fromSquare][toSquare]
    private val history = Array(2) { Array(BOARD_SQUARES) { IntArray(BOARD_SQUARES) } }

    // counters[colourIndex of the move being answered][fromSquare][toSquare] -> its refutation
    private val counters = Array(2) { Array(BOARD_SQUARES) { arrayOfNulls<Move>(BOARD_SQUARES) } }

    override fun order(moves: List<Move>, ply: Int): List<Move> {
        if (moves.size <= 1) return moves
        val counter = counterTo(previousMove)
        return moves.sortedByDescending { scoreOf(it, ply, counter) }
    }

    private fun scoreOf(move: Move, ply: Int, counter: Move?): Int {
        val tactical = mvvLva.scoreOf(move)
        if (tactical > 0) return CAPTURE_BASE + tactical

        if (ply in 0 until MAX_PLY) {
            val slots = killers[ply]
            if (move == slots[0]) return KILLER_1
            if (move == slots[1]) return KILLER_2
        }

        if (move == counter) return COUNTER_MOVE

        // Capped so history never reaches the countermove band
        return historyValue(move).coerceAtMost(MAX_HISTORY)
    }

    override fun onBetaCutoff(move: Move, ply: Int, depth: Int) {
        // Only quiet moves; captures and promotions are already handled by MVV-LVA
        if (move.capturedPiece != null || move.promotionType != null) return

        if (ply in 0 until MAX_PLY) {
            val slots = killers[ply]
            // Keep two distinct killers, most recent first
            if (move != slots[0]) {
                slots[1] = slots[0]
                slots[0] = move
            }
        }

        val colour = colourIndex(move) ?: return
        // depth² so deeper cutoffs (more work saved) count for more
        history[colour][index(move.from)][index(move.to)] += depth * depth

        previousMove?.let { prev ->
            colourIndex(prev)?.let { counters[it][index(prev.from)][index(prev.to)] = move }
        }
    }

    override fun reset() {
        previousMove = null
        for (slots in killers) {
            slots[0] = null
            slots[1] = null
        }
        for (colour in history) {
            for (row in colour) {
                row.fill(0)
            }
        }
        for (colour in counters) {
            for (row in colour) {
                row.fill(null)
            }
        }
    }

    private fun counterTo(previous: Move?): Move? {
        val prev = previous ?: return null
        val colour = colourIndex(prev) ?: return null
        return counters[colour][index(prev.from)][index(prev.to)]
    }

    private fun historyValue(move: Move): Int {
        val colour = colourIndex(move) ?: return 0
        return history[colour][index(move.from)][index(move.to)]
    }

    private fun colourIndex(move: Move): Int? = when (move.piece?.colour) {
        Colour.WHITE -> 0
        Colour.BLACK -> 1
        null -> null
    }

    private fun index(square: Square): Int = square.row * 8 + square.col

    companion object {
        private const val MAX_PLY = 64
        private const val KILLER_SLOTS = 2
        private const val BOARD_SQUARES = 64

        private const val CAPTURE_BASE = 1_000_000
        private const val KILLER_1 = 900_000
        private const val KILLER_2 = 800_000
        private const val COUNTER_MOVE = 750_000
        private const val MAX_HISTORY = 700_000
    }
}
