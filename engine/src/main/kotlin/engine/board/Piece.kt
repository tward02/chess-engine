package com.tward.engine.board

data class Piece(var type: PieceType, var colour: Colour) {

    fun toFENChar(): Char {
        var char = pieceChar()

        if (colour == Colour.WHITE) {
            char = char.uppercaseChar()
        }

        return char
    }

    fun toUnicode(): String {

        return when (colour) {
            Colour.WHITE -> when (type) {
                PieceType.KING -> "♔"
                PieceType.QUEEN -> "♕"
                PieceType.ROOK -> "♖"
                PieceType.BISHOP -> "♗"
                PieceType.KNIGHT -> "♘"
                PieceType.PAWN -> "♙"
            }

            Colour.BLACK -> when (type) {
                PieceType.KING -> "♚"
                PieceType.QUEEN -> "♛"
                PieceType.ROOK -> "♜"
                PieceType.BISHOP -> "♝"
                PieceType.KNIGHT -> "♞"
                PieceType.PAWN -> "♟"
            }
        }
    }

    fun resourceName(): String {
        val colour =
            if (colour == Colour.WHITE) "w"
            else "b"

        return "$colour${this.pieceChar()}.svg.webp"
    }

    fun value(): Int {
        return type.value()
    }

    fun pieceChar(): Char {
        return type.char()
    }
}

enum class PieceType {
    PAWN,
    ROOK,
    KNIGHT,
    BISHOP,
    QUEEN,
    KING
}

// Coarse material scale used for ordering; evaluators use their own finely-tuned centipawn values.
fun PieceType.value(): Int {
    return when (this) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT -> 3
        PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        PieceType.KING -> 0
    }
}

fun PieceType.char(): Char {
    return when (this) {
        PieceType.PAWN -> 'p'
        PieceType.KNIGHT -> 'n'
        PieceType.BISHOP -> 'b'
        PieceType.ROOK -> 'r'
        PieceType.QUEEN -> 'q'
        PieceType.KING -> 'k'
    }
}

enum class Colour {
    WHITE,
    BLACK;

    fun opposite() = if (this == WHITE) BLACK else WHITE
}
