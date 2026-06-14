package com.tward.engine.player.ordering

import com.tward.engine.board.Move
import com.tward.engine.board.value

/**
 * Static move ordering using MVV-LVA (Most Valuable Victim − Least Valuable Aggressor) with a
 * promotion bonus. "Static" because each move is scored purely from its own fields — the captured
 * piece, the moving piece and the promotion type — with no board lookup and no search. That makes
 * it cheap enough to apply at every node, which is exactly where it needs to run.
 *
 * Resulting order (best-first):
 *   1. Capture-promotions  — win material AND make a new queen
 *   2. Promotions          — queen highest, then rook / bishop / knight
 *   3. Captures            — MVV-LVA: take the biggest piece with the smallest attacker
 *   4. Quiet moves         — score 0, left in their original (stable) order
 */
class MvvLvaMoveOrderer : MoveOrderer {

    // MVV-LVA is static, so the ply is irrelevant here
    override fun order(moves: List<Move>, ply: Int): List<Move> {
        // Nothing to reorder, and avoids allocating a copy on the search's hottest leaf nodes
        if (moves.size <= 1) return moves

        // sortedByDescending is a stable sort, so equally scored moves (e.g. all quiet moves)
        // keep their original relative order, keeping the bot's behaviour deterministic.
        return moves.sortedByDescending { scoreOf(it) }
    }

    /**
     * Higher means "search earlier". Exposed so future, smarter orderers can reuse MVV-LVA as one
     * component (e.g. after trying a hash/principal-variation move first). Quiet moves score 0.
     */
    fun scoreOf(move: Move): Int {

        var score = 0

        move.capturedPiece?.let { victim ->
            // Weight the victim heavily so a bigger victim always outranks a smaller one; subtract
            // the attacker so that, for the same victim, the cheapest attacker is preferred (a pawn
            // taking a queen is safer and better than a queen taking that queen).
            val aggressorValue = move.piece?.value() ?: 0
            score += CAPTURE_BONUS + victim.value() * VICTIM_WEIGHT - aggressorValue
        }

        move.promotionType?.let { promotion ->
            // Promotions are rare but decisive, so they sit above ordinary captures; the promoted
            // piece's value ranks a new queen above an under-promotion.
            score += PROMOTION_BONUS + promotion.value()
        }

        return score
    }

    companion object {
        // Bands chosen so the categories never overlap: quiet (0) < captures (~1000) < promotions
        // (~2000) < capture-promotions (~3000).
        private const val CAPTURE_BONUS = 1_000
        private const val PROMOTION_BONUS = 2_000

        // Larger than the maximum piece value (9) so the victim term dominates the aggressor term.
        private const val VICTIM_WEIGHT = 10
    }
}
