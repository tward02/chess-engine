package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame

/**
 * A tapered version of [StandardEvaluator].
 *
 * It reuses all of the base scoring — material, the per-piece loop, check, and the
 * whiteScore − blackScore framing — and only overrides the three things that should change as the
 * game progresses:
 *  - [locationValue]: blends the middlegame and endgame piece-square tables by game phase, so the
 *    king is drawn to the centre and pawns are pushed harder in the endgame.
 *  - [castled]: the castling bonus fades out toward the endgame, where a tucked-away king is a
 *    liability rather than an asset.
 *  - [mobility]: a plain mobility count (the base class's mobility carries an extra "aggression"
 *    term that this evaluator deliberately leaves out).
 *
 * The game phase is the same for every piece in a position, so it is computed once per [evaluate]
 * and stashed in [phase] for the override hooks to read during that call. This makes the evaluator
 * stateful and therefore not thread-safe — give each searching bot its own instance, exactly as the
 * per-game bot factories already do.
 */
class AdaptiveEvaluator(aggression: Int = 10) : StandardEvaluator(aggression = aggression) {

    private var phase: Int = PieceSquareTables.MAX_PHASE

    override fun evaluate(game: ChessGame, depth: Int): Int {
        // Compute the phase once, before the inherited scoring reads it via the hooks below
        phase = PieceSquareTables.gamePhase(game.board)
        return super.evaluate(game, depth)
    }

    override fun locationValue(piece: Piece, square: Square): Int {
        return PieceSquareTables.locationValue(piece.type, piece.colour, square, phase)
    }

    override fun castled(game: ChessGame, colour: Colour): Int {
        // Reuse the base "have we castled?" logic, then scale it down toward the endgame
        return super.castled(game, colour) * phase / PieceSquareTables.MAX_PHASE
    }
}
