package com.tward.engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square

/**
 * Piece-square tables for "tapered" evaluation.
 *
 * Every piece has two tables: a middlegame set and an endgame set. The board's "phase" (how far
 * through the game we are, measured by remaining non-pawn material) is used to blend smoothly
 * between them — full middlegame tables at the start, full endgame tables once the board is bare.
 *
 * Tables are laid out to match the board grid: row 0 is rank 8, row 7 is rank 1, from White's
 * perspective. [locationValue] mirrors vertically for Black.
 *
 * The two tables that actually differ here are the king and the pawn:
 *  - King: middlegame keeps it tucked away behind its pawns; endgame pulls it to the centre so it
 *    can support passed pawns and help deliver mate. Because evaluation is whiteScore − blackScore,
 *    this same endgame table also rewards driving the *enemy* king to the edge.
 *  - Pawn: the endgame table rewards advancing far more steeply, so the engine pushes passed pawns.
 *
 * The other pieces reuse their middlegame table in the endgame (their best squares change little),
 * but each still has an explicit endgame slot so the tables can be tuned independently later.
 */
object PieceSquareTables {

    // Phase weights: the classic 24-point scale. Both sides together at the start hold 8 minor
    // pieces (×1) + 4 rooks (×2) + 2 queens (×4) = 24.
    const val MAX_PHASE = 24

    private fun phaseWeight(type: PieceType): Int = when (type) {
        PieceType.KNIGHT, PieceType.BISHOP -> 1
        PieceType.ROOK -> 2
        PieceType.QUEEN -> 4
        PieceType.PAWN, PieceType.KING -> 0
    }

    /** 24 at the opening, sliding toward 0 as pieces are traded off. Capped in case of promotions. */
    fun gamePhase(board: Board): Int {
        var phase = 0
        for (piece in board.getPieces()) {
            phase += phaseWeight(piece.type)
        }
        return phase.coerceAtMost(MAX_PHASE)
    }

    /**
     * The positional value of [type] of [colour] sitting on [square], blended between the middlegame
     * and endgame tables according to [phase] (0..[MAX_PHASE]).
     */
    fun locationValue(type: PieceType, colour: Colour, square: Square, phase: Int): Int {
        val row = if (colour == Colour.WHITE) square.row else 7 - square.row

        val middlegame = middlegameTable(type)[row][square.col]
        val endgame = endgameTable(type)[row][square.col]

        // Linear interpolation: all middlegame at phase = MAX_PHASE, all endgame at phase = 0
        return (middlegame * phase + endgame * (MAX_PHASE - phase)) / MAX_PHASE
    }

    fun middlegameTable(type: PieceType): Array<IntArray> = when (type) {
        PieceType.PAWN -> MG_PAWN
        PieceType.KNIGHT -> MG_KNIGHT
        PieceType.BISHOP -> MG_BISHOP
        PieceType.ROOK -> MG_ROOK
        PieceType.QUEEN -> MG_QUEEN
        PieceType.KING -> MG_KING
    }

    fun endgameTable(type: PieceType): Array<IntArray> = when (type) {
        PieceType.PAWN -> EG_PAWN
        PieceType.KNIGHT -> MG_KNIGHT
        PieceType.BISHOP -> MG_BISHOP
        PieceType.ROOK -> MG_ROOK
        PieceType.QUEEN -> MG_QUEEN
        PieceType.KING -> EG_KING
    }

    // ---- Middlegame tables (same values the StandardEvaluator uses) ----

    private val MG_PAWN = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(5, 5, 10, 25, 25, 10, 5, 5),
        intArrayOf(0, 0, 0, 20, 20, 0, 0, 0),
        intArrayOf(5, -5, -10, 0, 0, -10, -5, 5),
        intArrayOf(5, 10, 10, -20, -20, 10, 10, 5),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

