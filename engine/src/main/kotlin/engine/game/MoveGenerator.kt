package com.tward.engine.game

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.logging.Log

class MoveGenerator(private val board: Board) {

    fun generateLegalMoves(): List<Move> {
        val pseudoLegalMoves = generatePseudoLegalMoves()
        val legalMoves = ArrayList<Move>(pseudoLegalMoves.size)
        val myColour = board.activeColour
        val enemyColour = myColour.opposite()

        val kingSquare = board.findKing(myColour)
        val inCheck = isSquareAttacked(kingSquare, enemyColour)
        // Out of check, only king moves, en-passant captures and moves of pinned pieces can expose
        // the king, so everything else is legal without the expensive make/verify/undo round trip.
        val pinned = if (inCheck) 0L else pinnedMask(kingSquare, myColour)

        for (move in pseudoLegalMoves) {
            if (move.isCastling && !isCastlingPathSafe(move, enemyColour)) {
                continue
            }

            val needsVerification = inCheck ||
                    move.piece?.type == PieceType.KING ||
                    (pinned ushr (move.from.row * 8 + move.from.col)) and 1L == 1L ||
                    isEnPassantCapture(move)

            if (!needsVerification) {
                legalMoves.add(move)
                continue
            }

            board.makeMove(move)

            if (!isSquareAttacked(board.findKing(myColour), enemyColour)) {
                legalMoves.add(move)
            }

            board.undoMove(move)
        }

        return legalMoves
    }

    // An en-passant capture is the one capture whose destination square is empty.
    private fun isEnPassantCapture(move: Move): Boolean =
        move.piece?.type == PieceType.PAWN && move.capturedPiece != null && board.getPiece(move.to) == null

    /**
     * Bitmask (square index `row * 8 + col`) of [colour]'s pieces pinned to their king: along each
     * of the eight rays from the king, the first friendly piece is pinned if the next occupied
     * square past it holds an enemy slider moving on that ray.
     */
    private fun pinnedMask(kingSquare: Square, colour: Colour): Long {
        var mask = 0L
        for ((dCol, dRow) in queenOffsets) {
            val straight = dCol == 0 || dRow == 0
            var col = kingSquare.col + dCol
            var row = kingSquare.row + dRow
            var shieldIndex = -1

            while (col in 0..7 && row in 0..7) {
                val piece = board.getPiece(Square(col, row))
                if (piece != null) {
                    if (shieldIndex < 0) {
                        if (piece.colour != colour) break   // enemy piece first: no pin on this ray
                        shieldIndex = row * 8 + col
                    } else {
                        if (piece.colour != colour &&
                            (piece.type == PieceType.QUEEN ||
                                    piece.type == (if (straight) PieceType.ROOK else PieceType.BISHOP))
                        ) {
                            mask = mask or (1L shl shieldIndex)
                        }
                        break
                    }
                }
                col += dCol
                row += dRow
            }
        }
        return mask
    }

    fun generatePseudoLegalMoves(): List<Move> {
        val moves = mutableListOf<Move>()

        for (row in 0..7) {
            for (col in 0..7) {
                val square = Square(col, row)
                val piece = board.getPiece(square)

                if (piece != null && piece.colour == board.activeColour) {
                    when (piece.type) {
                        PieceType.KNIGHT -> generateKnightMoves(square, piece.colour, moves)
                        PieceType.ROOK -> generateRookMoves(square, piece.colour, moves)
                        PieceType.BISHOP -> generateBishopMoves(square, piece.colour, moves)
                        PieceType.QUEEN -> generateQueenMoves(square, piece.colour, moves)
                        PieceType.KING -> {
                            generateKingMoves(square, piece.colour, moves)
                            generateCastlingMoves(board.activeColour, moves)
                        }

                        PieceType.PAWN -> generatePawnMoves(square, piece.colour, moves)
                    }
                }


            }
        }

        return moves
    }

