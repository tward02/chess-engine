package uci

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.bot.EliteNegamaxBot
import com.tward.uci.UciEngine
import com.tward.uci.UciMoveCodec
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UciPonderTest {

    // Out of book, quiet middlegame; White to move.
    private val fen = "r1bq1rk1/pp2bppp/2n1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R1BQ1RK1 w - - 0 8"

    private val out = LinkedBlockingQueue<String>()
    private val engine = UciEngine(
        output = { out.add(it) },
        botFactory = { colour ->
            EliteNegamaxBot(colour = colour, useOpeningBookMoves = false, transpositionTableBits = 16)
        },
        asyncSearch = true
    )

    private fun nextBestmove(timeoutMillis: Long = 5_000): String? {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return null
            val line = out.poll(remaining, TimeUnit.NANOSECONDS) ?: return null
            if (line.startsWith("bestmove")) return line
        }
    }

    private fun searchOnce(): Pair<String, String> {
        engine.handle("position fen $fen")
        engine.handle("go movetime 300")
        val parts = assertNotNull(nextBestmove()).split(" ")
        assertEquals(4, parts.size, "a searched move names the expected reply: $parts")
        assertEquals("ponder", parts[2])
        return parts[1] to parts[3]
    }

    @Test
    fun `bestmove carries a legal ponder move`() {
        val (move, reply) = searchOnce()
        val game = ChessGame(Board.fromFEN(fen))
        game.makeMove(assertNotNull(UciMoveCodec.findMove(game, move)))
        assertNotNull(UciMoveCodec.findMove(game, reply), "ponder move $reply must be legal after $move")
    }

    @Test
    fun `go ponder waits for stop before sending bestmove`() {
        val (move, reply) = searchOnce()
        engine.handle("position fen $fen moves $move $reply")
        engine.handle("go ponder movetime 100")

        assertNull(nextBestmove(400), "a ponder search must not finish by itself")
        engine.handle("stop")
        assertNotNull(nextBestmove(), "stop ends the ponder with a bestmove")
    }

    @Test
    fun `ponderhit turns the ponder into a timed search`() {
        val (move, reply) = searchOnce()
        engine.handle("position fen $fen moves $move $reply")
        engine.handle("go ponder movetime 200")
        engine.handle("ponderhit")

        val bestmove = assertNotNull(nextBestmove())
        val game = ChessGame(Board.fromFEN(fen))
        listOf(move, reply).forEach { game.makeMove(assertNotNull(UciMoveCodec.findMove(game, it))) }
        assertNotNull(UciMoveCodec.findMove(game, bestmove.split(" ")[1]))
    }

    @Test
    fun `a new position stops a running ponder`() {
        val (move, reply) = searchOnce()
        engine.handle("position fen $fen moves $move $reply")
        engine.handle("go ponder wtime 60000 btime 60000")
        engine.handle("position fen $fen")   // implies stop; must not hang
        assertNotNull(nextBestmove())
        engine.handle("go movetime 100")
        assertTrue(assertNotNull(nextBestmove()).startsWith("bestmove "))
    }

    @Test
    fun `bot ponder returns once asked to stop`() {
        val bot = EliteNegamaxBot(colour = Colour.WHITE, useOpeningBookMoves = false, transpositionTableBits = 16)
        val game = ChessGame(Board.fromFEN(fen))
        val before = game.board.toFEN()
        val stopAt = System.nanoTime() + 200_000_000
        bot.ponder(game) { System.nanoTime() > stopAt }
        assertEquals(before, game.board.toFEN(), "pondering restores the position")
        assertTrue(bot.nodesSearched > 0)
    }
}
