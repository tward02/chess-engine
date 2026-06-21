package com.tward.engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.MoveGenerator

/**
 * [CompactEvaluator] plus the two terms that matter most for play against strong opponents but that
 * the compact evaluator leaves out for speed: **piece mobility** and **king safety**.
 *
 * - Mobility: pseudo-legal destination squares for each side's knights, bishops, rooks and queens.
 *   Active pieces are rewarded; cramped ones penalised. Pins/checks are ignored — it's a fast proxy.
 * - King safety (middlegame-weighted, tapering to nothing in the endgame where the king belongs in
 *   the centre): open/half-open files next to the king and enemy attacks landing on the king's zone.
 *
 * Both terms cost extra move/attack scanning, so it is a few times slower per call than the parent.
 * Scores stay in the shared convention: centipawns from White's perspective. Stateless and thread-safe
 * (it reads no mutable state of its own).
 */
class AdvancedEvaluator : CompactEvaluator() {

    override fun evaluate(game: ChessGame, depth: Int): Int {
        val board = game.board
        var score = super.evaluate(game, depth)

        val phase = PieceSquareTables.gamePhase(board)
        val attacks = MoveGenerator(board)

        val mobility = mobilityScore(board, Colour.WHITE) - mobilityScore(board, Colour.BLACK)
        score += mobility * MOBILITY_WEIGHT

        // King safety is a middlegame concern; fade it out as material comes off.
        val kingDanger = kingDanger(board, attacks, Colour.BLACK) - kingDanger(board, attacks, Colour.WHITE)
        score += kingDanger * phase / PieceSquareTables.MAX_PHASE

        return score
    }

    /** Sum of squares the side's sliding pieces and knights can move to (empty or enemy-occupied). */
    private fun mobilityScore(board: Board, colour: Colour): Int {
        var total = 0
        for ((square, piece) in board.getPiecesWithSquares()) {
            if (piece.colour != colour) continue
            total += when (piece.type) {
                PieceType.KNIGHT -> stepMobility(board, square, colour, MoveGenerator.knightOffsets)
                PieceType.BISHOP -> slideMobility(board, square, colour, MoveGenerator.bishopOffsets)
                PieceType.ROOK -> slideMobility(board, square, colour, MoveGenerator.rookOffsets)
                PieceType.QUEEN -> slideMobility(board, square, colour, MoveGenerator.queenOffsets)
                else -> 0
            }
        }
        return total
    }

    private fun stepMobility(board: Board, from: Square, colour: Colour, offsets: List<Pair<Int, Int>>): Int {
        var count = 0
        for ((dc, dr) in offsets) {
            val col = from.col + dc
            val row = from.row + dr
            if (col in 0..7 && row in 0..7) {
                val piece = board.getPiece(Square(col, row))
                if (piece == null || piece.colour != colour) count++
            }
        }
        return count
    }

    private fun slideMobility(board: Board, from: Square, colour: Colour, offsets: List<Pair<Int, Int>>): Int {
        var count = 0
        for ((dc, dr) in offsets) {
            var col = from.col + dc
            var row = from.row + dr
            while (col in 0..7 && row in 0..7) {
                val piece = board.getPiece(Square(col, row))
                if (piece == null) {
                    count++
                } else {
                    if (piece.colour != colour) count++   // can capture the blocker
                    break
                }
                col += dc
                row += dr
            }
        }
        return count
    }

    /**
     * A positive "danger" penalty against [colour]'s king: open/half-open files beside the king plus
     * enemy pieces attacking the king's 3x3 zone. The caller subtracts each side's danger so a more
     * exposed king lowers that side's score.
     */
    private fun kingDanger(board: Board, attacks: MoveGenerator, colour: Colour): Int {
        val king = board.findKing(colour)
        val enemy = colour.opposite()
        var danger = 0

        // Open / half-open files on and beside the king.
        for (dc in -1..1) {
            val file = king.col + dc
            if (file !in 0..7) continue
            var ownPawns = 0
            var enemyPawns = 0
            for (row in 0..7) {
                val piece = board.getPiece(Square(file, row)) ?: continue
                if (piece.type == PieceType.PAWN) {
                    if (piece.colour == colour) ownPawns++ else enemyPawns++
                }
            }
            if (ownPawns == 0) danger += if (enemyPawns == 0) OPEN_FILE_PENALTY else HALF_OPEN_FILE_PENALTY
        }

        // Enemy attacks landing on the king's zone (the king square and its neighbours).
        for (dc in -1..1) {
            for (dr in -1..1) {
                val col = king.col + dc
                val row = king.row + dr
                if (col in 0..7 && row in 0..7 && attacks.isSquareAttacked(Square(col, row), enemy)) {
                    danger += ZONE_ATTACK_PENALTY
                }
            }
        }

        return danger
    }

    private companion object {
        const val MOBILITY_WEIGHT = 2
        const val ZONE_ATTACK_PENALTY = 6
        const val OPEN_FILE_PENALTY = 14
        const val HALF_OPEN_FILE_PENALTY = 7
    }
}
