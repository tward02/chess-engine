package engine.board

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.MoveGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardSpecialMovesTest {

    private fun legalMoves(board: Board) = MoveGenerator(board).generateLegalMoves()

    @Test
    fun `kingside castling moves the rook and sets the castled flag`() {

        val board = Board.fromFEN("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val before = board.toFEN()

        val castle = legalMoves(board).first { it.isCastling && it.to == Square.fromString("g1") }

        board.makeMove(castle)

        assertEquals(
            PieceType.KING,
            board.getPiece(Square.fromString("g1"))?.type
        )
        assertEquals(
            PieceType.ROOK,
            board.getPiece(Square.fromString("f1"))?.type
        )
        assertNull(board.getPiece(Square.fromString("h1")))
        assertTrue(board.whiteHasCastled)

        board.undoMove(castle)

        assertEquals(before, board.toFEN())
        assertFalse(board.whiteHasCastled)
    }

    @Test
    fun `queenside castling moves the rook`() {

        val board = Board.fromFEN("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")

        val castle = legalMoves(board).first { it.isCastling && it.to == Square.fromString("c1") }

        board.makeMove(castle)

        assertEquals(
            PieceType.KING,
            board.getPiece(Square.fromString("c1"))?.type
        )
        assertEquals(
            PieceType.ROOK,
            board.getPiece(Square.fromString("d1"))?.type
        )
        assertNull(board.getPiece(Square.fromString("a1")))
        assertTrue(board.whiteHasCastled)
    }

    @Test
    fun `promotion replaces the pawn and undo restores it`() {

        val board = Board.fromFEN("7k/4P3/8/8/8/8/8/4K3 w - - 0 1")

        val promotion = legalMoves(board)
            .first { it.from == Square.fromString("e7") && it.promotionType == PieceType.QUEEN }

        board.makeMove(promotion)

        assertEquals(
            PieceType.QUEEN,
            board.getPiece(Square.fromString("e8"))?.type
        )

        board.undoMove(promotion)

        assertEquals(
            PieceType.PAWN,
            board.getPiece(Square.fromString("e7"))?.type
        )
        assertNull(board.getPiece(Square.fromString("e8")))
    }

    @Test
    fun `pawn on the seventh rank has four promotion moves`() {

        val board = Board.fromFEN("7k/4P3/8/8/8/8/8/4K3 w - - 0 1")

        val promotions = legalMoves(board)
            .filter { it.from == Square.fromString("e7") }

        assertEquals(4, promotions.size)
        assertTrue(promotions.all { it.promotionType != null })
        assertEquals(
            setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT),
            promotions.mapNotNull { it.promotionType }.toSet()
        )
    }

    @Test
    fun `en passant capture removes the passed pawn and undo restores it`() {

        val board = Board.fromFEN("rnbqkbnr/ppp2ppp/8/4pP2/8/8/PPPP2PP/RNBQKBNR w KQkq e6 0 2")
        val before = board.toFEN()

        val enPassant = legalMoves(board)
            .first { it.from == Square.fromString("f5") && it.to == Square.fromString("e6") }

        board.makeMove(enPassant)

        assertEquals(
            Piece(PieceType.PAWN, Colour.WHITE),
            board.getPiece(Square.fromString("e6"))
        )
        assertNull(board.getPiece(Square.fromString("e5")))
        assertNull(board.getPiece(Square.fromString("f5")))

        board.undoMove(enPassant)

        assertEquals(before, board.toFEN())
    }

    @Test
    fun `capturing a rook removes the opponent's castling right`() {

        val board = Board.fromFEN("r3k3/8/8/8/8/8/8/R3K3 w Qq - 0 1")

        val capture = legalMoves(board)
            .first { it.from == Square.fromString("a1") && it.to == Square.fromString("a8") }

        board.makeMove(capture)

        assertFalse(board.blackCanCastleQueenside)
    }
}
