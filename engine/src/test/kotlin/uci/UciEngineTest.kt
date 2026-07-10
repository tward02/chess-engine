package uci

import com.tward.engine.board.Move
import com.tward.engine.game.ChessGame
import com.tward.engine.player.ChessBot
import com.tward.engine.player.ClockAware
import com.tward.uci.UciEngine
import com.tward.uci.UciMoveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UciEngineTest {

    // Bot that always plays the first legal move, so tests are deterministic and fast.
    private class FirstMoveBot : ChessBot {
        var lastTimeLeft: Int = -1
        override fun chooseMove(game: ChessGame, timeLeft: Int): Move {
            lastTimeLeft = timeLeft
            return game.getLegalMoves().first()
        }
    }

    private class ClockAwareFirstMoveBot : ChessBot, ClockAware {
        override var incrementMillis = -1
        override fun chooseMove(game: ChessGame, timeLeft: Int): Move = game.getLegalMoves().first()
    }

    private fun engineWith(bot: ChessBot, sink: MutableList<String>) =
        UciEngine(output = { sink.add(it) }, botFactory = { bot })

    @Test
    fun `uci handshake responds with id and uciok`() {
        val out = mutableListOf<String>()
        val engine = engineWith(FirstMoveBot(), out)

        engine.handle("uci")

        assertTrue(out.any { it.startsWith("id name") })
        assertTrue(out.any { it.startsWith("id author") })
        assertEquals("uciok", out.last())
    }

    @Test
    fun `isready responds readyok`() {
        val out = mutableListOf<String>()
        engineWith(FirstMoveBot(), out).handle("isready")
        assertEquals(listOf("readyok"), out)
    }

    @Test
    fun `quit returns false`() {
        val engine = engineWith(FirstMoveBot(), mutableListOf())
        assertTrue(engine.handle("isready"))
        assertFalse(engine.handle("quit"))
    }

    @Test
    fun `go on startpos returns a legal bestmove`() {
        val out = mutableListOf<String>()
        val engine = engineWith(FirstMoveBot(), out)

        engine.handle("position startpos")
        engine.handle("go wtime 60000 btime 60000")

        val bestmove = out.single { it.startsWith("bestmove ") }.removePrefix("bestmove ")
        // First legal move from the start position is generated for a white piece.
        val move = UciMoveCodec.findMove(ChessGame(com.tward.engine.board.Board.getStartingBoard()), bestmove)
        assertTrue(move != null, "bestmove '$bestmove' should be legal from startpos")
    }

    @Test
    fun `go forwards the side to move's increment to a clock-aware bot`() {
        val bot = ClockAwareFirstMoveBot()
        val engine = engineWith(bot, mutableListOf())

        engine.handle("position startpos")
        engine.handle("go wtime 60000 btime 60000 winc 2000 binc 3000")

        assertEquals(2000, bot.incrementMillis, "white to move: winc applies")
    }

    @Test
    fun `go without increments resets a clock-aware bot to zero`() {
        val bot = ClockAwareFirstMoveBot()
        val engine = engineWith(bot, mutableListOf())

        engine.handle("position startpos")
        engine.handle("go wtime 60000 btime 60000")

        assertEquals(0, bot.incrementMillis)
    }

    @Test
    fun `position startpos with moves is replayed and passes correct clock`() {
        val out = mutableListOf<String>()
        val bot = FirstMoveBot()
        val engine = engineWith(bot, out)

        // After 1. e2e4 it is Black to move; the engine should hand the bot Black's clock.
        engine.handle("position startpos moves e2e4")
        engine.handle("go wtime 30000 btime 45000")

        assertEquals(45000, bot.lastTimeLeft)
        assertTrue(out.any { it.startsWith("bestmove ") })
    }

    @Test
    fun `position fen is parsed`() {
        val out = mutableListOf<String>()
        val bot = FirstMoveBot()
        val engine = engineWith(bot, out)

        // Black to move; movetime is used directly as the budget.
        engine.handle("position fen rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        engine.handle("go movetime 1234")

        assertEquals(1234, bot.lastTimeLeft)
        assertTrue(out.any { it.startsWith("bestmove ") })
    }

    @Test
    fun `checkmate position returns null move`() {
        val out = mutableListOf<String>()
        val engine = engineWith(FirstMoveBot(), out)

        // Fool's mate: white is checkmated, white to move, no legal moves.
        engine.handle("position fen rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        engine.handle("go movetime 100")

        assertEquals("bestmove 0000", out.single { it.startsWith("bestmove") })
    }

    @Test
    fun `ucinewgame resets so colour clock maps correctly`() {
        val out = mutableListOf<String>()
        val bot = FirstMoveBot()
        val engine = engineWith(bot, out)

        engine.handle("ucinewgame")
        engine.handle("position startpos")
        engine.handle("go wtime 12345 btime 99999")

        assertEquals(12345, bot.lastTimeLeft)
    }
}