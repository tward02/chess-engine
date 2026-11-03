package com.tward.engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.MoveGenerator

/**
 * Static Exchange Evaluation (SEE): the material a capture wins or loses if both sides keep taking on
 * the destination square with their least valuable attacker until neither wants to continue. Used by
 * [AdvancedNegamaxBot] to prune obviously losing captures in quiescence (SEE < 0).
 *
 * It works directly on the board (no copying): squares of already-used attackers are tracked in a
 * "removed" bitmask, which doubles as x-ray handling — a slider behind a captured attacker is
 * revealed when the ray scan skips the removed square. Values are the engine's centipawn scale; the
 * king is effectively infinite so a side never plays a capture that leaves its king takeable.
 * Allocation-free, since it runs on many search nodes.
 */
internal object StaticExchangeEvaluator {

    private const val NONE = -1
    private const val MAX_EXCHANGE = 34   // 32 pieces plus the initial capture, with room to spare

    // The MoveGenerator offset lists as primitive arrays, in the same order.
    private val knightCols = MoveGenerator.knightOffsets.map { it.first }.toIntArray()
    private val knightRows = MoveGenerator.knightOffsets.map { it.second }.toIntArray()
    private val kingCols = MoveGenerator.kingOffsets.map { it.first }.toIntArray()
    private val kingRows = MoveGenerator.kingOffsets.map { it.second }.toIntArray()
    private val bishopCols = MoveGenerator.bishopOffsets.map { it.first }.toIntArray()
    private val bishopRows = MoveGenerator.bishopOffsets.map { it.second }.toIntArray()
    private val rookCols = MoveGenerator.rookOffsets.map { it.first }.toIntArray()
    private val rookRows = MoveGenerator.rookOffsets.map { it.second }.toIntArray()
    private val pawnCols = intArrayOf(-1, 1)

    private val gains = ThreadLocal.withInitial { IntArray(MAX_EXCHANGE) }

    fun evaluate(board: Board, move: Move): Int {
        val captured = move.capturedPiece ?: return 0
        val mover = move.piece ?: return 0
        // En passant lands on an empty square; SEE on that square doesn't model it, so skip it.
        if (board.getPiece(move.to) == null) return 0

        val toCol = move.to.col
        val toRow = move.to.row
        var removed = bit(move.from.col, move.from.row)

        val gain = gains.get()
        var count = 1
        gain[0] = value(captured.type)                 // we win the captured piece...
        var onSquareValue = value(mover.type)          // ...and our mover now sits on the square
        var side = mover.colour.opposite()

        while (count < MAX_EXCHANGE) {
            val attacker = leastValuableAttacker(board, toCol, toRow, side, removed)
            if (attacker == NONE) break
            gain[count] = onSquareValue - gain[count - 1]   // side to move can win the piece on the square
            count++
            onSquareValue = value(PieceType.entries[attacker ushr 8])
            removed = removed or (1L shl (attacker and 63))
            side = side.opposite()
        }

        // Minimax the capture sequence back to the root: at each step the side could have stopped.
        for (i in count - 1 downTo 1) {
            gain[i - 1] = -maxOf(-gain[i - 1], gain[i])
        }
        return gain[0]
    }

    /**
     * [side]'s least valuable piece attacking the target, skipping [removed] squares, packed as
     * `square | type.ordinal << 8`; [NONE] if there is none.
     */
    private fun leastValuableAttacker(board: Board, toCol: Int, toRow: Int, side: Colour, removed: Long): Int {
        // Pawns: a `side` pawn attacks the target from one rank "behind" the direction it pushes.
        val pawnRow = toRow + if (side == Colour.WHITE) 1 else -1
        for (dc in pawnCols) {
            val found = pieceAt(board, toCol + dc, pawnRow, side, PieceType.PAWN, removed)
            if (found != NONE) return found
        }

        for (i in knightCols.indices) {
            val found = pieceAt(board, toCol + knightCols[i], toRow + knightRows[i], side, PieceType.KNIGHT, removed)
            if (found != NONE) return found
        }

        // The first slider met on any diagonal / straight ray: a bishop or rook is taken at once, a
        // queen only after both (it's dearer).
        val diagonal = firstSliderAttacker(board, toCol, toRow, side, removed, bishopCols, bishopRows, PieceType.BISHOP)
        if (diagonal != NONE && diagonal ushr 8 == PieceType.BISHOP.ordinal) return diagonal
        val straight = firstSliderAttacker(board, toCol, toRow, side, removed, rookCols, rookRows, PieceType.ROOK)
        if (straight != NONE && straight ushr 8 == PieceType.ROOK.ordinal) return straight
        if (diagonal != NONE) return diagonal
        if (straight != NONE) return straight

        for (i in kingCols.indices) {
            val found = pieceAt(board, toCol + kingCols[i], toRow + kingRows[i], side, PieceType.KING, removed)
            if (found != NONE) return found
        }

        return NONE
    }

    /** First non-removed piece along the rays (in order), if it is a [side] [straightType] or queen. */
    private fun firstSliderAttacker(
        board: Board,
        toCol: Int,
        toRow: Int,
        side: Colour,
        removed: Long,
        dCols: IntArray,
        dRows: IntArray,
        straightType: PieceType
    ): Int {
        for (d in dCols.indices) {
            val dCol = dCols[d]
            val dRow = dRows[d]
            var col = toCol + dCol
            var row = toRow + dRow
            while (col in 0..7 && row in 0..7) {
                if (removed and bit(col, row) == 0L) {
                    val piece = board.getPiece(col, row)
                    if (piece != null) {
                        if (piece.colour == side && (piece.type == straightType || piece.type == PieceType.QUEEN)) {
                            return (row * 8 + col) or (piece.type.ordinal shl 8)
                        }
                        break   // a blocker that isn't a matching attacker stops this ray
                    }
                }
                col += dCol
                row += dRow
            }
        }
        return NONE
    }

    private fun pieceAt(board: Board, col: Int, row: Int, colour: Colour, type: PieceType, removed: Long): Int {
        if (col !in 0..7 || row !in 0..7 || removed and bit(col, row) != 0L) return NONE
        val piece = board.getPiece(col, row) ?: return NONE
        return if (piece.colour == colour && piece.type == type) (row * 8 + col) or (type.ordinal shl 8) else NONE
    }

    private fun bit(col: Int, row: Int): Long = 1L shl (row * 8 + col)

    private fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 10_000
    }
}
