package com.tward.engine.player.evaluator

import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame

/**
 * A fast, fully static evaluator built for deep search. It scores material, tapered piece-square
 * tables, pawn structure (doubled / isolated / passed), the bishop pair, rook activity on open and
 * half-open files, a king pawn shield, and a small side-to-move tempo bonus.
 *
 * It reuses [PositionalEvaluator]'s pawn-structure / king-shield scoring but deliberately leaves out
 * the expensive parts of the [StandardEvaluator] base it inherits: it never calls `mobility()`,
 * `check()` or `castled()` (each of which generates moves or copies the board), so it is several
 * times cheaper per call — the difference between a shallow and a deep search when the leaf is hit
 * millions of times. Tactics are left to the search (quiescence); this evaluator only judges quiet
 * positions.
 *
 * Scores follow the shared convention: centipawns from White's perspective (white − black). Its own
 * [evaluate] reads no mutable state (it computes phase locally and never touches the inherited
 * [AdaptiveEvaluator] phase field), so it is effectively stateless and safe to share between bots.
 */
open class CompactEvaluator : PositionalEvaluator() {

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val board = game.board
        val phase = PieceSquareTables.gamePhase(board)

        var score = 0
        for ((square, piece) in board.getPiecesWithSquares()) {
            val value = material(piece.type) +
                    PieceSquareTables.locationValue(piece.type, piece.colour, square, phase)
            score += if (piece.colour == Colour.WHITE) value else -value
        }

        score += positionalScore(board, Colour.WHITE, phase) - positionalScore(board, Colour.BLACK, phase)

        // Tempo: a small edge for having the move, kept tiny so it never distorts material judgement.
        score += if (board.activeColour == Colour.WHITE) TEMPO else -TEMPO

        return score
    }

    /** CompactEvaluator omits the space term that [PositionalEvaluator] adds. */
    override fun spaceScore(square: Square, colour: Colour, phase: Int): Int = 0

    private fun material(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 0
    }

    private companion object {
        const val TEMPO = 10
    }
}
