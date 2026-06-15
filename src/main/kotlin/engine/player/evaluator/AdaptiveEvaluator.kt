package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame

/**
 * Tapered version of [StandardEvaluator]: blends middlegame and endgame piece-square tables by
 * game phase, scales the castling bonus toward zero in the endgame, and drops the aggression
 * term from mobility.
 *
 * Not thread-safe — [phase] is computed once per [evaluate] and stored for the hook overrides
 * to read. Give each searching bot its own instance.
 */
class AdaptiveEvaluator(aggression: Int = 10) : StandardEvaluator(aggression = aggression) {

    private var phase: Int = PieceSquareTables.MAX_PHASE

    override fun evaluate(game: ChessGame, depth: Int): Int {
        phase = PieceSquareTables.gamePhase(game.board)
        return super.evaluate(game, depth)
    }

    override fun locationValue(piece: Piece, square: Square): Int {
        return PieceSquareTables.locationValue(piece.type, piece.colour, square, phase)
    }

    override fun castled(game: ChessGame, colour: Colour): Int {
        return super.castled(game, colour) * phase / PieceSquareTables.MAX_PHASE
    }
}
