package engine.player.ordering

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.player.ordering.CounterMoveOrderer
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterMoveOrdererTest {

    private fun quiet(from: String, to: String, colour: Colour = Colour.WHITE) =
        Move(Square.fromString(from), Square.fromString(to), Piece(PieceType.KNIGHT, colour))

    private fun capture(from: String, to: String, victim: PieceType = PieceType.PAWN) =
        Move(
            Square.fromString(from),
            Square.fromString(to),
            Piece(PieceType.QUEEN, Colour.WHITE),
            capturedPiece = Piece(victim, Colour.BLACK)
        )

    @Test
    fun `a recorded countermove is tried before plain quiets when its trigger repeats`() {
        val orderer = CounterMoveOrderer()

        val trigger = quiet("e7", "e5", Colour.BLACK)
        val refutation = quiet("g1", "f3")
        val plain = quiet("b1", "c3")

        // 'refutation' cut off in answer to 'trigger' (at ply 9, so it earns no killer status at
        // the ply we later order)
        orderer.previousMove = trigger
        orderer.onBetaCutoff(refutation, ply = 9, depth = 1)

        // Elsewhere in the tree the opponent plays 'trigger' again
        orderer.previousMove = trigger
        assertEquals(refutation, orderer.order(listOf(plain, refutation), ply = 3).first())
    }

    @Test
    fun `the countermove band is inactive for a different previous move`() {
        val orderer = CounterMoveOrderer()

        val trigger = quiet("e7", "e5", Colour.BLACK)
        val refutation = quiet("g1", "f3")
        val plain = quiet("b1", "c3")

        orderer.previousMove = trigger
        orderer.onBetaCutoff(refutation, ply = 9, depth = 1)

        // A different opponent move: no countermove match. Only history separates the quiets, and
        // 'refutation' earned depth²=1 history, so give 'plain' more to prove counters don't leak.
        repeat(3) { orderer.onBetaCutoff(plain, ply = 8, depth = 1) }
        orderer.previousMove = quiet("d7", "d5", Colour.BLACK)
        assertEquals(plain, orderer.order(listOf(refutation, plain), ply = 3).first())
    }

    @Test
    fun `with no previous move ordering degrades to killers and history`() {
        val orderer = CounterMoveOrderer()

        val killer = quiet("b1", "c3")
        val other = quiet("g1", "f3")

        orderer.onBetaCutoff(killer, ply = 2, depth = 3)

        orderer.previousMove = null
        assertEquals(killer, orderer.order(listOf(other, killer), ply = 2).first())
    }

    @Test
    fun `killers rank above a countermove`() {
        val orderer = CounterMoveOrderer()

        val trigger = quiet("e7", "e5", Colour.BLACK)
        val counter = quiet("g1", "f3")
        val killer = quiet("b1", "c3")

        orderer.previousMove = trigger
        orderer.onBetaCutoff(counter, ply = 9, depth = 1)
        // Killer cutoff recorded with no previous move so counters[trigger] stays pointing at 'counter'
        orderer.previousMove = null
        orderer.onBetaCutoff(killer, ply = 3, depth = 1)

        orderer.previousMove = trigger
        assertEquals(killer, orderer.order(listOf(counter, killer), ply = 3).first())
    }

    @Test
    fun `captures rank above everything`() {
        val orderer = CounterMoveOrderer()

        val trigger = quiet("e7", "e5", Colour.BLACK)
        val counter = quiet("g1", "f3")
        val cap = capture("d1", "d7")

        orderer.previousMove = trigger
        orderer.onBetaCutoff(counter, ply = 9, depth = 1)

        orderer.previousMove = trigger
        assertEquals(cap, orderer.order(listOf(counter, cap), ply = 3).first())
    }

    @Test
    fun `reset clears counters killers history and the previous move`() {
        val orderer = CounterMoveOrderer()

        val trigger = quiet("e7", "e5", Colour.BLACK)
        val counter = quiet("g1", "f3")
        val plain = quiet("b1", "c3")

        orderer.previousMove = trigger
        orderer.onBetaCutoff(counter, ply = 9, depth = 5)
        orderer.reset()

        orderer.previousMove = trigger
        // Nothing is remembered, so original order is preserved
        assertEquals(listOf(plain, counter), orderer.order(listOf(plain, counter), ply = 3))
    }

    @Test
    fun `order preserves every move exactly once`() {
        val orderer = CounterMoveOrderer()
        val moves = listOf(
            quiet("g1", "f3"),
            capture("d1", "d7"),
            quiet("b1", "c3"),
            capture("e1", "e7", victim = PieceType.ROOK)
        )

        val ordered = orderer.order(moves, ply = 0)

        assertEquals(moves.size, ordered.size)
        assertEquals(moves.toSet(), ordered.toSet())
    }
}
