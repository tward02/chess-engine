package com.tward.engine.player.ordering

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Square

/**
 * Layers the two classic dynamic ordering heuristics on top of MVV-LVA:
 *
 *  - **Killer moves**: at each ply we remember the last two *quiet* moves that produced a beta
 *    cutoff. The same quiet refutation often works against the sibling positions at that ply, so we
 *    try the killers immediately after captures.
 *  - **History heuristic**: a running per-(colour, from, to) counter, bumped by depth² whenever a
 *    quiet move causes a cutoff (deeper cutoffs are worth more). Quiet moves that have been good
 *    across the whole search so far are tried first among the remaining quiets.
 *
 * Captures and promotions keep their MVV-LVA order and always come first; killers and history only
 * decide the order *among quiet moves*, which is exactly where MVV-LVA gives no signal (every quiet
 * move scores 0 there).
 *
 * This orderer is stateful and NOT thread-safe — give each searching bot its own instance. The
 * per-game bot factories already do this, and the default constructor makes a fresh one per bot.
 */
class KillerHistoryMoveOrderer(
    private val mvvLva: MvvLvaMoveOrderer = MvvLvaMoveOrderer()
) : MoveOrderer {

    // Two killer slots per ply
    private val killers = Array(MAX_PLY) { arrayOfNulls<Move>(KILLER_SLOTS) }

    // history[colourIndex][fromSquare][toSquare]
    private val history = Array(2) { Array(BOARD_SQUARES) { IntArray(BOARD_SQUARES) } }

    override fun order(moves: List<Move>, ply: Int): List<Move> {
        if (moves.size <= 1) return moves
        return moves.sortedByDescending { scoreOf(it, ply) }
    }

    private fun scoreOf(move: Move, ply: Int): Int {

        // Captures and promotions: keep their MVV-LVA order, lifted above every quiet-move score
        val tactical = mvvLva.scoreOf(move)
        if (tactical > 0) return CAPTURE_BASE + tactical

        // Quiet moves: killers first (in slot order), then history
        if (ply in 0 until MAX_PLY) {
            val slots = killers[ply]
            if (move == slots[0]) return KILLER_1
            if (move == slots[1]) return KILLER_2
        }

        // Capped so history can never reach the killer band — killers always win ties
        return historyValue(move).coerceAtMost(MAX_HISTORY)
    }

    override fun onBetaCutoff(move: Move, ply: Int, depth: Int) {

        // Only quiet moves are recorded; captures/promotions are already ordered by MVV-LVA
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
        // depth² so a cutoff found deeper in the tree (more work saved) counts for more
        history[colour][index(move.from)][index(move.to)] += depth * depth
    }

    override fun reset() {
        for (slots in killers) {
            slots[0] = null
            slots[1] = null
        }
        for (colour in history) {
            for (row in colour) {
                row.fill(0)
            }
        }
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

        // Score bands kept far apart so the categories never overlap:
        //   quiet history [0 .. MAX_HISTORY]  <  KILLER_2  <  KILLER_1  <  captures (CAPTURE_BASE+)
        private const val CAPTURE_BASE = 1_000_000
        private const val KILLER_1 = 900_000
        private const val KILLER_2 = 800_000
        private const val MAX_HISTORY = 700_000
    }
}
