package engine.game

import com.tward.engine.board.Board
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.MoveGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveGeneratorTest {

    // Helper to setup and generate
    private fun getLegalMoves(fen: String): List<Move> {
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)
        return generator.generateLegalMoves()
    }

    @Test
    fun `test starting position has 20 legal moves`() {
        val moves = getLegalMoves("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertEquals(20, moves.size)
    }

    @Test
    fun `test en passant is detected as a legal move`() {
        val fen = "rnbqkbnr/ppp2ppp/8/4pP2/8/8/PPPP2PP/RNBQKBNR w KQkq e6 0 2"
        val moves = getLegalMoves(fen)

        val hasEnPassant = moves.any { it.from == Square(5, 3) && it.to == Square(4, 2) }
        assertTrue(hasEnPassant, "Pawn should be able to capture en passant")
    }

    @Test
    fun `test king can move freely on empty board`() {
        val fen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
        val moves = getLegalMoves(fen)

        val expectedMoves = listOf(
            Square(3, 7), Square(5, 7),
            Square(3, 6), Square(4, 6), Square(5, 6)
        )

        for (target in expectedMoves) {
            val found = moves.any { it.to == target && it.piece?.type == PieceType.KING }
            assertTrue(found, "King should be able to move to $target")
        }

        assertEquals(5, moves.size, "King should have exactly 5 legal moves in the corner/edge")
    }

    @Test
    fun `test king cannot move into check or stay in check`() {
        val fen = "4k3/8/8/8/8/8/4r3/4K3 w - - 0 1"
        val moves = getLegalMoves(fen)

        val canCaptureRook = moves.any { it.to == Square(4, 6) }
        assertTrue(canCaptureRook, "King should be able to capture the Rook on e2")

        val canMoveToF2 = moves.any { it.to == Square(5, 6) }
        assertFalse(canMoveToF2, "King should not be able to move to f2 (attacked by Rook on e2)")

        val canMoveToD2 = moves.any { it.to == Square(3, 6) }
        assertFalse(canMoveToD2, "King should not be able to move to d2 (attacked by Rook on e2)")
    }

    @Test
    fun `both castling moves are generated when available`() {
        val moves = getLegalMoves("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")

        assertTrue(
            moves.any { it.isCastling && it.to == Square(6, 7) },
            "White should be able to castle kingside"
        )
        assertTrue(
            moves.any { it.isCastling && it.to == Square(2, 7) },
            "White should be able to castle queenside"
        )
    }

    @Test
    fun `cannot castle when a square between king and rook is occupied`() {
        // A bishop on f1 blocks the kingside castle
        val moves = getLegalMoves("r3k2r/8/8/8/8/8/8/R3KB1R w KQkq - 0 1")

        assertFalse(
            moves.any { it.isCastling && it.to == Square(6, 7) },
            "White cannot castle kingside through the bishop on f1"
        )
    }

    @Test
    fun `cannot castle out of check`() {
        // Black rook on e8 checks the white king down the e-file
        val moves = getLegalMoves("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1")

        assertFalse(
            moves.any { it.isCastling },
            "White cannot castle while in check"
        )
    }

    @Test
    fun `cannot castle through an attacked square`() {
        // Black rook on f8 attacks f1, the square the king passes over when castling kingside
        val moves = getLegalMoves("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")

        assertFalse(
            moves.any { it.isCastling && it.to == Square(6, 7) },
            "White cannot castle kingside through the attacked f1 square"
        )
    }
}