    fun generateKnightMoves(start: Square, colour: Colour, moves: MutableList<Move>) {
        generateOneOffMoves(start, colour, moves, PieceType.KNIGHT, knightOffsets)
    }

    fun generateRookMoves(start: Square, colour: Colour, moves: MutableList<Move>) {
        generateSlidingMoves(start, colour, moves, PieceType.ROOK, rookOffsets)
    }

    fun generateBishopMoves(start: Square, colour: Colour, moves: MutableList<Move>) {
        generateSlidingMoves(start, colour, moves, PieceType.BISHOP, bishopOffsets)
    }


    fun generateQueenMoves(start: Square, colour: Colour, moves: MutableList<Move>) {
        generateSlidingMoves(start, colour, moves, PieceType.QUEEN, queenOffsets)
    }

    fun generateKingMoves(start: Square, colour: Colour, moves: MutableList<Move>) {
        generateOneOffMoves(start, colour, moves, PieceType.KING, kingOffsets)
    }

    fun generatePawnMoves(start: Square, colour: Colour, moves: MutableList<Move>) {
        val offset = if (colour == Colour.WHITE) -1 else 1
        val startRank = if (colour == Colour.WHITE) 6 else 1
        val promotionRank = if (colour == Colour.WHITE) 0 else 7

        val forwardOneRow = start.row + offset
        if (forwardOneRow in 0..7) {
            val forwardOneSquare = Square(start.col, forwardOneRow)

            if (board.getPiece(forwardOneSquare) == null) {
                addPawnMove(start, forwardOneSquare, colour, null, promotionRank, moves)

                if (start.row == startRank) {
                    val forwardTwoSquare = Square(start.col, start.row + (offset * 2))
                    if (board.getPiece(forwardTwoSquare) == null) {
                        moves.add(Move(start, forwardTwoSquare, Piece(PieceType.PAWN, colour)))
                    }
                }
            }
        }

        val captureCols = listOf(start.col - 1, start.col + 1)

        for (col in captureCols) {
            if (col in 0..7 && forwardOneRow in 0..7) {
                val targetSquare = Square(col, forwardOneRow)
                val targetPiece = board.getPiece(targetSquare)

                if (targetPiece != null && targetPiece.colour != colour) {
                    addPawnMove(start, targetSquare, colour, targetPiece, promotionRank, moves)
                } else if (board.enPassantTarget == targetSquare) {
                    val capturedPawnSquare = Square(col, start.row)
                    val capturedPawn = board.getPiece(capturedPawnSquare)

                    moves.add(
                        Move(
                            from = start,
                            to = targetSquare,
                            piece = Piece(PieceType.PAWN, colour),
                            capturedPiece = capturedPawn
                        )
                    )
                }
            }
        }
    }

    fun generateCastlingMoves(colour: Colour, moves: MutableList<Move>) {
        val row = if (colour == Colour.WHITE) 7 else 0

        val kingStart = Square(4, row)

        if (colour == Colour.WHITE) {

            if (board.whiteCanCastleKingside && board.getPiece(Square(7, row)) == Piece(PieceType.ROOK, Colour.WHITE)) {

                if (board.getPiece(Square(5, row)) == null &&
                    board.getPiece(Square(6, row)) == null
                ) {
                    moves.add(
                        Move(
                            from = kingStart,
                            to = Square(6, row),
                            piece = Piece(PieceType.KING, colour),
                            isCastling = true
                        )
                    )
                }
            }

            if (board.whiteCanCastleQueenside && board.getPiece(Square(0, row)) == Piece(
                    PieceType.ROOK,
                    Colour.WHITE
                )
            ) {

                if (board.getPiece(Square(1, row)) == null &&
                    board.getPiece(Square(2, row)) == null &&
                    board.getPiece(Square(3, row)) == null
                ) {
                    moves.add(
                        Move(
                            from = kingStart,
                            to = Square(2, row),
                            piece = Piece(PieceType.KING, colour),
                            isCastling = true
                        )
                    )
                }
            }
        } else {

            if (board.blackCanCastleKingside && board.getPiece(Square(7, row)) == Piece(PieceType.ROOK, Colour.BLACK)) {

                if (board.getPiece(Square(5, row)) == null &&
                    board.getPiece(Square(6, row)) == null
                ) {
                    moves.add(
                        Move(
                            from = kingStart,
                            to = Square(6, row),
                            piece = Piece(PieceType.KING, colour),
                            isCastling = true
                        )
                    )
                }
            }

            if (board.blackCanCastleQueenside && board.getPiece(Square(0, row)) == Piece(
                    PieceType.ROOK,
                    Colour.BLACK
                )
            ) {

                if (board.getPiece(Square(1, row)) == null &&
                    board.getPiece(Square(2, row)) == null &&
                    board.getPiece(Square(3, row)) == null
                ) {
                    moves.add(
                        Move(
                            from = kingStart,
                            to = Square(2, row),
                            piece = Piece(PieceType.KING, colour),
                            isCastling = true
                        )
                    )
                }
            }
        }
    }

