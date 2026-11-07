package com.tward.engine.game

import com.tward.engine.board.Attacks
import com.tward.engine.board.Bitboards
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.logging.Log

/**
 * Legal move generation on the board's bitboards. Moves come out in a fixed order — pieces by
 * square index, each piece's directions in [Attacks.DIRECTIONS] / offset-list order, rays nearest
 * first — which move ordering relies on to break ties, so search results depend on it.
 */
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
        val occupied = board.occupied

        for (move in pseudoLegalMoves) {
            if (move.isCastling) {
                if (!isCastlingPathSafe(move, enemyColour)) continue
            } else if (move.piece?.type == PieceType.KING) {
                // The king's destination must be safe with the king lifted off its square, so a
                // slider can't hide behind it; a piece captured on the destination attacks nothing there.
                val from = Bitboards.index(move.from)
                if (!isAttacked(Bitboards.index(move.to), enemyColour, occupied xor (1L shl from))) {
                    legalMoves.add(move)
                }
                continue
            } else if (!inCheck &&
                (pinned ushr Bitboards.index(move.from)) and 1L == 0L &&
                !isEnPassantCapture(move)
            ) {
                legalMoves.add(move)
                continue
            }

            // The probe restores the board exactly, so its board listener (an NNUE accumulator) has
            // nothing to track; pausing it skips two wasted updates per verified move.
            val listener = board.listener
            board.listener = null
            board.makeMove(move)

            if (!isSquareAttacked(board.findKing(myColour), enemyColour)) {
                legalMoves.add(move)
            }

            board.undoMove(move)
            board.listener = listener
        }

        return legalMoves
    }

    // An en-passant capture is the one capture whose destination square is empty.
    private fun isEnPassantCapture(move: Move): Boolean =
        move.piece?.type == PieceType.PAWN && move.capturedPiece != null && board.getPiece(move.to) == null

    /**
     * Bitmask (square index `row * 8 + col`) of [colour]'s pieces pinned to their king: an enemy
     * slider that would see the king through enemy-free rays pins the one friendly piece between them.
     */
    private fun pinnedMask(kingSquare: Square, colour: Colour): Long {
        val king = Bitboards.index(kingSquare)
        val enemy = colour.opposite()
        val enemies = board.pieces(enemy)
        val queens = board.pieces(enemy, PieceType.QUEEN)
        // Rays through friendly pieces (enemy pieces still block): candidate pinners.
        val snipers = (Attacks.rook(king, enemies) and (board.pieces(enemy, PieceType.ROOK) or queens)) or
                (Attacks.bishop(king, enemies) and (board.pieces(enemy, PieceType.BISHOP) or queens))
        val occupied = board.occupied
        var mask = 0L
        Bitboards.forEachBit(snipers) { sniper ->
            val blockers = Attacks.between(king, sniper) and occupied
            if (blockers != 0L && blockers and (blockers - 1) == 0L) mask = mask or blockers
        }
        return mask
    }

    fun generatePseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        val colour = board.activeColour
        val own = board.pieces(colour)
        val enemy = board.pieces(colour.opposite())

        Bitboards.forEachBit(own) { from ->
            val piece = board.getPiece(Bitboards.col(from), Bitboards.row(from))!!
            val mover = Piece.of(piece.type, colour)
            when (piece.type) {
                PieceType.KNIGHT -> addSteps(from, Attacks.knightTargets(from), mover, own, moves)
                PieceType.BISHOP -> addRays(from, 0, 4, mover, own, enemy, moves)
                PieceType.ROOK -> addRays(from, 4, 8, mover, own, enemy, moves)
                PieceType.QUEEN -> addRays(from, 0, 8, mover, own, enemy, moves)
                PieceType.KING -> {
                    addSteps(from, Attacks.kingTargets(from), mover, own, moves)
                    addCastlingMoves(colour, moves)
                }

                PieceType.PAWN -> addPawnMoves(from, colour, moves)
            }
        }

        return moves
    }

    private fun addSteps(from: Int, targets: IntArray, mover: Piece, own: Long, moves: MutableList<Move>) {
        val fromSquare = Square.of(from)
        for (to in targets) {
            if ((own ushr to) and 1L != 0L) continue
            moves.add(Move(fromSquare, Square.of(to), mover, board.getPiece(Bitboards.col(to), Bitboards.row(to))))
        }
    }

    private fun addRays(
        from: Int,
        firstDirection: Int,
        endDirection: Int,
        mover: Piece,
        own: Long,
        enemy: Long,
        moves: MutableList<Move>
    ) {
        val fromSquare = Square.of(from)
        for (direction in firstDirection until endDirection) {
            for (to in Attacks.ray(direction, from)) {
                val bit = 1L shl to
                if (own and bit != 0L) break
                if (enemy and bit != 0L) {
                    moves.add(Move(fromSquare, Square.of(to), mover, board.getPiece(Bitboards.col(to), Bitboards.row(to))))
                    break
                }
                moves.add(Move(fromSquare, Square.of(to), mover))
            }
        }
    }

    private fun addPawnMoves(from: Int, colour: Colour, moves: MutableList<Move>) {
        val col = Bitboards.col(from)
        val row = Bitboards.row(from)
        val direction = if (colour == Colour.WHITE) -1 else 1
        val startRank = if (colour == Colour.WHITE) 6 else 1
        val promotionRank = if (colour == Colour.WHITE) 0 else 7
        val pawn = Piece.of(PieceType.PAWN, colour)
        val fromSquare = Square.of(from)
        val occupied = board.occupied

        val forwardRow = row + direction
        if (forwardRow !in 0..7) return

        val one = Bitboards.index(col, forwardRow)
        if ((occupied ushr one) and 1L == 0L) {
            addPawnMove(fromSquare, one, pawn, null, promotionRank, moves)
            if (row == startRank) {
                val two = Bitboards.index(col, row + 2 * direction)
                if ((occupied ushr two) and 1L == 0L) moves.add(Move(fromSquare, Square.of(two), pawn))
            }
        }

        val enPassant = board.enPassantTarget
        for (captureCol in col - 1..col + 1 step 2) {
            if (captureCol !in 0..7) continue
            val target = board.getPiece(captureCol, forwardRow)
            if (target != null && target.colour != colour) {
                addPawnMove(fromSquare, Bitboards.index(captureCol, forwardRow), pawn, target, promotionRank, moves)
            } else if (enPassant != null && enPassant.col == captureCol && enPassant.row == forwardRow) {
                moves.add(
                    Move(fromSquare, Square.of(Bitboards.index(captureCol, forwardRow)), pawn, board.getPiece(captureCol, row))
                )
            }
        }
    }

    private fun addPawnMove(
        from: Square,
        to: Int,
        pawn: Piece,
        captured: Piece?,
        promotionRank: Int,
        moves: MutableList<Move>
    ) {
        val target = Square.of(to)
        if (target.row == promotionRank) {
            moves.add(Move(from, target, pawn, captured, PieceType.QUEEN))
            moves.add(Move(from, target, pawn, captured, PieceType.ROOK))
            moves.add(Move(from, target, pawn, captured, PieceType.BISHOP))
            moves.add(Move(from, target, pawn, captured, PieceType.KNIGHT))
        } else {
            moves.add(Move(from, target, pawn, captured))
        }
    }

    /** Castling moves whose rights hold and whose squares between king and rook are empty; safety is checked later. */
    private fun addCastlingMoves(colour: Colour, moves: MutableList<Move>) {
        val row = if (colour == Colour.WHITE) 7 else 0
        val kingSide = if (colour == Colour.WHITE) board.whiteCanCastleKingside else board.blackCanCastleKingside
        val queenSide = if (colour == Colour.WHITE) board.whiteCanCastleQueenside else board.blackCanCastleQueenside
        if (!kingSide && !queenSide) return

        val rook = Piece.of(PieceType.ROOK, colour)
        val king = Piece.of(PieceType.KING, colour)
        val kingStart = Square.of(Bitboards.index(4, row))
        // Bits of columns 5-6 and 1-3 on the back rank (row * 8 shifts a row-0 mask down to it).
        val rank = board.occupied ushr (row * 8)

        if (kingSide && board.getPiece(7, row) == rook && rank and 0x60L == 0L) {
            moves.add(Move(kingStart, Square.of(Bitboards.index(6, row)), king, isCastling = true))
        }
        if (queenSide && board.getPiece(0, row) == rook && rank and 0x0EL == 0L) {
            moves.add(Move(kingStart, Square.of(Bitboards.index(2, row)), king, isCastling = true))
        }
    }

    fun isSquareAttacked(targetSquare: Square, attackerColour: Colour): Boolean =
        isAttacked(Bitboards.index(targetSquare), attackerColour, board.occupied)

    /** Whether any [attackerColour] piece attacks square index [sq] (see [Bitboards.index]). */
    fun isSquareAttacked(sq: Int, attackerColour: Colour): Boolean = isAttacked(sq, attackerColour, board.occupied)

    /** [isSquareAttacked] with slider rays blocked by [occupied] instead of the board's occupancy. */
    private fun isAttacked(sq: Int, attackerColour: Colour, occupied: Long): Boolean {
        val b = board
        if (Attacks.knight(sq) and b.pieces(attackerColour, PieceType.KNIGHT) != 0L) return true
        // A pawn attacks sq exactly when an opposing pawn on sq would attack the pawn's square.
        if (Attacks.pawn(attackerColour.opposite(), sq) and b.pieces(attackerColour, PieceType.PAWN) != 0L) return true
        if (Attacks.king(sq) and b.pieces(attackerColour, PieceType.KING) != 0L) return true
        val queens = b.pieces(attackerColour, PieceType.QUEEN)
        if (Attacks.rook(sq, occupied) and (b.pieces(attackerColour, PieceType.ROOK) or queens) != 0L) return true
        return Attacks.bishop(sq, occupied) and (b.pieces(attackerColour, PieceType.BISHOP) or queens) != 0L
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

    private fun isCastlingPathSafe(move: Move, enemyColour: Colour): Boolean {
        val row = move.from.row
        if (isSquareAttacked(move.from, enemyColour)) return false
        val passCol = if (move.to.col == 6) 5 else 3
        return !isSquareAttacked(Bitboards.index(passCol, row), enemyColour)
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
