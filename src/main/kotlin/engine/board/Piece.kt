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