    fun isSquareAttacked(targetSquare: Square, attackerColour: Colour): Boolean {
        for (offset in knightOffsets) {
            val checkCol = targetSquare.col + offset.first
            val checkRow = targetSquare.row + offset.second
            if (checkCol in 0..7 && checkRow in 0..7) {
                val piece = board.getPiece(Square(checkCol, checkRow))
                if (piece != null && piece.colour == attackerColour && piece.type == PieceType.KNIGHT) {
                    return true
                }
            }
        }

        if (isAttackedBySlidingPiece(
                targetSquare,
                attackerColour,
                rookOffsets,
                listOf(PieceType.ROOK, PieceType.QUEEN)
            )
        ) {
            return true
        }

        if (isAttackedBySlidingPiece(
                targetSquare,
                attackerColour,
                bishopOffsets,
                listOf(PieceType.BISHOP, PieceType.QUEEN)
            )
        ) {
            return true
        }

        val direction = if (attackerColour == Colour.WHITE) 1 else -1 // Pawns capture "down" relative to their movement
        for (pawnOffset in listOf(-1, 1)) {
            val checkCol = targetSquare.col + pawnOffset
            val checkRow = targetSquare.row + direction
            if (checkCol in 0..7 && checkRow in 0..7) {
                val piece = board.getPiece(Square(checkCol, checkRow))
                if (piece != null && piece.colour == attackerColour && piece.type == PieceType.PAWN) {
                    return true
                }
            }
        }

        for (offset in kingOffsets) {
            val checkCol = targetSquare.col + offset.first
            val checkRow = targetSquare.row + offset.second
            if (checkCol in 0..7 && checkRow in 0..7) {
                val piece = board.getPiece(Square(checkCol, checkRow))
                if (piece != null && piece.colour == attackerColour && piece.type == PieceType.KING) {
                    return true
                }
            }
        }

        return false
    }

    fun perft(depth: Int): Long {
        if (depth == 0) return 1

        var nodes: Long = 0
        val legalMoves = generateLegalMoves()

        for (move in legalMoves) {
            board.makeMove(move)
            nodes += perft(depth - 1)
            board.undoMove(move)
        }

        return nodes
    }

    fun perftDivide(depth: Int) {
        val moves = generateLegalMoves()
        for (move in moves) {
            board.makeMove(move)
            val nodes = perft(depth - 1)
            log.debug { "${move.from}${move.to}: $nodes" }
            board.undoMove(move)
        }
    }

    private fun isAttackedBySlidingPiece(
        target: Square,
        attackerColour: Colour,
        directions: List<Pair<Int, Int>>,
        attackerTypes: List<PieceType>
    ): Boolean {
        for ((dCol, dRow) in directions) {
            var checkCol = target.col + dCol
            var checkRow = target.row + dRow

            while (checkCol in 0..7 && checkRow in 0..7) {
                val piece = board.getPiece(Square(checkCol, checkRow))
                if (piece != null) {
                    if (piece.colour == attackerColour && attackerTypes.contains(piece.type)) {
                        return true
                    }
                    break
                }
                checkCol += dCol
                checkRow += dRow
            }
        }
        return false
    }

