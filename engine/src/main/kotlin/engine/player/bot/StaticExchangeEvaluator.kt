package com.tward.engine.player.bot

import com.tward.engine.board.Attacks
import com.tward.engine.board.Bitboards
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType

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

    private val gains = ThreadLocal.withInitial { IntArray(MAX_EXCHANGE) }

    fun evaluate(board: Board, move: Move): Int {
        val captured = move.capturedPiece ?: return 0
        val mover = move.piece ?: return 0
        // En passant lands on an empty square; SEE on that square doesn't model it, so skip it.
        if (board.getPiece(move.to) == null) return 0

        val to = Bitboards.index(move.to)
        var removed = 1L shl Bitboards.index(move.from)

        val gain = gains.get()
        var count = 1
        gain[0] = value(captured.type)                 // we win the captured piece...
        var onSquareValue = value(mover.type)          // ...and our mover now sits on the square
        var side = mover.colour.opposite()

        while (count < MAX_EXCHANGE) {
            val attacker = leastValuableAttacker(board, to, side, removed)
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
     * `square | type.ordinal << 8`; [NONE] if there is none. Within a piece type the candidates are
     * tried in the generator's direction order, so which of two equal attackers goes first (and so
     * which x-ray opens) never changes. Bitboard tests skip the scans that can't find anything.
     */
    private fun leastValuableAttacker(board: Board, to: Int, side: Colour, removed: Long): Int {
        val available = board.pieces(side) and removed.inv()

        // A `side` pawn attacks the target from where an opposing pawn on the target would attack;
        // the lower index is the left-hand one, the order the scan always used.
        val pawns = Attacks.pawn(side.opposite(), to) and board.pieces(side, PieceType.PAWN) and available
        if (pawns != 0L) return java.lang.Long.numberOfTrailingZeros(pawns) or (PieceType.PAWN.ordinal shl 8)

        if (Attacks.knight(to) and board.pieces(side, PieceType.KNIGHT) and available != 0L) {
            for (sq in Attacks.knightTargets(to)) {
                if (removed and (1L shl sq) == 0L && board.getPiece(Bitboards.col(sq), Bitboards.row(sq)).isA(side, PieceType.KNIGHT)) {
                    return sq or (PieceType.KNIGHT.ordinal shl 8)
                }
            }
        }

        // The first slider met on any diagonal / straight ray: a bishop or rook is taken at once, a
        // queen only after both (it's dearer).
        val occupied = board.occupied and removed.inv()
        val queens = board.pieces(side, PieceType.QUEEN)
        val diagonal = if (Attacks.bishop(to, occupied) and (board.pieces(side, PieceType.BISHOP) or queens) and available != 0L) {
            firstSliderAttacker(board, to, side, removed, 0, PieceType.BISHOP)
        } else NONE
        if (diagonal != NONE && diagonal ushr 8 == PieceType.BISHOP.ordinal) return diagonal
        val straight = if (Attacks.rook(to, occupied) and (board.pieces(side, PieceType.ROOK) or queens) and available != 0L) {
            firstSliderAttacker(board, to, side, removed, 4, PieceType.ROOK)
        } else NONE
        if (straight != NONE && straight ushr 8 == PieceType.ROOK.ordinal) return straight
        if (diagonal != NONE) return diagonal
        if (straight != NONE) return straight

        val king = Attacks.king(to) and board.pieces(side, PieceType.KING) and available
        if (king != 0L) return java.lang.Long.numberOfTrailingZeros(king) or (PieceType.KING.ordinal shl 8)

        return NONE
    }

    /** First non-removed piece along the four rays from [firstDirection] (in order), if it is a [side] [straightType] or queen. */
    private fun firstSliderAttacker(
        board: Board,
        to: Int,
        side: Colour,
        removed: Long,
        firstDirection: Int,
        straightType: PieceType
    ): Int {
        val occupied = board.occupied
        for (direction in firstDirection until firstDirection + 4) {
            for (sq in Attacks.ray(direction, to)) {
                val bit = 1L shl sq
                if (removed and bit != 0L || occupied and bit == 0L) continue
                val piece = board.getPiece(Bitboards.col(sq), Bitboards.row(sq))!!
                if (piece.colour == side && (piece.type == straightType || piece.type == PieceType.QUEEN)) {
                    return sq or (piece.type.ordinal shl 8)
                }
                break   // a blocker that isn't a matching attacker stops this ray
            }
        }
        return NONE
    }

    private fun Piece?.isA(colour: Colour, type: PieceType): Boolean = this != null && this.colour == colour && this.type == type

    private fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 10_000
    }
}
