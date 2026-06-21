package engine.player.ordering

import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.player.ordering.KillerHistoryMoveOrderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KillerHistoryMoveOrdererTest {

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
    fun `with no learning it behaves like mvv-lva - captures before quiets`() {

        val orderer = KillerHistoryMoveOrderer()

        val q1 = quiet("g1", "f3")
        val q2 = quiet("b1", "c3")
        val cap = capture("d1", "d7")

        val ordered = orderer.order(listOf(q1, q2, cap), ply = 0)

        assertEquals(cap, ordered.first())
        // Quiet moves keep their original relative order when nothing has been learned
        assertEquals(listOf(cap, q1, q2), ordered)
    }

    @Test
    fun `a killer quiet move is tried before other quiets`() {

        val orderer = KillerHistoryMoveOrderer()

        val killer = quiet("b1", "c3")
        val other = quiet("g1", "f3")

        // The killer caused a cutoff at ply 2
        orderer.onBetaCutoff(killer, ply = 2, depth = 3)

        val ordered = orderer.order(listOf(other, killer), ply = 2)

        assertEquals(killer, ordered.first())
    }

    @Test
    fun `killer status is scoped to its ply`() {

        val orderer = KillerHistoryMoveOrderer()

        val killer = quiet("b1", "c3")
        val other = quiet("g1", "f3")

        // Give 'other' a strong history so it wins among quiets on history alone (at ply 7, so it is
        // not itself a killer at the plies we query)
        repeat(20) { orderer.onBetaCutoff(other, ply = 7, depth = 5) }

        // 'killer' cut off once, at ply 2
        orderer.onBetaCutoff(killer, ply = 2, depth = 1)

        // At ply 2 the killer slot lifts 'killer' above 'other' despite other's larger history
        assertEquals(killer, orderer.order(listOf(other, killer), ply = 2).first())

        // At ply 5 'killer' is no longer a killer, so 'other' (more history) is tried first
        assertEquals(other, orderer.order(listOf(other, killer), ply = 5).first())
    }

    @Test
    fun `captures still beat killers`() {

        val orderer = KillerHistoryMoveOrderer()

        val killer = quiet("b1", "c3")
        orderer.onBetaCutoff(killer, ply = 0, depth = 3)

        val cap = capture("d1", "d7")

        val ordered = orderer.order(listOf(killer, cap), ply = 0)

        assertEquals(cap, ordered.first())
    }

    @Test
    fun `the most recent two cutoffs are kept as killers`() {

        val orderer = KillerHistoryMoveOrderer()

        val first = quiet("a1", "a2")
        val second = quiet("b1", "b2")
        val third = quiet("c1", "c2")

        orderer.onBetaCutoff(first, ply = 1, depth = 2)
        orderer.onBetaCutoff(second, ply = 1, depth = 2)
        orderer.onBetaCutoff(third, ply = 1, depth = 2)

        // third and second are the two most recent killers; first has been pushed out
        val ordered = orderer.order(listOf(first, second, third), ply = 1)

        assertEquals(third, ordered[0])
        assertEquals(second, ordered[1])
        assertEquals(first, ordered[2])
    }

    @Test
    fun `history orders quiet moves that have caused cutoffs before untouched ones`() {

        val orderer = KillerHistoryMoveOrderer()

        val good = quiet("g1", "f3")
        val plain = quiet("b1", "c3")

        // 'good' cut off somewhere deep (high depth -> big history bump), but at a different ply so
        // it isn't a killer for the ply we order at
        orderer.onBetaCutoff(good, ply = 4, depth = 5)

        val ordered = orderer.order(listOf(plain, good), ply = 0)

        assertEquals(good, ordered.first())
    }

    @Test
    fun `history distinguishes colours on the same squares`() {

        val orderer = KillerHistoryMoveOrderer()

        val whiteMove = quiet("g1", "f3", Colour.WHITE)
        val blackMove = quiet("g1", "f3", Colour.BLACK)

        orderer.onBetaCutoff(whiteMove, ply = 9, depth = 4)

        // Only the white move earned history; the black move from/to the same squares stays plain
        assertEquals(whiteMove, orderer.order(listOf(blackMove, whiteMove), ply = 0).first())
    }

    @Test
    fun `captures are never recorded as killers`() {

        val orderer = KillerHistoryMoveOrderer()

        val cap = capture("d1", "d7")
        orderer.onBetaCutoff(cap, ply = 0, depth = 3)

        val quietA = quiet("g1", "f3")
        val quietB = quiet("b1", "c3")

        // The capture did not become a killer, so the quiets keep their natural order
        assertEquals(listOf(quietA, quietB), orderer.order(listOf(quietA, quietB), ply = 0))
    }

    @Test
    fun `reset clears killers and history`() {

        val orderer = KillerHistoryMoveOrderer()

        val killer = quiet("b1", "c3")
        val other = quiet("g1", "f3")

        orderer.onBetaCutoff(killer, ply = 0, depth = 3)
        orderer.reset()

        // After reset nothing is remembered, so original order is preserved
        assertEquals(listOf(other, killer), orderer.order(listOf(other, killer), ply = 0))
    }

    @Test
    fun `order preserves every move exactly once`() {

        val orderer = KillerHistoryMoveOrderer()
        val moves = listOf(
            quiet("g1", "f3"),
            capture("d1", "d7"),
            quiet("b1", "c3"),
            capture("e1", "e7", victim = PieceType.ROOK)
        )

        val ordered = orderer.order(moves, ply = 0)

        assertEquals(moves.size, ordered.size)
        assertEquals(moves.toSet(), ordered.toSet())
        assertTrue(ordered.first().capturedPiece != null)
    }
}
