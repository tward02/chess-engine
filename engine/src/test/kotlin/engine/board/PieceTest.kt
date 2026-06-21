package engine.board

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PieceTest {

    @Test
    fun `toFENChar returns correct uppercase for white pieces`() {
        val whitePawn = Piece(PieceType.PAWN, Colour.WHITE)
        assertEquals('P', whitePawn.toFENChar())
    }

    @Test
    fun `toFENChar returns correct lowercase for black pieces`() {
        val blackRook = Piece(PieceType.ROOK, Colour.BLACK)
        assertEquals('r', blackRook.toFENChar())
    }

    @Test
    fun `toUnicode returns correct symbols`() {
        assertEquals("♔", Piece(PieceType.KING, Colour.WHITE).toUnicode())
        assertEquals("♟", Piece(PieceType.PAWN, Colour.BLACK).toUnicode())
    }

    @ParameterizedTest(name = "Test piece resource name: {0}")
    @EnumSource(Colour::class)
    fun `resourceName returns correct format`(colour: Colour) {
        val piece = Piece(PieceType.KNIGHT, colour)
        val expectedPrefix = if (colour == Colour.WHITE) "wn" else "bn"

        assertEquals("$expectedPrefix.svg.webp", piece.resourceName())
    }

    @Test
    fun `colour opposite returns correct value`() {
        assertEquals(Colour.BLACK, Colour.WHITE.opposite())
        assertEquals(Colour.WHITE, Colour.BLACK.opposite())
    }

    @Test
    fun `pieces with same properties are equal`() {
        val p1 = Piece(PieceType.QUEEN, Colour.WHITE)
        val p2 = Piece(PieceType.QUEEN, Colour.WHITE)
        assertEquals(p1, p2)
    }
}
