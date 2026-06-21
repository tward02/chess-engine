package com.tward.server

import com.tward.engine.board.Colour
import com.tward.engine.player.ChessBot
import com.tward.engine.player.bot.AdvancedNegamaxBot
import com.tward.engine.player.bot.MiniMaxBot
import com.tward.engine.player.bot.NegamaxBot
import com.tward.shared.BotDifficulty

/**
 * Supplies the bots the server plays. Abstracted so the in-process implementation (calling the
 * `:engine` bots directly) can later be swapped for, or joined by, a UCI-subprocess implementation
 * that drives `uci-engine.jar` or another UCI engine (e.g. Stockfish) over stdio.
 */
interface EngineService {
    fun createBot(difficulty: BotDifficulty, colour: Colour): ChessBot
}

/** Calls the engine bots in the server process — lightweight, no subprocess management. */
class InProcessEngineService : EngineService {
    override fun createBot(difficulty: BotDifficulty, colour: Colour): ChessBot = when (difficulty) {
        BotDifficulty.EASY -> MiniMaxBot(depth = 2, colour = colour)
        BotDifficulty.MEDIUM -> NegamaxBot(colour = colour, maxThinkTimeMillis = 1_000)
        BotDifficulty.HARD -> AdvancedNegamaxBot(colour = colour)
    }
}
