package com.tward.client.model

import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.shared.ChallengeInfo
import com.tward.shared.GameStateDto
import com.tward.shared.GameStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

/**
 * Drives [ChessClient]'s state machine directly. The networking methods launch on the (unconfined)
 * scope and the sockets are null in tests, so those launches are harmless no-ops — the state changes
 * (selection, promotion offers, challenge list) happen synchronously and are what we assert.
 */
class ChessClientTest {

    private val client = ChessClient(CoroutineScope(Dispatchers.Unconfined))

    @AfterTest
    fun tearDown() = client.close()

    private fun sq(name: String) = Square.fromString(name)

    private fun gameState(
        sideToMove: String = "white",
        status: GameStatus = GameStatus.IN_PROGRESS,
        legalMoves: List<String> = emptyList()
    ) = GameStateDto(
        gameId = "g",
        fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        sideToMove = sideToMove,
        whiteMillis = 60_000,
        blackMillis = 60_000,
        status = status,
        legalMoves = legalMoves
    )

    private fun challenge(id: String) = ChallengeInfo(
        id = id, fromId = "p", fromName = "Pat", toId = "me",
        challengerColour = "white", initialTimeMillis = 60_000, incrementMillis = 0
    )

    @Test
    fun `the first click selects a square`() {
        client.myColour = "white"
        client.game = gameState(legalMoves = listOf("e2e4"))
        client.clickSquare(sq("e2"))
        assertEquals(sq("e2"), client.selected)
    }

    @Test
    fun `clicking the selected square again clears the selection`() {
        client.myColour = "white"
        client.game = gameState(legalMoves = listOf("e2e4"))
        client.clickSquare(sq("e2"))
        client.clickSquare(sq("e2"))
        assertNull(client.selected)
    }

    @Test
    fun `completing a legal move clears the selection and offers no promotion`() {
        client.myColour = "white"
        client.game = gameState(legalMoves = listOf("e2e4", "e2e3"))
        client.clickSquare(sq("e2"))
        client.clickSquare(sq("e4"))
        assertNull(client.selected)
        assertTrue(client.promotionMoves.isEmpty())
    }

    @Test
    fun `clicking an illegal target reselects that square`() {
        client.myColour = "white"
        client.game = gameState(legalMoves = listOf("e2e4"))
        client.clickSquare(sq("e2"))
        client.clickSquare(sq("h5"))   // no legal e2->h5
        assertEquals(sq("h5"), client.selected)
    }

    @Test
    fun `a promotion target offers all four promotion pieces for the right squares`() {
        client.myColour = "white"
        client.game = gameState(legalMoves = listOf("e7e8q", "e7e8r", "e7e8b", "e7e8n"))
        client.clickSquare(sq("e7"))
        client.clickSquare(sq("e8"))

        assertEquals(4, client.promotionMoves.size)
        assertEquals(
            setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT),
            client.promotionMoves.mapNotNull { it.promotionType }.toSet()
        )
        client.promotionMoves.forEach {
            assertEquals(sq("e7"), it.from)
            assertEquals(sq("e8"), it.to)
        }
    }

    @Test
    fun `choosing a promotion clears the pending promotion offer`() {
        client.myColour = "white"
        client.game = gameState(legalMoves = listOf("e7e8q", "e7e8r", "e7e8b", "e7e8n"))
        client.clickSquare(sq("e7"))
        client.clickSquare(sq("e8"))
        assertTrue(client.promotionMoves.isNotEmpty())

        client.makePromotionMove(client.promotionMoves.first())
        assertTrue(client.promotionMoves.isEmpty())
    }

    @Test
    fun `you cannot move on the opponent's turn`() {
        client.myColour = "white"
        client.game = gameState(sideToMove = "black", legalMoves = listOf("e7e5"))
        client.clickSquare(sq("e7"))
        assertNull(client.selected)
        assertEquals("Not your turn", client.status)
    }

    @Test
    fun `wrong-turn clicks are silent once the game is over`() {
        client.myColour = "white"
        client.status = "Black won by checkmate"
        client.game = gameState(sideToMove = "black", status = GameStatus.BLACK_WON)
        client.clickSquare(sq("e7"))
        assertNull(client.selected)
        assertEquals("Black won by checkmate", client.status)   // not overwritten with "Not your turn"
    }

    @Test
    fun `clicking with no active game does nothing`() {
        client.clickSquare(sq("e2"))
        assertNull(client.selected)
    }

    @Test
    fun `accepting a challenge removes it from the list`() {
        client.challenges = listOf(challenge("c1"), challenge("c2"))
        client.accept("c1")
        assertEquals(listOf("c2"), client.challenges.map { it.id })
    }

    @Test
    fun `declining a challenge removes it from the list`() {
        client.challenges = listOf(challenge("c1"), challenge("c2"))
        client.decline("c2")
        assertEquals(listOf("c1"), client.challenges.map { it.id })
    }

    @Test
    fun `leaving a game returns to the lobby and clears game state`() {
        client.game = gameState()
        client.selected = sq("e2")
        client.screen = Screen.GAME
        client.leaveGame()
        assertEquals(Screen.LOBBY, client.screen)
        assertNull(client.game)
        assertNull(client.selected)
    }
}
