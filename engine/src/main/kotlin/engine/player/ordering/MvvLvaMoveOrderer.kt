package com.tward.engine.player.ordering

import com.tward.engine.board.Move
import com.tward.engine.board.value

/**
 * Static move ordering: captures by MVV-LVA (Most Valuable Victim − Least Valuable Aggressor),
 * promotions above captures, quiet moves last. "Static" means each move is scored from its own
 * fields with no board lookup and no per-search state.
 */
class MvvLvaMoveOrderer : MoveOrderer {

    override fun order(moves: List<Move>, ply: Int): List<Move> {
        if (moves.size <= 1) return moves
        return moves.sortedByDescending { scoreOf(it) }
    }

    /**
     * Higher means "search earlier". Exposed so composite orderers can reuse this for captures.
     * Quiet moves score 0.
     */
    fun scoreOf(move: Move): Int {
        var score = 0

        move.capturedPiece?.let { victim ->
            // Bigger victim wins; for the same victim, prefer the cheapest attacker
            val aggressorValue = move.piece?.value() ?: 0
            score += CAPTURE_BONUS + victim.value() * VICTIM_WEIGHT - aggressorValue
        }

        move.promotionType?.let { promotion ->
            score += PROMOTION_BONUS + promotion.value()
        }

        return score
    }

    companion object {
        // Bands: quiet (0) < captures (~1000) < promotions (~2000) < capture-promotions (~3000)
        private const val CAPTURE_BONUS = 1_000
        private const val PROMOTION_BONUS = 2_000
        private const val VICTIM_WEIGHT = 10  // > max piece value (9), so victim term dominates
    }
}
