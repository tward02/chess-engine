package engine.player.ordering

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.player.ordering.MvvLvaMoveOrderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MvvLvaMoveOrdererTest {

    private val orderer = MvvLvaMoveOrderer()

    // Colour is irrelevant to ordering (only material values matter), so default the mover to white
    private fun move(
        from: String,
        to: String,
        mover: PieceType = PieceType.PAWN,
        captured: PieceType? = null,
        promotion: PieceType? = null
    ): Move {
        return Move(
            from = Square.fromString(from),
            to = Square.fromString(to),
            piece = Piece(mover, Colour.WHITE),
            capturedPiece = captured?.let { Piece(it, Colour.BLACK) },
            promotionType = promotion
        )
    }

    // ----- scoreOf: exact values -----

    @Test
    fun `quiet move scores zero`() {
        assertEquals(0, orderer.scoreOf(move("e2", "e4")))
    }

    @Test
    fun `pawn takes queen scores the most among captures`() {
        // CAPTURE_BONUS(1000) + victim 9 * 10 - aggressor 1 = 1089
        assertEquals(1089, orderer.scoreOf(move("d1", "d8", mover = PieceType.PAWN, captured = PieceType.QUEEN)))
    }

    @Test
    fun `queen takes pawn scores the least among captures`() {
        // CAPTURE_BONUS(1000) + victim 1 * 10 - aggressor 9 = 1001
        assertEquals(1001, orderer.scoreOf(move("d1", "d2", mover = PieceType.QUEEN, captured = PieceType.PAWN)))
    }

    @Test
    fun `queen promotion scores above any capture`() {
        // PROMOTION_BONUS(2000) + queen 9 = 2009
        assertEquals(2009, orderer.scoreOf(move("e7", "e8", promotion = PieceType.QUEEN)))
    }

    @Test
    fun `capture promotion combines both bonuses`() {
        // capture: 1000 + 5*10 - 1 = 1049 ; promotion: 2000 + 9 = 2009 ; total 3058
        val score = orderer.scoreOf(
            move("d7", "e8", mover = PieceType.PAWN, captured = PieceType.ROOK, promotion = PieceType.QUEEN)
        )
        assertEquals(3058, score)
    }

    // ----- scoreOf: relative ordering -----

    @Test
    fun `bigger victims score higher`() {
        val takeQueen = orderer.scoreOf(move("a1", "a8", mover = PieceType.PAWN, captured = PieceType.QUEEN))
        val takeRook = orderer.scoreOf(move("a1", "a8", mover = PieceType.PAWN, captured = PieceType.ROOK))
        val takeKnight = orderer.scoreOf(move("a1", "a8", mover = PieceType.PAWN, captured = PieceType.KNIGHT))

        assertTrue(takeQueen > takeRook)
        assertTrue(takeRook > takeKnight)
    }

    @Test
    fun `for the same victim a cheaper attacker scores higher`() {
        val pawnTakesQueen = orderer.scoreOf(move("d1", "d8", mover = PieceType.PAWN, captured = PieceType.QUEEN))
        val knightTakesQueen = orderer.scoreOf(move("d1", "d8", mover = PieceType.KNIGHT, captured = PieceType.QUEEN))
        val queenTakesQueen = orderer.scoreOf(move("d1", "d8", mover = PieceType.QUEEN, captured = PieceType.QUEEN))

        assertTrue(pawnTakesQueen > knightTakesQueen)
        assertTrue(knightTakesQueen > queenTakesQueen)
    }

    @Test
    fun `queen promotion outranks under-promotions`() {
        val queen = orderer.scoreOf(move("e7", "e8", promotion = PieceType.QUEEN))
        val rook = orderer.scoreOf(move("e7", "e8", promotion = PieceType.ROOK))
        val knight = orderer.scoreOf(move("e7", "e8", promotion = PieceType.KNIGHT))

        assertTrue(queen > rook)
        assertTrue(rook > knight)
    }

    @Test
    fun `any capture scores above any quiet move`() {
        val smallestCapture = orderer.scoreOf(move("d1", "d2", mover = PieceType.QUEEN, captured = PieceType.PAWN))
        val quiet = orderer.scoreOf(move("e2", "e4"))

        assertTrue(smallestCapture > quiet)
    }

    // ----- order() -----

    @Test
    fun `order sorts moves best first`() {
        val quiet = move("e2", "e4")
        val pawnTakesPawn = move("b2", "c3", mover = PieceType.PAWN, captured = PieceType.PAWN)
        val pawnTakesQueen = move("d1", "d8", mover = PieceType.PAWN, captured = PieceType.QUEEN)
        val promotion = move("a7", "a8", promotion = PieceType.QUEEN)

        val ordered = orderer.order(listOf(quiet, pawnTakesPawn, pawnTakesQueen, promotion), ply = 0)

        assertEquals(listOf(promotion, pawnTakesQueen, pawnTakesPawn, quiet), ordered)
    }

    @Test
    fun `order preserves every move exactly once`() {
        val moves = listOf(
            move("e2", "e4"),
            move("d1", "d8", mover = PieceType.PAWN, captured = PieceType.QUEEN),
            move("g1", "f3"),
            move("a7", "a8", promotion = PieceType.QUEEN)
        )

        val ordered = orderer.order(moves, ply = 0)

        assertEquals(moves.size, ordered.size)
        assertEquals(moves.toSet(), ordered.toSet())
    }

    @Test
    fun `quiet moves keep their original relative order`() {
        val first = move("e2", "e4")
        val second = move("d2", "d4")
        val third = move("g1", "f3")

        val ordered = orderer.order(listOf(first, second, third), ply = 0)

        // All score 0, so the stable sort must leave them untouched
        assertEquals(listOf(first, second, third), ordered)
    }

    @Test
    fun `order handles empty and single-element lists`() {
        assertEquals(emptyList(), orderer.order(emptyList(), ply = 0))

        val single = listOf(move("e2", "e4"))
        assertEquals(single, orderer.order(single, ply = 0))
    }
}