    private fun isCastlingPathSafe(move: Move, enemyColour: Colour): Boolean {
        val row = move.from.row

        if (isSquareAttacked(move.from, enemyColour)) {
            return false
        }

        if (move.to.col == 6) {

            if (isSquareAttacked(Square(5, row), enemyColour)) {
                return false
            }
        } else if (move.to.col == 2) {
            if (isSquareAttacked(Square(3, row), enemyColour)) {
                return false
            }
        }

        return true
    }

    private fun addPawnMove(
        start: Square,
        target: Square,
        colour: Colour,
        captured: Piece?,
        promotionRank: Int,
        moves: MutableList<Move>
    ) {
        if (target.row == promotionRank) {

            moves.add(Move(start, target, Piece(PieceType.PAWN, colour), captured, PieceType.QUEEN))
            moves.add(Move(start, target, Piece(PieceType.PAWN, colour), captured, PieceType.ROOK))
            moves.add(Move(start, target, Piece(PieceType.PAWN, colour), captured, PieceType.BISHOP))
            moves.add(Move(start, target, Piece(PieceType.PAWN, colour), captured, PieceType.KNIGHT))
        } else {
            moves.add(Move(start, target, Piece(PieceType.PAWN, colour), captured))
        }
    }

    private fun generateSlidingMoves(
        start: Square,
        colour: Colour,
        moves: MutableList<Move>,
        pieceType: PieceType,
        offsets: List<Pair<Int, Int>>
    ) {
        for ((dCol, dRow) in offsets) {
            var currentCol = start.col + dCol
            var currentRow = start.row + dRow

            while (currentCol in 0..7 && currentRow in 0..7) {
                val targetSquare = Square(currentCol, currentRow)
                val pieceAtTarget = board.getPiece(targetSquare)

                if (pieceAtTarget != null && pieceAtTarget.colour == colour) {
                    break
                }

                moves.add(
                    Move(
                        from = start,
                        to = targetSquare,
                        piece = Piece(pieceType, colour),
                        capturedPiece = pieceAtTarget
                    )
                )

                if (pieceAtTarget != null && pieceAtTarget.colour != colour) {
                    break
                }

                currentCol += dCol
                currentRow += dRow
            }
        }
    }

    private fun generateOneOffMoves(
        start: Square,
        colour: Colour,
        moves: MutableList<Move>,
        pieceType: PieceType,
        offsets: List<Pair<Int, Int>>
    ) {
        for (offset in offsets) {
            val targetCol = start.col + offset.first
            val targetRow = start.row + offset.second

            if (targetCol in 0..7 && targetRow in 0..7) {
                val targetSquare = Square(targetCol, targetRow)
                val pieceAtTarget = board.getPiece(targetSquare)

                if (pieceAtTarget == null || pieceAtTarget.colour != colour) {
                    moves.add(
                        Move(
                            from = start,
                            to = targetSquare,
                            piece = Piece(pieceType, colour),
                            capturedPiece = pieceAtTarget
                        )
                    )
                }
            }
        }
    }

    companion object {

        private val log = Log.of("com.tward.engine.game.MoveGenerator")

        val knightOffsets = listOf(
            Pair(1, 2), Pair(2, 1), Pair(-1, 2), Pair(-2, 1),
            Pair(1, -2), Pair(2, -1), Pair(-1, -2), Pair(-2, -1)
        )

        val rookOffsets = listOf(
            Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)
        )

        val bishopOffsets = listOf(
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
        )

        val queenOffsets = bishopOffsets + rookOffsets

        val kingOffsets = bishopOffsets + rookOffsets
    }
}
