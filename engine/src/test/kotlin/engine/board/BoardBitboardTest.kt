package engine.board

import com.tward.engine.board.Bitboards
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.board.Move
import com.tward.engine.board.PieceType
import com.tward.engine.game.ChessGame
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Board]'s bitboards must always mirror its grid, through every kind of move (captures,
 * castling, promotions, en passant), through undo, and across [Board.copy] / [Board.clearBoard].
 */
class BoardBitboardTest {

    private fun assertMirrorsGrid(board: Board, context: String = "") {
        val expected = LongArray(12)
        board.forEachPiece { col, row, piece ->
            expected[piece.colour.ordinal * 6 + piece.type.ordinal] =
                expected[piece.colour.ordinal * 6 + piece.type.ordinal] or (1L shl Bitboards.index(col, row))
        }
        var all = 0L
        for (colour in Colour.entries) {
            var side = 0L
            for (type in PieceType.entries) {
                val bits = expected[colour.ordinal * 6 + type.ordinal]
                assertEquals(bits, board.pieces(colour, type), "$colour $type $context")
                side = side or bits
            }
            assertEquals(side, board.pieces(colour), "$colour pieces $context")
            all = all or side
        }
        assertEquals(all, board.occupied, "occupied $context")
    }

    @Test
    fun `bitboards mirror the grid through random playouts and undo`() {
        val rng = Random(7)
        val starts = listOf(
            Board.getStartingBoard().toFEN(),
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        )
        for (fen in starts) repeat(10) {
            val game = ChessGame(Board.fromFEN(fen))
            assertMirrorsGrid(game.board, "at $fen")
            val played = mutableListOf<Move>()
            for (ply in 0 until 150) {
                val moves = game.getLegalMoves()
                if (moves.isEmpty() || game.getGameResult() != null) break
                val move = moves[rng.nextInt(moves.size)]
                game.makeMove(move)
                played.add(move)
                assertMirrorsGrid(game.board, "after ${move.toAlgebraic()} at ply $ply")
            }
            for (move in played.asReversed()) {
                game.undoMove(move)
                assertMirrorsGrid(game.board, "undoing ${move.toAlgebraic()}")
            }
        }
    }

    @Test
    fun `copy and clearBoard keep the bitboards in step`() {
        val board = Board.fromFEN("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        val copy = board.copy()
        assertMirrorsGrid(copy, "copy")
        assertEquals(board.occupied, copy.occupied)

        board.clearBoard()
        assertMirrorsGrid(board, "cleared")
        assertEquals(0L, board.occupied)
        assertMirrorsGrid(copy, "copy after the original was cleared")
    }

    @Test
    fun `bit index follows the board orientation`() {
        val board = Board.getStartingBoard()
        // Row 0 is rank 8: bit 0 is a8 (a black rook), bit 63 is h1 (a white rook).
        assertEquals(1L, board.pieces(Colour.BLACK, PieceType.ROOK) and 1L)
        assertEquals(1L shl 63, board.pieces(Colour.WHITE, PieceType.ROOK) and (1L shl 63))
        assertEquals(0x0000_0000_0000_FFFFL, board.pieces(Colour.BLACK))
        assertEquals(-0x1_0000_0000_0000L, board.pieces(Colour.WHITE))
        val visited = mutableListOf<Int>()
        Bitboards.forEachBit(board.pieces(Colour.WHITE, PieceType.KING)) { visited += it }
        assertEquals(listOf(Bitboards.index(4, 7)), visited)
    }
}
