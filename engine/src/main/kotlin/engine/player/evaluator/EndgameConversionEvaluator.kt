package com.tward.engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import kotlin.math.abs
import kotlin.math.max

/**
 * [CompactEvaluator] plus the terms that turn winning positions into wins instead of draws:
 *
 * - **Mop-up**: once one side is clearly ahead in a late endgame, plain material + PST goes flat —
 *   every safe square scores the same and the winning side shuffles until the 50-move rule saves the
 *   loser. This term rewards driving the losing king toward the edge/corner and marching the winning
 *   king up to help, which is exactly the technique K+R vs K and K+Q vs K conversions need.
 * - **50-move fade**: as the halfmove clock climbs, the whole score is scaled toward zero, so a line
 *   that makes real progress (a pawn push or a capture resets the clock) outscores an equivalent one
 *   that drifts toward the draw. The search then prefers progress automatically.
 *
 * Same conventions as the rest of the family: centipawns from White's perspective, effectively
 * stateless, cheap enough for the search hot path (both terms are O(pieces) and mop-up only runs in
 * won endgames).
 */
open class EndgameConversionEvaluator : CompactEvaluator() {

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val board = game.board
        var score = super.evaluate(game, depth)

        val phase = PieceSquareTables.gamePhase(board)
        if (phase <= MOP_UP_MAX_PHASE) {
            val materialLead = materialBalance(board)
            if (materialLead >= MOP_UP_MIN_LEAD) {
                score += mopUp(board, winning = Colour.WHITE)
            } else if (materialLead <= -MOP_UP_MIN_LEAD) {
                score -= mopUp(board, winning = Colour.BLACK)
            }
        }

        // Fade the score once the halfmove clock gets threatening (100 half-moves is the draw).
        val clock = board.halfMoveClock
        if (clock > CLOCK_FADE_START) {
            score = score * (CLOCK_FADE_SPAN - (clock - CLOCK_FADE_START)) / CLOCK_FADE_SPAN
        }

        return score
    }

    /** Positive bonus for [winning]: losing king near the edge, kings close together. */
    private fun mopUp(board: Board, winning: Colour): Int {
        val winnerKing = board.findKing(winning)
        val loserKing = board.findKing(winning.opposite())

        val centreDistance = max(abs(loserKing.col * 2 - 7), abs(loserKing.row * 2 - 7)) / 2   // 0..3
        val kingGap = abs(winnerKing.col - loserKing.col) + abs(winnerKing.row - loserKing.row) // 2..14

        return centreDistance * EDGE_WEIGHT + (14 - kingGap) * PROXIMITY_WEIGHT
    }

    private fun materialBalance(board: Board): Int {
        var balance = 0
        for (piece in board.getPieces()) {
            val value = when (piece.type) {
                PieceType.PAWN -> 100
                PieceType.KNIGHT -> 320
                PieceType.BISHOP -> 330
                PieceType.ROOK -> 500
                PieceType.QUEEN -> 900
                PieceType.KING -> 0
            }
            balance += if (piece.colour == Colour.WHITE) value else -value
        }
        return balance
    }

    private companion object {
        const val MOP_UP_MAX_PHASE = 8      // late endgame only (phase is 0..24 non-pawn material)
        const val MOP_UP_MIN_LEAD = 400     // clearly winning: at least ~a rook (or two minors) up

        const val EDGE_WEIGHT = 25          // per step the losing king stands from the centre
        const val PROXIMITY_WEIGHT = 8      // per step the kings are closer together

        // No fade until move 30 of the clock, then linear toward 0 at the 50-move draw itself.
        const val CLOCK_FADE_START = 60
        const val CLOCK_FADE_SPAN = 100 - CLOCK_FADE_START
    }
}