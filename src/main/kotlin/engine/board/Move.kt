package com.tward.engine.board

data class Move(
    val from: Square,
    val to: Square,
    val piece: Piece? = null,
    val capturedPiece: Piece? = null,
    val promotionType: PieceType? = null,
    val isCastling: Boolean = false
)
