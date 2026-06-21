package com.tward.engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.MoveGenerator

/**
 * Static Exchange Evaluation (SEE): the material a capture wins or loses if both sides keep taking on
 * the destination square with their least valuable attacker until neither wants to continue. Used by
 * [AdvancedNegamaxBot] to prune obviously losing captures in quiescence (SEE < 0).
 *
 * It works directly on the board (no copying): squares of already-used attackers are tracked in a
 * "removed" set, which doubles as x-ray handling — a slider behind a captured attacker is revealed
 * when the ray scan skips the removed square. Values are the engine's centipawn scale; the king is
 * effectively infinite so a side never plays a capture that leaves its king takeable.
 */
internal object StaticExchangeEvaluator {

    fun evaluate(board: Board, move: Move): Int {
        val captured = move.capturedPiece ?: return 0
        val mover = move.piece ?: return 0
        // En passant lands on an empty square; SEE on that square doesn't model it, so skip it.
        if (board.getPiece(move.to) == null) return 0

        val to = move.to
        val removed = HashSet<Square>()
        removed.add(move.from)

        val gain = ArrayList<Int>()
        gain.add(value(captured.type))                 // we win the captured piece...
        var onSquareValue = value(mover.type)          // ...and our mover now sits on the square
        var side = mover.colour.opposite()

        while (true) {
            val attacker = leastValuableAttacker(board, to, side, removed) ?: break
            gain.add(onSquareValue - gain.last())      // side to move can win the piece on the square
            onSquareValue = value(attacker.second)
            removed.add(attacker.first)
            side = side.opposite()
        }

        // Minimax the capture sequence back to the root: at each step the side could have stopped.
        for (i in gain.size - 1 downTo 1) {
            gain[i - 1] = -maxOf(-gain[i - 1], gain[i])
        }
        return gain[0]
    }

    /** The square and type of [side]'s least valuable piece attacking [to], skipping [removed]. */
    private fun leastValuableAttacker(
        board: Board,
        to: Square,
        side: Colour,
        removed: Set<Square>
    ): Pair<Square, PieceType>? {
        // Pawns: a `side` pawn attacks `to` from one rank "behind" the direction it pushes.
        val pawnRow = to.row + if (side == Colour.WHITE) 1 else -1
        for (dc in intArrayOf(-1, 1)) {
            val sq = squareOrNull(to.col + dc, pawnRow) ?: continue
            if (sq !in removed && isPiece(board, sq, side, PieceType.PAWN)) return sq to PieceType.PAWN
        }

        for (offset in MoveGenerator.knightOffsets) {
            val sq = squareOrNull(to.col + offset.first, to.row + offset.second) ?: continue
            if (sq !in removed && isPiece(board, sq, side, PieceType.KNIGHT)) return sq to PieceType.KNIGHT
        }

        // Diagonal sliders (bishop, then queen handled by overall min ordering below).
        bishopLike(board, to, side, removed)?.let { if (it.second == PieceType.BISHOP) return it }
        rookLike(board, to, side, removed)?.let { if (it.second == PieceType.ROOK) return it }

        // Queens reachable along either ray are next cheapest after rooks.
        bishopLike(board, to, side, removed)?.let { if (it.second == PieceType.QUEEN) return it }
        rookLike(board, to, side, removed)?.let { if (it.second == PieceType.QUEEN) return it }

        for (offset in MoveGenerator.kingOffsets) {
            val sq = squareOrNull(to.col + offset.first, to.row + offset.second) ?: continue
            if (sq !in removed && isPiece(board, sq, side, PieceType.KING)) return sq to PieceType.KING
        }

        return null
    }

    /** First non-removed piece along each diagonal ray from [to], if it is a [side] bishop or queen. */
    private fun bishopLike(board: Board, to: Square, side: Colour, removed: Set<Square>): Pair<Square, PieceType>? {
        return firstSliderAttacker(board, to, side, removed, MoveGenerator.bishopOffsets, PieceType.BISHOP)
    }

    private fun rookLike(board: Board, to: Square, side: Colour, removed: Set<Square>): Pair<Square, PieceType>? {
        return firstSliderAttacker(board, to, side, removed, MoveGenerator.rookOffsets, PieceType.ROOK)
    }

    private fun firstSliderAttacker(
        board: Board,
        to: Square,
        side: Colour,
        removed: Set<Square>,
        directions: List<Pair<Int, Int>>,
        straightType: PieceType
    ): Pair<Square, PieceType>? {
        for ((dCol, dRow) in directions) {
            var col = to.col + dCol
            var row = to.row + dRow
            while (col in 0..7 && row in 0..7) {
                val sq = Square(col, row)
                if (sq !in removed) {
                    val piece = board.getPiece(sq)
                    if (piece != null) {
                        if (piece.colour == side && (piece.type == straightType || piece.type == PieceType.QUEEN)) {
                            return sq to piece.type
                        }
                        break   // a blocker that isn't a matching attacker stops this ray
                    }
                }
                col += dCol
                row += dRow
            }
        }
        return null
    }

    private fun isPiece(board: Board, square: Square, colour: Colour, type: PieceType): Boolean {
        val piece = board.getPiece(square)
        return piece != null && piece.colour == colour && piece.type == type
    }

    private fun squareOrNull(col: Int, row: Int): Square? =
        if (col in 0..7 && row in 0..7) Square(col, row) else null

    private fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 10_000
    }
}
