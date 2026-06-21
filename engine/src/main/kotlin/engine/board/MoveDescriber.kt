package com.tward.engine.board

/**
 * Produces human-readable move notation, e.g. "Ng1-f3", "e7-e8=Q", "exd6 e.p.", "O-O+", "Qd8-h4#".
 *
 * En passant, check and checkmate can't be inferred from a [Move] alone, so the caller provides them.
 */
object MoveDescriber {

    fun describe(
        move: Move,
        isEnPassant: Boolean = false,
        gaveCheck: Boolean = false,
        isCheckmate: Boolean = false
    ): String {

        val core =
            if (move.isCastling) {
                if (move.to.col == 6) "O-O" else "O-O-O"
            } else {
                buildString {
                    append(pieceLetter(move.piece?.type))
                    append(move.from)
                    append(if (move.capturedPiece != null) "x" else "-")
                    append(move.to)
                    move.promotionType?.let { append("=").append(pieceLetter(it)) }
                    if (isEnPassant) append(" e.p.")
                }
            }

        val suffix = when {
            isCheckmate -> "#"
            gaveCheck -> "+"
            else -> ""
        }

        return core + suffix
    }

    // Pawns have no letter; other pieces use their standard initial (knight is N)
    private fun pieceLetter(type: PieceType?): String {
        return when (type) {
            PieceType.KNIGHT -> "N"
            PieceType.BISHOP -> "B"
            PieceType.ROOK -> "R"
            PieceType.QUEEN -> "Q"
            PieceType.KING -> "K"
            PieceType.PAWN, null -> ""
        }
    }
}
