package com.tward.engine.board

import kotlin.math.abs

class Board {

    private val grid: Array<Array<Piece?>> = Array(8) { Array(8) { null } }

    private val stateHistory = ArrayDeque<BoardState>()

    var activeColour: Colour = Colour.WHITE
    var enPassantTarget: Square? = null

    var whiteCanCastleKingside: Boolean = true
    var whiteCanCastleQueenside: Boolean = true
    var blackCanCastleKingside: Boolean = true
    var blackCanCastleQueenside: Boolean = true

    fun getPiece(square: Square): Piece? = grid[square.row][square.col]

    fun setPiece(square: Square, piece: Piece?) {
        grid[square.row][square.col] = piece
    }

    fun setupStandardPosition() {
        for (col in 0..7) {
            setPiece(Square(col, 1), Piece(PieceType.PAWN, Colour.BLACK))
            setPiece(Square(col, 6), Piece(PieceType.PAWN, Colour.WHITE))
        }

        val backlineTypes = arrayOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )

        for (col in 0..7) {
            setPiece(Square(col, 0), Piece(backlineTypes[col], Colour.BLACK))
            setPiece(Square(col, 7), Piece(backlineTypes[col], Colour.WHITE))
        }

        activeColour = Colour.WHITE
        enPassantTarget = null
        whiteCanCastleKingside = true
        whiteCanCastleQueenside = true
        blackCanCastleKingside = true
        blackCanCastleQueenside = true
    }

    fun clearBoard() {
        for (r in 0..7) {
            for (c in 0..7) {
                grid[r][c] = null
            }
        }
    }

    fun copy(): Board {
        val board = Board()
        board.grid.forEachIndexed { r, row ->
            row.forEachIndexed { c, piece ->
                board.grid[r][c] = piece?.copy()
            }
        }
        board.activeColour = activeColour
        board.enPassantTarget = enPassantTarget
        board.whiteCanCastleKingside = whiteCanCastleKingside
        board.whiteCanCastleQueenside = whiteCanCastleQueenside
        board.blackCanCastleKingside = blackCanCastleKingside
        board.blackCanCastleQueenside = blackCanCastleQueenside
        return board
    }

    fun makeMove(move: Move) {

        saveSate()
         var movingPiece = getPiece(move.from)

        if (move.promotionType != null) {
            movingPiece = Piece(move.promotionType, movingPiece!!.colour)
        }

        setPiece(move.to, movingPiece)
        setPiece(move.from, null)

        enPassantTarget = null

        if (movingPiece?.type == PieceType.PAWN) {
            if (abs(move.from.row - move.to.row) == 2) {
                val direction = if (movingPiece.colour == Colour.WHITE) -1 else 1
                enPassantTarget = Square(move.from.col, move.from.row + direction)
            } else if (move.capturedPiece != null && move.to == stateHistory.last().enPassantTarget) {

                if (move.to == stateHistory.last().enPassantTarget) {
                    val capturePawnRow = if (movingPiece.colour == Colour.WHITE) move.to.row + 1 else move.to.row - 1
                    setPiece(Square(move.to.col, capturePawnRow), null)
                }
            }
        } else if (move.isCastling) {
            val row = move.to.row
            if (move.to.col == 6) {
                setPiece(Square(5, row), getPiece(Square(7, row)))
                setPiece(Square(7, row), null)
            } else if (move.to.col == 2) {
                setPiece(Square(3, row), getPiece(Square(0, row)))
                setPiece(Square(0, row), null)
            }
        }

        updateCastlingRights(move)
        activeColour = activeColour.opposite()
    }

    fun undoMove(move: Move) {

        activeColour = activeColour.opposite()
        restoreState()

        var movedPiece = getPiece(move.to)

        if  (move.promotionType != null) {
            movedPiece = Piece(PieceType.PAWN, movedPiece!!.colour)
        }

        setPiece(move.from, movedPiece)

        if (move.capturedPiece != null) {
            if (movedPiece?.type == PieceType.PAWN && move.to == enPassantTarget) {
                setPiece(move.to, null)
                val capturedPawnRow = if (movedPiece.colour == Colour.WHITE) move.to.row + 1 else move.to.row - 1
                setPiece(Square(move.to.col, capturedPawnRow), move.capturedPiece)
            } else {
                setPiece(move.to, move.capturedPiece)
            }
        } else {
            setPiece(move.to, null)
        }

        if (move.isCastling) {
            val row = move.to.row
            if (move.to.col == 6) {
                setPiece(Square(7, row), getPiece(Square(5, row)))
                setPiece(Square(5, row), null)
            } else if (move.to.col == 2) {
                setPiece(Square(0, row), getPiece(Square(3, row)))
                setPiece(Square(3, row), null)
            }
        }
    }

    fun findKing(colour: Colour): Square {
        for (row in 0..7) {
            for (col in 0..7) {
                val square = Square(col, row)
                val piece = getPiece(square)
                if (piece != null && piece.type == PieceType.KING && piece.colour == colour) {
                    return square
                }
            }
        }
        throw IllegalStateException("Critical Error: $colour King is missing from the board!")
    }

    fun toFEN(): String {
        return toFEN(this)
    }

    private fun updateCastlingRights(move: Move) {

        val movingPiece = getPiece(move.to)

        if (movingPiece?.type == PieceType.KING) {
            if (movingPiece.colour == Colour.WHITE) {
                whiteCanCastleKingside = false
                whiteCanCastleQueenside = false
            } else {
                blackCanCastleKingside = false
                blackCanCastleQueenside = false
            }
        }

        if (movingPiece?.type == PieceType.ROOK) {
            when (move.from) {
                Square(0, 7) -> whiteCanCastleQueenside = false
                Square(7, 7) -> whiteCanCastleKingside = false
                Square(0, 0) -> blackCanCastleQueenside = false
                Square(7, 0) -> blackCanCastleKingside = false
            }
        }

        val capturedPiece = move.capturedPiece

        if (capturedPiece?.type == PieceType.ROOK) {
            when (move.to) {
                Square(0, 7) -> whiteCanCastleQueenside = false
                Square(7, 7) -> whiteCanCastleKingside = false
                Square(0, 0) -> blackCanCastleQueenside = false
                Square(7, 0) -> blackCanCastleKingside = false
            }
        }
    }

    private fun saveSate() {
        stateHistory.addLast(
            BoardState(
                enPassantTarget,
                whiteCanCastleKingside,
                whiteCanCastleQueenside,
                blackCanCastleKingside,
                blackCanCastleQueenside
            )
        )
    }

    private fun restoreState() {
        val previousSate = stateHistory.removeLast()
        enPassantTarget = previousSate.enPassantTarget
        whiteCanCastleKingside = previousSate.whiteCanCastleKingside
        whiteCanCastleQueenside = previousSate.whiteCanCastleQueenside
        blackCanCastleKingside = previousSate.blackCanCastleKingside
        blackCanCastleQueenside = previousSate.blackCanCastleQueenside
    }

    companion object {

        fun fromFEN(fen: String): Board {
            val board = Board()
            val parts = fen.split(" ")
            val boardLayout = parts[0]
            val activeColourChar = parts[1]
            val castlingRights = parts[2]
            val enPassantTargetSquare = parts[3]

            var row = 0
            var col = 0

            for (char in boardLayout) {
                when {
                    char == '/' -> {
                        row++
                        col = 0
                    }

                    char.isDigit() -> {
                        col += char.toString().toInt()
                    }

                    else -> {
                        val colour = if (char.isUpperCase()) Colour.WHITE else Colour.BLACK
                        val type = when (char.lowercaseChar()) {
                            'p' -> PieceType.PAWN
                            'n' -> PieceType.KNIGHT
                            'b' -> PieceType.BISHOP
                            'r' -> PieceType.ROOK
                            'q' -> PieceType.QUEEN
                            'k' -> PieceType.KING
                            else -> throw IllegalArgumentException("Invalid piece type: $char")
                        }
                        board.setPiece(Square(col, row), Piece(type, colour))
                        col++
                    }
                }
            }

            board.activeColour = if (activeColourChar == "w") Colour.WHITE else Colour.BLACK

            board.whiteCanCastleKingside = castlingRights.contains("K")
            board.whiteCanCastleQueenside = castlingRights.contains("Q")
            board.blackCanCastleKingside = castlingRights.contains("k")
            board.blackCanCastleQueenside = castlingRights.contains("q")

            if (enPassantTargetSquare != "-") {
                val file = enPassantTargetSquare[0] - 'a'
                val rank = 8 - enPassantTargetSquare[1].toString().toInt()
                board.enPassantTarget = Square(file, rank)
            } else {
                board.enPassantTarget = null
            }

            return board
        }

        fun toFEN(board:Board): String {
            var fen = ""

            for (row in 0..7) {
                var emptyCount = 0
                for (col in 0..7) {
                    val piece = board.getPiece(Square(col, row))
                    if (piece == null) {
                        emptyCount++
                    } else {
                        if (emptyCount > 0) {
                            fen += emptyCount
                            emptyCount = 0
                        }
                        fen += piece.toFENChar()
                    }
                }

                if (emptyCount > 0) {
                    fen += emptyCount
                }
                if (row < 7) {
                    fen += "/"
                }
            }

            fen += (" ${if (board.activeColour == Colour.WHITE) "w" else "b"}")

            val castling = StringBuilder()
            if (board.whiteCanCastleKingside) castling.append("K")
            if (board.whiteCanCastleQueenside) castling.append("Q")
            if (board.blackCanCastleKingside) castling.append("k")
            if (board.blackCanCastleQueenside) castling.append("q")
            fen += (" ${castling.ifEmpty { "-" }}")

            fen += (" ${board.enPassantTarget?.toAlgebraic() ?: "-"}")


            fen += (" 0 1")

            return fen
        }
    }
}

data class BoardState(
    val enPassantTarget: Square? = null,
    var whiteCanCastleKingside: Boolean,
    var whiteCanCastleQueenside: Boolean,
    var blackCanCastleKingside: Boolean,
    var blackCanCastleQueenside: Boolean,
    val takenPieces: Set<Piece> = emptySet()
)
