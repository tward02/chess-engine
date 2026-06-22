package com.tward.server

import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.uci.UciMoveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotCatalogTest {

    @Test
    fun `catalog is non-empty with unique ids`() {
        assertTrue(BotCatalog.specs.size >= 10, "expected a good range of bots")
        assertEquals(BotCatalog.specs.size, BotCatalog.specs.map { it.id }.toSet().size, "bot ids must be unique")
    }

    @Test
    fun `every catalog bot builds and returns a legal opening move`() {
        val game = ChessGame(Board.getStartingBoard())
        val legal = game.getLegalMoves().map { UciMoveCodec.encode(it) }.toSet()

        for (spec in BotCatalog.specs) {
            val bot = BotFactory.build(spec, Colour.WHITE)
            val move = bot.chooseMove(game.copy(), timeLeft = 500)  // small budget keeps every bot fast
            assertTrue(UciMoveCodec.encode(move) in legal, "${spec.id} returned an illegal move")
        }
    }

    @Test
    fun `infos expose the public fields`() {
        val info = BotCatalog.infos().first { it.id == "grandmaster-greg" }
        assertTrue(info.approxElo > 2000)
        assertTrue(info.name.isNotBlank())
    }
}
