package com.tward.engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square

/**
 * Piece-square tables for tapered evaluation: blends middlegame and endgame tables by game phase.
 *
 * Phase is measured by remaining non-pawn material (24 = starting position, 0 = bare board).
 * Tables are row 0 = rank 8, from White's perspective; [locationValue] mirrors vertically for Black.
 *
 * King and pawn have distinct endgame tables; all other pieces reuse their middlegame table.
 */
object PieceSquareTables {

    // Classic 24-point scale: 2 queens (×4) + 4 rooks (×2) + 8 minors (×1) = 24
    const val MAX_PHASE = 24

    private fun phaseWeight(type: PieceType): Int = when (type) {
        PieceType.KNIGHT, PieceType.BISHOP -> 1
        PieceType.ROOK -> 2
        PieceType.QUEEN -> 4
        PieceType.PAWN, PieceType.KING -> 0
    }

    /** 24 at the opening, sliding toward 0 as pieces are traded off. Capped for promotions. */
    fun gamePhase(board: Board): Int {
        var phase = 0
        for (piece in board.getPieces()) {
            phase += phaseWeight(piece.type)
        }
        return phase.coerceAtMost(MAX_PHASE)
    }

    /**
     * Positional value of [type] of [colour] on [square], blended between middlegame and endgame
     * tables according to [phase] (0..[MAX_PHASE]).
     */
    fun locationValue(type: PieceType, colour: Colour, square: Square, phase: Int): Int {
        val row = if (colour == Colour.WHITE) square.row else 7 - square.row
        val middlegame = middlegameTable(type)[row][square.col]
        val endgame = endgameTable(type)[row][square.col]
        // Linear blend: full middlegame at MAX_PHASE, full endgame at 0
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

    // ---- Middlegame tables ----

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

    // Advancing is much more important with pieces off the board
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

    // Centre is best; corners and back rank are worst. Via whiteScore−blackScore this also
    // rewards pushing the enemy king to the edge.
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
