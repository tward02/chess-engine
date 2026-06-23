package com.tward.ui.board

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType

/**
 * The pieces each side has captured and the running material score, derived from a board position.
 * [whiteAdvantage] is white-minus-black in coarse piece values (pawn 1 … queen 9); positive favours
 * White. Captured lists are computed as "starting set minus what's on the board", so they may be
 * approximate after promotions, but the advantage uses live material and stays exact.
 */
data class CapturedMaterial(
    val capturedByWhite: List<Piece>,   // Black pieces White has taken
    val capturedByBlack: List<Piece>,   // White pieces Black has taken
    val whiteAdvantage: Int
)

private val STARTING_COUNTS = mapOf(
    PieceType.PAWN to 8,
    PieceType.KNIGHT to 2,
    PieceType.BISHOP to 2,
    PieceType.ROOK to 2,
    PieceType.QUEEN to 1
)

fun capturedMaterial(board: Board): CapturedMaterial {
    val pieces = board.getPieces()
    val white = pieces.filter { it.colour == Colour.WHITE }
    val black = pieces.filter { it.colour == Colour.BLACK }

    val whiteMaterial = white.sumOf { it.value() }
    val blackMaterial = black.sumOf { it.value() }

    return CapturedMaterial(
        capturedByWhite = missingPieces(black, Colour.BLACK),
        capturedByBlack = missingPieces(white, Colour.WHITE),
        whiteAdvantage = whiteMaterial - blackMaterial
    )
}

private fun missingPieces(current: List<Piece>, colour: Colour): List<Piece> {
    val captured = mutableListOf<Piece>()
    for ((type, startCount) in STARTING_COUNTS) {
        val onBoard = current.count { it.type == type }
        repeat((startCount - onBoard).coerceAtLeast(0)) { captured.add(Piece(type, colour)) }
    }
    return captured
}
