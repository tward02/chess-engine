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
