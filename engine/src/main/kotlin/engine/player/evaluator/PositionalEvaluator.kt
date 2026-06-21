package com.tward.engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import kotlin.math.abs

/**
 * Richer tapered evaluator built on top of [AdaptiveEvaluator]. Keeps the tapered material +
 * piece-square tables, mobility, check and castling scoring, and adds: pawn structure (doubled,
 * isolated, passed), bishop pair, rook activity (open/half-open files), king pawn shield, and a
 * small middlegame space bonus.
 *
 * Scores stay in the shared convention: centipawns from White's perspective.
 *
 * Not thread-safe — inherits [AdaptiveEvaluator]'s per-call phase field, so give each searching
 * bot its own instance. Drop-in [Evaluator]; usable by bots directly, or wrapped in
 * [QuiescenceEvaluator] for a stable bar reading.
 */
open class PositionalEvaluator(aggression: Int = 10) : AdaptiveEvaluator(aggression = aggression) {

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val taperedBase = super.evaluate(game, depth)

        val board = game.board
        val phase = PieceSquareTables.gamePhase(board)
        val white = positionalScore(board, Colour.WHITE, phase)
        val black = positionalScore(board, Colour.BLACK, phase)

        return taperedBase + (white - black)
    }

    protected fun positionalScore(board: Board, colour: Colour, phase: Int): Int {
        val piecesWithSquares = board.getPiecesWithSquares()
        val own = piecesWithSquares.filter { (_, piece) -> piece.colour == colour }
        val ownPawns = own.filter { (_, piece) -> piece.type == PieceType.PAWN }.map { it.first }
        val enemyPawns = piecesWithSquares
            .filter { (_, piece) -> piece.colour == colour.opposite() && piece.type == PieceType.PAWN }
            .map { it.first }

        val ownPawnsPerFile = IntArray(8)
        for (square in ownPawns) ownPawnsPerFile[square.col]++
        val enemyPawnsPerFile = IntArray(8)
        for (square in enemyPawns) enemyPawnsPerFile[square.col]++

        var score = 0

        for (file in 0..7) {
            if (ownPawnsPerFile[file] > 1) {
                score -= DOUBLED_PENALTY * (ownPawnsPerFile[file] - 1)
            }
        }

        for (square in ownPawns) {
            val hasNeighbour = (square.col > 0 && ownPawnsPerFile[square.col - 1] > 0) ||
                    (square.col < 7 && ownPawnsPerFile[square.col + 1] > 0)
            if (!hasNeighbour) score -= ISOLATED_PENALTY

            if (isPassed(square, colour, enemyPawns)) {
                val advance = advancement(square, colour)
                score += taper(PASSED_MG[advance], PASSED_EG[advance], phase)
            }

            score += spaceScore(square, colour, phase)
        }

        if (own.count { (_, piece) -> piece.type == PieceType.BISHOP } >= 2) {
            score += BISHOP_PAIR_BONUS
        }

        for ((square, piece) in own) {
            if (piece.type == PieceType.ROOK && ownPawnsPerFile[square.col] == 0) {
                score += if (enemyPawnsPerFile[square.col] == 0) ROOK_OPEN_FILE else ROOK_HALF_OPEN
            }
        }

        // Shield fades toward the endgame (taper against 0), where king activity matters more
        score += taper(kingShield(board, colour) * KING_SHIELD_PER_PAWN, 0, phase)

        return score
    }

    /**
     * Middlegame bonus for a pawn sitting in the enemy half; fades to nothing in the endgame.
     * Factored into its own hook so subclasses (e.g. [CompactEvaluator]) can drop the space term
     * while still reusing the rest of [positionalScore].
     */
    protected open fun spaceScore(square: Square, colour: Colour, phase: Int): Int {
        return if (inEnemyHalf(square, colour)) taper(SPACE_BONUS, 0, phase) else 0
    }

    /** Friendly pawns on the three files in front of the king. A cheap pawn-cover proxy. */
    private fun kingShield(board: Board, colour: Colour): Int {
        val king = board.findKing(colour)
        val shieldRow = king.row + if (colour == Colour.WHITE) -1 else 1
        if (shieldRow !in 0..7) return 0

        var pawns = 0
        for (dc in -1..1) {
            val col = king.col + dc
            if (col in 0..7) {
                val piece = board.getPiece(Square(col, shieldRow))
                if (piece != null && piece.type == PieceType.PAWN && piece.colour == colour) {
                    pawns++
                }
            }
        }
        return pawns
    }

    private fun isPassed(square: Square, colour: Colour, enemyPawns: List<Square>): Boolean {
        return enemyPawns.none { enemy ->
            abs(enemy.col - square.col) <= 1 && isAhead(enemy.row, square.row, colour)
        }
    }

    /** True when [otherRow] lies further up the board (toward promotion) than [pawnRow] for [colour]. */
    private fun isAhead(otherRow: Int, pawnRow: Int, colour: Colour): Boolean {
        return if (colour == Colour.WHITE) otherRow < pawnRow else otherRow > pawnRow
    }

    /** 0 on the starting rank, growing to 5 one step from promotion. */
    private fun advancement(square: Square, colour: Colour): Int {
        val raw = if (colour == Colour.WHITE) 6 - square.row else square.row - 1
        return raw.coerceIn(0, PASSED_MG.size - 1)
    }

    private fun inEnemyHalf(square: Square, colour: Colour): Boolean {
        return if (colour == Colour.WHITE) square.row <= 3 else square.row >= 4
    }

    private fun taper(middlegame: Int, endgame: Int, phase: Int): Int {
        return (middlegame * phase + endgame * (PieceSquareTables.MAX_PHASE - phase)) / PieceSquareTables.MAX_PHASE
    }

    private companion object {
        const val DOUBLED_PENALTY = 15
        const val ISOLATED_PENALTY = 15
        const val BISHOP_PAIR_BONUS = 30
        const val ROOK_OPEN_FILE = 25
        const val ROOK_HALF_OPEN = 12
        const val KING_SHIELD_PER_PAWN = 12
        const val SPACE_BONUS = 4

        // Indexed by advancement (0..6); endgame values are larger since passers matter far
        // more once pieces are traded off.
        val PASSED_MG = intArrayOf(0, 5, 10, 20, 35, 60, 90)
        val PASSED_EG = intArrayOf(0, 15, 25, 45, 75, 120, 160)
    }
}
