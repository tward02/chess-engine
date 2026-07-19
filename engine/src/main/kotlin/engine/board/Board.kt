package com.tward.engine.board

import kotlin.math.abs

class Board {

    private val grid: Array<Array<Piece?>> = Array(8) { Array(8) { null } }

    // Cached king square per colour (index by Colour.ordinal), kept current by [setPiece] whenever a
    // king is placed. A pure optimisation for [findKing] on the search hot path; [findKing] validates
    // the cache and falls back to a full scan if it is ever stale, so correctness never depends on it.
    private val kingSquares = arrayOfNulls<Square>(2)

    // Restorable per-move state (castling rights, EP target, clocks) packed into a long each —
    // makeMove/undoMove run at every search node, so this stays allocation-free.
    private var stateStack = LongArray(256)
    private var stateCount = 0

    // XOR of ZobristKeys.pieceKey for every piece on the board, maintained by [setPiece] (the only
    // grid mutator besides [clearBoard]/[copy], which reset/copy it). Makes [zobristKey] O(1).
    private var pieceHash = 0L

    var activeColour: Colour = Colour.WHITE
    var enPassantTarget: Square? = null

    var whiteCanCastleKingside: Boolean = true
    var whiteCanCastleQueenside: Boolean = true
    var blackCanCastleKingside: Boolean = true
    var blackCanCastleQueenside: Boolean = true

    var whiteHasCastled: Boolean = false
    var blackHasCastled: Boolean = false

    var halfMoveClock = 0
    var fullMoveNumber = 0

    fun getPiece(square: Square): Piece? = grid[square.row][square.col]

    /**
     * The position's 64-bit Zobrist hash (pieces + side to move + castling rights + en-passant
     * file), computed in O(1) from the incrementally maintained piece hash. Used as the
     * transposition-table key and for repetition detection.
     */
    val zobristKey: Long
        get() {
            var h = pieceHash
            if (activeColour == Colour.BLACK) h = h xor ZobristKeys.blackToMove
            if (whiteCanCastleKingside) h = h xor ZobristKeys.castling[0]
            if (whiteCanCastleQueenside) h = h xor ZobristKeys.castling[1]
            if (blackCanCastleKingside) h = h xor ZobristKeys.castling[2]
            if (blackCanCastleQueenside) h = h xor ZobristKeys.castling[3]
            enPassantTarget?.let { h = h xor ZobristKeys.enPassantFile[it.col] }
            return h
        }

    fun setPiece(square: Square, piece: Piece?) {
        val previous = grid[square.row][square.col]
        if (previous != null) pieceHash = pieceHash xor ZobristKeys.pieceKey(previous, square.col, square.row)
        if (piece != null) pieceHash = pieceHash xor ZobristKeys.pieceKey(piece, square.col, square.row)
        grid[square.row][square.col] = piece
        // makeMove always writes the king's destination before clearing its origin, so tracking
        // placements here keeps the cache correct through normal moves, castling and FEN setup.
        if (piece != null && piece.type == PieceType.KING) {
            kingSquares[piece.colour.ordinal] = square
        }
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
        whiteHasCastled = false
        blackHasCastled = false
    }

    fun clearBoard() {
        for (r in 0..7) {
            for (c in 0..7) {
                grid[r][c] = null
            }
        }
        pieceHash = 0L
    }

    fun copy(): Board {
        val board = Board()
        grid.forEachIndexed { r, row ->
            row.forEachIndexed { c, piece ->
                board.grid[r][c] = if (piece == null) null else Piece(piece.type, piece.colour)
            }
        }
        board.activeColour = activeColour
        board.enPassantTarget = enPassantTarget
        board.whiteCanCastleKingside = whiteCanCastleKingside
        board.whiteCanCastleQueenside = whiteCanCastleQueenside
        board.blackCanCastleKingside = blackCanCastleKingside
        board.blackCanCastleQueenside = blackCanCastleQueenside
        board.whiteHasCastled = whiteHasCastled
        board.blackHasCastled = blackHasCastled
        board.halfMoveClock = halfMoveClock
        board.fullMoveNumber = fullMoveNumber
        board.pieceHash = pieceHash   // the grid writes above bypass setPiece
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
            } else if (move.capturedPiece != null && move.to == savedEnPassantTarget()) {
                val capturePawnRow = if (movingPiece.colour == Colour.WHITE) move.to.row + 1 else move.to.row - 1
                setPiece(Square(move.to.col, capturePawnRow), null)
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

            if (movingPiece?.colour == Colour.WHITE) {
                whiteHasCastled = true
            } else {
                blackHasCastled = true
            }
        }

        updateCastlingRights(move)

        if (movingPiece?.type == PieceType.PAWN || move.capturedPiece != null) {
            halfMoveClock = 0
        } else {
            halfMoveClock++
        }

        if (activeColour == Colour.BLACK) {
            fullMoveNumber++
        }