    private val MG_KNIGHT = arrayOf(
        intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50),
        intArrayOf(-40, -20, 0, 0, 0, 0, -20, -40),
        intArrayOf(-30, 0, 10, 15, 15, 10, 0, -30),
        intArrayOf(-30, 5, 15, 20, 20, 15, 5, -30),
        intArrayOf(-30, 0, 15, 20, 20, 15, 0, -30),
        intArrayOf(-30, 5, 10, 15, 15, 10, 5, -30),
        intArrayOf(-40, -20, 0, 5, 5, 0, -20, -40),
        intArrayOf(-50, -40, -30, -30, -30, -30, -40, -50)
    )

    private val MG_BISHOP = arrayOf(
        intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20),
        intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
        intArrayOf(-10, 0, 5, 10, 10, 5, 0, -10),
        intArrayOf(-10, 5, 5, 10, 10, 5, 5, -10),
        intArrayOf(-10, 0, 10, 10, 10, 10, 0, -10),
        intArrayOf(-10, 10, 10, 10, 10, 10, 10, -10),
        intArrayOf(-10, 5, 0, 0, 0, 0, 5, -10),
        intArrayOf(-20, -10, -10, -10, -10, -10, -10, -20)
    )

    private val MG_ROOK = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(5, 10, 10, 10, 10, 10, 10, 5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(-5, 0, 0, 0, 0, 0, 0, -5),
        intArrayOf(0, 0, 0, 5, 5, 0, 0, 0)
    )

    private val MG_QUEEN = arrayOf(
        intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20),
        intArrayOf(-10, 0, 0, 0, 0, 0, 0, -10),
        intArrayOf(-10, 0, 5, 5, 5, 5, 0, -10),
        intArrayOf(-5, 0, 5, 5, 5, 5, 0, -5),
        intArrayOf(0, 0, 5, 5, 5, 5, 0, -5),
        intArrayOf(-10, 5, 5, 5, 5, 5, 0, -10),
        intArrayOf(-10, 0, 5, 0, 0, 0, 0, -10),
        intArrayOf(-20, -10, -10, -5, -5, -10, -10, -20)
    )

    private val MG_KING = arrayOf(
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-30, -40, -40, -50, -50, -40, -40, -30),
        intArrayOf(-20, -30, -30, -40, -40, -30, -30, -20),
        intArrayOf(-10, -20, -20, -20, -20, -20, -20, -10),
        intArrayOf(20, 20, 0, 0, 0, 0, 20, 20),
        intArrayOf(20, 30, 10, 0, 0, 10, 30, 20)
    )

    // ---- Endgame tables ----

    // Pawns: advancing matters much more once the pieces are gone, so the bonus grows steeply with
    // rank (row 1 = rank 7, one step from promoting).
    private val EG_PAWN = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(90, 90, 90, 90, 90, 90, 90, 90),
        intArrayOf(60, 60, 60, 60, 60, 60, 60, 60),
        intArrayOf(40, 40, 40, 40, 40, 40, 40, 40),
        intArrayOf(25, 25, 25, 25, 25, 25, 25, 25),
        intArrayOf(15, 15, 15, 15, 15, 15, 15, 15),
        intArrayOf(10, 10, 10, 10, 10, 10, 10, 10),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

    // King: the opposite of the middlegame — the centre is now the best place to be, the back rank
    // and corners the worst. Pulls our king up the board, and (via whiteScore − blackScore) rewards
    // pushing the enemy king to the edge.
    private val EG_KING = arrayOf(
        intArrayOf(-50, -40, -30, -20, -20, -30, -40, -50),
        intArrayOf(-30, -20, -10, 0, 0, -10, -20, -30),
        intArrayOf(-30, -10, 20, 30, 30, 20, -10, -30),
        intArrayOf(-30, -10, 30, 40, 40, 30, -10, -30),
        intArrayOf(-30, -10, 30, 40, 40, 30, -10, -30),
        intArrayOf(-30, -10, 20, 30, 30, 20, -10, -30),
        intArrayOf(-30, -30, 0, 0, 0, 0, -30, -30),
        intArrayOf(-50, -30, -30, -30, -30, -30, -30, -50)
    )
}
