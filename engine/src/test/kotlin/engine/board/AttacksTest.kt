package engine.board

import com.tward.engine.board.Attacks
import com.tward.engine.board.Bitboards
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.MoveGenerator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AttacksTest {

    @Test
    fun `magic slider attacks match ray walking for random occupancies`() {
        val rng = Random(11)
        for (sq in 0 until 64) {
            repeat(300) {
                // Sparse and dense boards both: AND-ing random longs thins them out.
                val occupied = when (it % 3) {
                    0 -> rng.nextLong()
                    1 -> rng.nextLong() and rng.nextLong()
                    else -> rng.nextLong() and rng.nextLong() and rng.nextLong()
                }
                assertEquals(Attacks.rookReference(sq, occupied), Attacks.rook(sq, occupied), "rook on $sq")
                assertEquals(Attacks.bishopReference(sq, occupied), Attacks.bishop(sq, occupied), "bishop on $sq")
            }
            assertEquals(Attacks.rookReference(sq, 0L), Attacks.rook(sq, 0L))
            assertEquals(Attacks.bishopReference(sq, -1L), Attacks.bishop(sq, -1L))
        }
    }

    @Test
    fun `leaper tables`() {
        val e4 = Bitboards.index(Square.fromString("e4"))
        assertEquals(8, java.lang.Long.bitCount(Attacks.knight(e4)))
        assertEquals(2, java.lang.Long.bitCount(Attacks.knight(Bitboards.index(Square.fromString("a1")))))
        assertEquals(8, java.lang.Long.bitCount(Attacks.king(e4)))
        assertEquals(3, java.lang.Long.bitCount(Attacks.king(Bitboards.index(Square.fromString("h8")))))
        fun bits(vararg squares: String) = squares.fold(0L) { acc, s -> acc or (1L shl Bitboards.index(Square.fromString(s))) }
        assertEquals(Attacks.pawn(Colour.WHITE, e4), bits("d5", "f5"))
        assertEquals(Attacks.pawn(Colour.BLACK, e4), bits("d3", "f3"))
        assertEquals(Attacks.pawn(Colour.WHITE, Bitboards.index(Square.fromString("a2"))), bits("b3"))
    }

    @Test
    fun `between covers only aligned squares`() {
        fun sq(s: String) = Bitboards.index(Square.fromString(s))
        fun bits(vararg squares: String) = squares.fold(0L) { acc, s -> acc or (1L shl sq(s)) }
        assertEquals(bits("b1", "c1", "d1"), Attacks.between(sq("a1"), sq("e1")))
        assertEquals(bits("b2", "c3"), Attacks.between(sq("d4"), sq("a1")))
        assertEquals(bits("e2", "e3"), Attacks.between(sq("e4"), sq("e1")))
        assertEquals(0L, Attacks.between(sq("a1"), sq("b3")))
        assertEquals(0L, Attacks.between(sq("a1"), sq("b2")))
    }

    @Test
    fun `isSquareAttacked agrees with a grid walk through random playouts`() {
        val rng = Random(3)
        val starts = listOf(
            Board.getStartingBoard().toFEN(),
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        )
        for (fen in starts) repeat(8) {
            val game = ChessGame(Board.fromFEN(fen))
            for (ply in 0 until 120) {
                val generator = MoveGenerator(game.board)
                for (row in 0..7) for (col in 0..7) for (colour in Colour.entries) {
                    assertEquals(
                        referenceAttacked(game.board, col, row, colour),
                        generator.isSquareAttacked(Square(col, row), colour),
                        "${Square(col, row)} by $colour in ${game.board.toFEN()}"
                    )
                }
                val moves: List<Move> = game.getLegalMoves()
                if (moves.isEmpty() || game.getGameResult() != null) break
                game.makeMove(moves[rng.nextInt(moves.size)])
            }
        }
    }

    /** The pre-bitboard implementation: walk the grid outward from the target square. */
    private fun referenceAttacked(board: Board, col: Int, row: Int, attacker: Colour): Boolean {
        fun isAt(c: Int, r: Int, vararg types: PieceType): Boolean {
            if (c !in 0..7 || r !in 0..7) return false
            val piece = board.getPiece(c, r) ?: return false
            return piece.colour == attacker && piece.type in types
        }
        val knight = listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1)
        if (knight.any { (dc, dr) -> isAt(col + dc, row + dr, PieceType.KNIGHT) }) return true
        val king = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        if (king.any { (dc, dr) -> isAt(col + dc, row + dr, PieceType.KING) }) return true
        val pawnRow = if (attacker == Colour.WHITE) row + 1 else row - 1
        if (isAt(col - 1, pawnRow, PieceType.PAWN) || isAt(col + 1, pawnRow, PieceType.PAWN)) return true
        for ((dc, dr) in king) {
            val straight = dc == 0 || dr == 0
            var c = col + dc
            var r = row + dr
            while (c in 0..7 && r in 0..7) {
                val piece = board.getPiece(c, r)
                if (piece != null) {
                    if (piece.colour == attacker &&
                        (piece.type == PieceType.QUEEN || piece.type == if (straight) PieceType.ROOK else PieceType.BISHOP)
                    ) return true
                    break
                }
                c += dc
                r += dr
            }
        }
        return false
    }
}
