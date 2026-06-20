package engine.player.bot

import com.tward.engine.board.Board
import com.tward.engine.player.bot.Zobrist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ZobristTest {

    private fun board(fen: String) = Board.fromFEN(fen)

    @Test
    fun `the same position always hashes to the same value`() {
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1"
        assertEquals(Zobrist.hash(board(fen)), Zobrist.hash(board(fen)))
    }

    @Test
    fun `different positions hash differently`() {
        val start = board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val afterE4 = board("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        assertNotEquals(Zobrist.hash(start), Zobrist.hash(afterE4))
    }

    @Test
    fun `side to move changes the hash`() {
        val white = board("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val black = board("4k3/8/8/8/8/8/8/4K3 b - - 0 1")
        assertNotEquals(Zobrist.hash(white), Zobrist.hash(black))
    }

    @Test
    fun `castling rights change the hash`() {
        val withRights = board("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val withoutRights = board("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1")
        assertNotEquals(Zobrist.hash(withRights), Zobrist.hash(withoutRights))
    }

    @Test
    fun `a move and its undo round-trip to the same hash`() {
        val board = board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val before = Zobrist.hash(board)
        val move = com.tward.engine.game.MoveGenerator(board).generateLegalMoves().first()
        board.makeMove(move)
        board.undoMove(move)
        assertEquals(before, Zobrist.hash(board))
    }
}
