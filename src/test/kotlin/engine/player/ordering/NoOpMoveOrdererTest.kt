package engine.player.ordering

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.player.ordering.NoOpMoveOrderer
import kotlin.test.Test
import kotlin.test.assertEquals

class NoOpMoveOrdererTest {

    private fun move(from: String, to: String, captured: PieceType? = null) =
        Move(
            from = Square.fromString(from),
            to = Square.fromString(to),
            piece = Piece(PieceType.PAWN, Colour.WHITE),
            capturedPiece = captured?.let { Piece(it, Colour.BLACK) }
        )

    @Test
    fun `returns the moves untouched even when a capture is present`() {
        val moves = listOf(
            move("e2", "e4"),
            move("d1", "d8", captured = PieceType.QUEEN),
            move("g1", "f3")
        )

        assertEquals(moves, NoOpMoveOrderer.order(moves, ply = 0))
    }
}
