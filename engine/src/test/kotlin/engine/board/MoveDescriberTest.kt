package engine.board

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.MoveDescriber
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveDescriberTest {

    private fun pawn(colour: Colour) = Piece(PieceType.PAWN, colour)
    private fun piece(type: PieceType, colour: Colour = Colour.WHITE) = Piece(type, colour)

    @Test
    fun `quiet pawn move`() {
        val move = Move(Square.fromString("e2"), Square.fromString("e4"), pawn(Colour.WHITE))
        assertEquals("e2-e4", MoveDescriber.describe(move))
    }

    @Test
    fun `knight move uses N and no piece letter for pawns`() {
        val move = Move(Square.fromString("g1"), Square.fromString("f3"), piece(PieceType.KNIGHT))
        assertEquals("Ng1-f3", MoveDescriber.describe(move))
    }

    @Test
    fun `capture uses x`() {
        val move = Move(
            Square.fromString("f1"),
            Square.fromString("c4"),
            piece(PieceType.BISHOP),
            capturedPiece = pawn(Colour.BLACK)
        )
        assertEquals("Bf1xc4", MoveDescriber.describe(move))
    }

    @Test
    fun `kingside castling`() {
        val move = Move(
            Square.fromString("e1"),
            Square.fromString("g1"),
            piece(PieceType.KING),
            isCastling = true
        )
        assertEquals("O-O", MoveDescriber.describe(move))
    }

    @Test
    fun `queenside castling`() {
        val move = Move(
            Square.fromString("e1"),
            Square.fromString("c1"),
            piece(PieceType.KING),
            isCastling = true
        )
        assertEquals("O-O-O", MoveDescriber.describe(move))
    }

    @Test
    fun `promotion appends piece`() {
        val move = Move(
            Square.fromString("e7"),
            Square.fromString("e8"),
            pawn(Colour.WHITE),
            promotionType = PieceType.QUEEN
        )
        assertEquals("e7-e8=Q", MoveDescriber.describe(move))
    }

    @Test
    fun `en passant is labelled`() {
        val move = Move(
            Square.fromString("f5"),
            Square.fromString("e6"),
            pawn(Colour.WHITE),
            capturedPiece = pawn(Colour.BLACK)
        )
        assertEquals("f5xe6 e.p.", MoveDescriber.describe(move, isEnPassant = true))
    }

    @Test
    fun `check appends plus`() {
        val move = Move(Square.fromString("d1"), Square.fromString("h5"), piece(PieceType.QUEEN))
        assertEquals("Qd1-h5+", MoveDescriber.describe(move, gaveCheck = true))
    }

    @Test
    fun `checkmate appends hash and takes priority over check`() {
        val move = Move(Square.fromString("d8"), Square.fromString("h4"), piece(PieceType.QUEEN, Colour.BLACK))
        assertEquals(
            "Qd8-h4#",
            MoveDescriber.describe(move, gaveCheck = true, isCheckmate = true)
        )
    }
}
