package com.tward.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class OutcomeTextTest {

    @Test
    fun `wins describe the manner of victory`() {
        assertEquals("White won by checkmate", describeOutcome(GameStatus.WHITE_WON, Termination.CHECKMATE))
        assertEquals("Black won on time", describeOutcome(GameStatus.BLACK_WON, Termination.TIMEOUT))
        assertEquals("White won by resignation", describeOutcome(GameStatus.WHITE_WON, Termination.RESIGNATION))
    }

    @Test
    fun `draws describe the reason`() {
        assertEquals("Draw by threefold repetition", describeOutcome(GameStatus.DRAW, Termination.THREEFOLD_REPETITION))
        assertEquals("Draw by stalemate", describeOutcome(GameStatus.DRAW, Termination.STALEMATE))
        assertEquals(
            "Draw by insufficient material",
            describeOutcome(GameStatus.DRAW, Termination.INSUFFICIENT_MATERIAL)
        )
    }

    @Test
    fun `in-progress games read as in progress`() {
        assertEquals("In progress", describeOutcome(GameStatus.IN_PROGRESS, Termination.ONGOING))
    }

    @Test
    fun `outcomeText on the dto delegates to describeOutcome`() {
        val dto = GameStateDto(
            gameId = "g", fen = "f", sideToMove = "white", whiteMillis = 0, blackMillis = 0,
            status = GameStatus.BLACK_WON, termination = Termination.CHECKMATE
        )
        assertEquals("Black won by checkmate", dto.outcomeText())
    }
}