        activeColour = activeColour.opposite()
    }

    fun undoMove(move: Move) {

        activeColour = activeColour.opposite()
        restoreState()

        var movedPiece = getPiece(move.to)

        if (move.promotionType != null) {
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
        val cached = kingSquares[colour.ordinal]
        if (cached != null) {
            val piece = grid[cached.row][cached.col]
            if (piece != null && piece.type == PieceType.KING && piece.colour == colour) {
                return cached
            }
        }

        // Cache miss or stale (e.g. after a raw grid copy): scan, repair the cache, and return.
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = grid[row][col]
                if (piece != null && piece.type == PieceType.KING && piece.colour == colour) {
                    val square = Square(col, row)
                    kingSquares[colour.ordinal] = square
                    return square
                }
            }
        }
        throw IllegalStateException("Critical Error: $colour King is missing from the board!")
    }

    fun toFEN(isFullFEN: Boolean = true): String {
        return toFEN(this, isFullFEN)
    }

    fun getPieces(): List<Piece> {
        return grid.flatten().filterNotNull()
    }

    /**
     * Visits every piece without allocating (unlike [getPiecesWithSquares]) — for evaluators on the
     * search hot path, where the per-call list/pair/square allocations dominate the board scan.
     */
    fun forEachPiece(action: (col: Int, row: Int, piece: Piece) -> Unit) {
        for (row in 0..7) {
            val gridRow = grid[row]
            for (col in 0..7) {
                val piece = gridRow[col]
                if (piece != null) action(col, row, piece)
            }
        }
    }

    fun getPiecesWithSquares(): List<Pair<Square, Piece>> {
        val pieces = mutableListOf<Pair<Square, Piece>>()
        for (row in 0..7) {
            for (col in 0..7) {
                val square = Square(col, row)
                val piece = getPiece(square)
                if (piece != null) {
                    pieces.add(square to piece)
                }
            }
        }
        return pieces
    }

    // Returns the first match; duplicates are possible (e.g. promoted queens)
    fun findPiece(piece: Piece): Square? {
        for (row in 0..7) {
            for (col in 0..7) {

                val foundPiece =
                    getPiece(Square(col, row))

                if (foundPiece != null && foundPiece.type == piece.type && foundPiece.colour == piece.colour) {
                    return Square(col, row)
                }
            }
        }
        return null
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
        var packed = 0L
        enPassantTarget?.let { packed = packed or 1L or (it.col.toLong() shl 1) or (it.row.toLong() shl 4) }
        if (whiteCanCastleKingside) packed = packed or (1L shl 7)
        if (whiteCanCastleQueenside) packed = packed or (1L shl 8)
        if (blackCanCastleKingside) packed = packed or (1L shl 9)
        if (blackCanCastleQueenside) packed = packed or (1L shl 10)
        if (whiteHasCastled) packed = packed or (1L shl 11)
        if (blackHasCastled) packed = packed or (1L shl 12)
        packed = packed or (halfMoveClock.toLong() shl 13) or (fullMoveNumber.toLong() shl 29)

        if (stateCount == stateStack.size) stateStack = stateStack.copyOf(stateStack.size * 2)
        stateStack[stateCount++] = packed
    }

    private fun restoreState() {
        val packed = stateStack[--stateCount]
        enPassantTarget = if (packed and 1L != 0L) {
            Square(((packed shr 1) and 0x7L).toInt(), ((packed shr 4) and 0x7L).toInt())
        } else {
            null
        }
        whiteCanCastleKingside = packed and (1L shl 7) != 0L
        whiteCanCastleQueenside = packed and (1L shl 8) != 0L
        blackCanCastleKingside = packed and (1L shl 9) != 0L
        blackCanCastleQueenside = packed and (1L shl 10) != 0L
        whiteHasCastled = packed and (1L shl 11) != 0L
        blackHasCastled = packed and (1L shl 12) != 0L
        halfMoveClock = ((packed shr 13) and 0xFFFFL).toInt()
        fullMoveNumber = (packed shr 29).toInt()
    }

    private fun savedEnPassantTarget(): Square? {
        val packed = stateStack[stateCount - 1]
        if (packed and 1L == 0L) return null
        return Square(((packed shr 1) and 0x7L).toInt(), ((packed shr 4) and 0x7L).toInt())
    }

    companion object {

        fun getStartingBoard(): Board {
            val board = Board()
            board.setupStandardPosition()
            return board
        }

        fun fromFEN(fen: String): Board {
            val board = Board()
            val parts = fen.split(" ")
            val boardLayout = parts[0]
            val activeColourChar = parts[1]
            val castlingRights = parts[2]
            val enPassantTargetSquare = parts[3]
            val halfMoveClock = parts[4].toInt()
            val fullMoveNumber = parts[5].toInt()

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

            board.halfMoveClock = halfMoveClock
            board.fullMoveNumber = fullMoveNumber

            return board
        }

        fun toFEN(board: Board, isFullFEN: Boolean = true): String {
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

            fen += (" ${board.enPassantTarget?.toString() ?: "-"}")

            if (isFullFEN) {
                fen += (" ${board.halfMoveClock} ${board.fullMoveNumber}")
            }

            return fen
        }
    }
}
