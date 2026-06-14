package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.player.evaluator.PieceSquareTables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PieceSquareTablesTest {

    private fun board(fen: String) = Board.fromFEN(fen)

    // ----- gamePhase -----

    @Test
    fun `starting position is full phase`() {
        assertEquals(PieceSquareTables.MAX_PHASE, PieceSquareTables.gamePhase(Board.getStartingBoard()))
    }

    @Test
    fun `king and pawn only is zero phase`() {
        assertEquals(0, PieceSquareTables.gamePhase(board("4k3/4p3/8/8/8/8/4P3/4K3 w - - 0 1")))
    }

    @Test
    fun `two queens count eight phase`() {
        // queen weight 4, one each side
        assertEquals(8, PieceSquareTables.gamePhase(board("3qk3/8/8/8/8/8/8/3QK3 w - - 0 1")))
    }

    @Test
    fun `phase decreases as material is removed`() {
        val full = PieceSquareTables.gamePhase(Board.getStartingBoard())
        val fewer = PieceSquareTables.gamePhase(board("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1")) // 4 rooks = 8

        assertEquals(8, fewer)
        assertTrue(fewer < full)
    }

    // ----- locationValue blending -----

    @Test
    fun `at full phase the location value equals the middlegame table`() {
        // The middlegame king table penalises a central king
        val central = PieceSquareTables.locationValue(
            PieceType.KING, Colour.WHITE, Square.fromString("e4"), PieceSquareTables.MAX_PHASE
        )
        assertEquals(-40, central)
    }

    @Test
    fun `at zero phase the location value equals the endgame table`() {
        // The endgame king table rewards a central king
        val central = PieceSquareTables.locationValue(
            PieceType.KING, Colour.WHITE, Square.fromString("e4"), 0
        )
        assertEquals(40, central)
    }

    @Test
    fun `half phase is the midpoint of the two tables`() {
        val half = PieceSquareTables.locationValue(
            PieceType.KING, Colour.WHITE, Square.fromString("e4"), PieceSquareTables.MAX_PHASE / 2
        )
        // (-40 + 40) / 2 = 0
        assertEquals(0, half)
    }

    @Test
    fun `central king value rises as the game heads into the endgame`() {
        val opening = PieceSquareTables.locationValue(PieceType.KING, Colour.WHITE, Square.fromString("e4"), 24)
        val middle = PieceSquareTables.locationValue(PieceType.KING, Colour.WHITE, Square.fromString("e4"), 12)
        val endgame = PieceSquareTables.locationValue(PieceType.KING, Colour.WHITE, Square.fromString("e4"), 0)

        assertTrue(opening < middle)
        assertTrue(middle < endgame)
    }

    @Test
    fun `advanced pawns are worth more in the endgame`() {
        val pawnOnSeventh = Square.fromString("e7")

        val opening = PieceSquareTables.locationValue(PieceType.PAWN, Colour.WHITE, pawnOnSeventh, 24)
        val endgame = PieceSquareTables.locationValue(PieceType.PAWN, Colour.WHITE, pawnOnSeventh, 0)

        assertTrue(endgame > opening)
    }

    @Test
    fun `black is mirrored vertically`() {
        // White king on e1 must score the same as a black king on e8 (the mirror square)
        val white = PieceSquareTables.locationValue(PieceType.KING, Colour.WHITE, Square.fromString("e1"), 6)
        val black = PieceSquareTables.locationValue(PieceType.KING, Colour.BLACK, Square.fromString("e8"), 6)

        assertEquals(white, black)
    }
}
