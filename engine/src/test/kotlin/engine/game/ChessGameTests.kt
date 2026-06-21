package engine.game

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChessGameTests {

    @Test
    fun `K vs K is insufficient material`() {

        val game =
            game("8/8/8/8/8/8/4k3/4K3 w - - 0 1")

        assertTrue(game.isInsufficientMaterial())

        assertEquals(
            GameResult.DRAW_INSUFFICIENT_MATERIAL,
            game.getGameResult()
        )
    }

    @Test
    fun `K plus bishop vs K is insufficient material`() {

        val game =
            game("8/8/8/8/8/8/4k3/3BK3 w - - 0 1")

        assertTrue(game.isInsufficientMaterial())
    }

    @Test
    fun `K plus knight vs K is insufficient material`() {

        val game =
            game("8/8/8/8/8/8/4k3/3NK3 w - - 0 1")

        assertTrue(game.isInsufficientMaterial())
    }

    @Test
    fun `K plus rook vs K is not insufficient material`() {

        val game =
            game("8/8/8/8/8/8/4k3/3RK3 w - - 0 1")

        assertFalse(game.isInsufficientMaterial())
    }

    @Test
    fun `same coloured bishops is insufficient material`() {

        val game =
            game("8/8/8/8/5b2/8/4k3/2B1K3 w - - 0 1")

        assertTrue(game.isInsufficientMaterial())

        assertEquals(
            GameResult.DRAW_INSUFFICIENT_MATERIAL,
            game.getGameResult()
        )
    }

    @Test
    fun `opposite coloured bishops is not insufficient material`() {

        val game =
            game("8/8/8/8/4b3/8/4k3/2B1K3 w - - 0 1")

        assertFalse(game.isInsufficientMaterial())
    }

    @Test
    fun `50 move rule triggers at 100 half moves`() {

        val board = Board.getStartingBoard()

        board.halfMoveClock = 100

        val game = ChessGame(board)

        assertTrue(game.isFiftyMoveRule())

        assertEquals(
            GameResult.DRAW_50_MOVE_RULE,
            game.getGameResult()
        )
    }

    @Test
    fun `99 half moves is not a draw`() {

        val board = Board.getStartingBoard()

        board.halfMoveClock = 99

        val game = ChessGame(board)

        assertFalse(game.isFiftyMoveRule())
    }

    @Test
    fun `threefold repetition detected`() {

        val game = ChessGame(Board.getStartingBoard())

        repeat(3) {
            game.makeMove(game.findMove("g1", "f3")!!)
            game.makeMove(game.findMove("g8", "f6")!!)

            game.makeMove(game.findMove("f3", "g1")!!)
            game.makeMove(game.findMove("f6", "g8")!!)
        }

        assertTrue(game.isThreefoldRepetition())

        assertEquals(
            GameResult.DRAW_THREEFOLD_REPETITION,
            game.getGameResult()
        )
    }

    @Test
    fun `stalemate detected`() {

        val game =
            game("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")

        assertEquals(
            GameResult.DRAW_STALEMATE,
            game.getGameResult()
        )

        assertTrue(game.isGameOver())
    }

    @Test
    fun `fools mate checkmate detected`() {

        val game = ChessGame(Board.getStartingBoard())

        game.makeMove(game.findMove("f2", "f3")!!)
        game.makeMove(game.findMove("e7", "e5")!!)

        game.makeMove(game.findMove("g2", "g4")!!)
        game.makeMove(game.findMove("d8", "h4")!!)

        assertEquals(
            GameResult.BLACK_WIN,
            game.getGameResult()
        )
    }

    @Test
    fun `copy is independent of the original`() {

        val game = ChessGame(Board.getStartingBoard())
        val startFen = game.board.toFEN()

        val copy = game.copy()
        copy.makeMove(copy.findMove("e2", "e4")!!)

        assertEquals(startFen, game.board.toFEN(), "Original game should be unchanged")
        assertTrue(startFen != copy.board.toFEN(), "Copy should have advanced")
    }

    @Test
    fun `undo restores the position and threefold history`() {

        val game = ChessGame(Board.getStartingBoard())
        val startFen = game.board.toFEN()

        val move = game.findMove("g1", "f3")!!
        game.makeMove(move)
        game.undoMove(move)

        assertEquals(startFen, game.board.toFEN())
        assertFalse(game.isThreefoldRepetition())
    }

    @Test
    fun `findMove returns null for an illegal move`() {

        val game = ChessGame(Board.getStartingBoard())

        assertNull(game.findMove("e2", "e5"))
    }

    private fun game(fen: String): ChessGame {
        return ChessGame(Board.fromFEN(fen))
    }
}
