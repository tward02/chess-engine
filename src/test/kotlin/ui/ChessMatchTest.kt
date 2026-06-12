package ui

import com.tward.engine.board.Board
import com.tward.engine.board.Square
import com.tward.engine.game.ChessGame
import com.tward.engine.game.GameResult
import com.tward.engine.player.HumanPlayer
import com.tward.ui.ChessMatch
import com.tward.ui.ClockManager
import com.tward.ui.TimeControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChessMatchTest {

    @Test
    fun `select piece updates ui state`() {

        val match = createMatch()

        match.select(Square(4, 6))

        assertEquals(
            Square(4, 6),
            match.uiState.selectedSquare
        )

        assertTrue(
            Square(4, 5) in match.uiState.legalTargets
        )

        assertTrue(
            Square(4, 4) in match.uiState.legalTargets
        )
    }

    @Test
    fun `select empty square produces no legal targets`() {

        val match = createMatch()

        match.select(Square(4, 4))

        assertEquals(
            Square(4, 4),
            match.uiState.selectedSquare
        )

        assertTrue(
            match.uiState.legalTargets.isEmpty()
        )
    }

    @Test
    fun `clear selection resets ui state`() {

        val match = createMatch()

        match.select(Square(4, 6))

        match.clearSelection()

        assertNull(match.uiState.selectedSquare)

        assertTrue(
            match.uiState.legalTargets.isEmpty()
        )

        assertNull(match.uiState.gameResult)
    }

    @Test
    fun `move increments version`() {

        val match = createMatch()

        val before = match.moveVersion

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertEquals(
            before + 1,
            match.moveVersion
        )
    }

    @Test
    fun `move clears selection`() {

        val match = createMatch()

        match.select(Square(4, 6))

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertNull(match.uiState.selectedSquare)

        assertTrue(
            match.uiState.legalTargets.isEmpty()
        )
    }

    @Test
    fun `move updates board position`() {

        val match = createMatch()

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertNull(
            match.game.board.getPiece(
                Square(4, 6)
            )
        )

        assertNotNull(
            match.game.board.getPiece(
                Square(4, 4)
            )
        )
    }

    @Test
    fun `cannot select when game is over`() {

        val match = createMatch()

        match.game.result =
            GameResult.WHITE_WIN

        match.select(Square(4, 6))

        assertNull(
            match.uiState.selectedSquare
        )
    }

    @Test
    fun `cannot make move when game is over`() {

        val match = createMatch()

        match.game.result =
            GameResult.WHITE_WIN

        val versionBefore =
            match.moveVersion

        match.makeMove(
            match.game.findMove("e2", "e4")!!
        )

        assertEquals(
            versionBefore,
            match.moveVersion
        )
    }

    @Test
    fun `check game over updates ui result`() {

        val match = createMatch()

        match.game.result =
            GameResult.DRAW_STALEMATE

        match.checkGameOver()

        assertEquals(
            GameResult.DRAW_STALEMATE,
            match.uiState.gameResult
        )
    }

    @Test
    fun `white timeout gives black win`() {

        val match = createMatch()

        match.clockManager.whiteMillis = 0

        match.checkGameOver()

        assertEquals(
            GameResult.BLACK_TIME_WIN,
            match.game.result
        )
    }

    @Test
    fun `black timeout gives white win`() {

        val match = createMatch()

        match.clockManager.blackMillis = 0

        match.checkGameOver()

        assertEquals(
            GameResult.WHITE_TIME_WIN,
            match.game.result
        )
    }

    @Test
    fun `fools mate propagates result into ui`() {

        val match = createMatch()

        match.makeMove(match.game.findMove("f2", "f3")!!)
        match.makeMove(match.game.findMove("e7", "e5")!!)

        match.makeMove(match.game.findMove("g2", "g4")!!)
        match.makeMove(match.game.findMove("d8", "h4")!!)

        assertEquals(
            GameResult.BLACK_WIN,
            match.game.result
        )

        assertEquals(
            GameResult.BLACK_WIN,
            match.uiState.gameResult
        )
    }

    private fun createMatch(): ChessMatch {
        return ChessMatch(
            game = ChessGame(Board.getStartingBoard()),
            whitePlayer = HumanPlayer(),
            blackPlayer = HumanPlayer(),
            clockManager = ClockManager(
                TimeControl(
                    initialMillis = 300_000,
                    incrementMillis = 0
                )
            )
        )
    }

}