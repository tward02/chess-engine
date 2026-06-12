package com.tward.engine.board

data class Piece(var type: PieceType, var colour: Colour) {

    fun toFENChar(): Char {
        var char = when (type) {
            PieceType.PAWN -> 'P'
            PieceType.ROOK -> 'R'
            PieceType.KNIGHT -> 'N'
            PieceType.BISHOP -> 'B'
            PieceType.QUEEN -> 'Q'
            PieceType.KING -> 'K'
        }

        if (colour == Colour.BLACK) {
            char = char.lowercaseChar()
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

        val piece =
            when (type) {
                PieceType.PAWN -> "p"
                PieceType.KNIGHT -> "n"
                PieceType.BISHOP -> "b"
                PieceType.ROOK -> "r"
                PieceType.QUEEN -> "q"
                PieceType.KING -> "k"
            }

        return "$colour$piece.svg.webp"
    }

    fun value(): Int {
        return when (type) {
            PieceType.PAWN -> 1
            PieceType.KNIGHT -> 3
            PieceType.BISHOP -> 3
            PieceType.ROOK -> 5
            PieceType.QUEEN -> 9
            PieceType.KING -> 0
        }
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

enum class Colour {
    WHITE,
    BLACK;

    fun opposite() = if (this == WHITE) BLACK else WHITE
}
