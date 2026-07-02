package engine.player.evaluator

import com.tward.engine.board.Board
import com.tward.engine.game.ChessGame
import com.tward.engine.player.evaluator.NnueEvaluator
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import engine.player.evaluator.nnue.TestNetworks
import java.util.Random
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NnueEvaluatorTest {

    private fun eval(network: NnueNetwork, fen: String) =
        NnueEvaluator(network).evaluate(ChessGame(Board.fromFEN(fen)))

    @Test
    fun `the material network counts material from white's perspective`() {
        val net = TestNetworks.material()
        val queenUp = eval(net, "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1")
        assertTrue(abs(queenUp - 900) <= 1, "expected ~+900, got $queenUp")

        val rookDown = eval(net, "r3k3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(abs(rookDown + 500) <= 1, "expected ~-500, got $rookDown")
    }

    @Test
    fun `the score is white-perspective regardless of the side to move`() {
        val net = TestNetworks.material()
        val whiteToMove = eval(net, "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1")
        val blackToMove = eval(net, "4k3/8/8/8/8/8/8/Q3K3 b - - 0 1")
        assertEquals(whiteToMove, blackToMove, "same position must score the same for either side to move")
    }

    @Test
    fun `swapping the colours exactly negates the score, for any network`() {
        val net = NnueNetwork.random(hiddenSize = 32, random = Random(99))
        val positions = listOf(
            "4k3/8/8/8/8/8/8/Q3K3 w - - 0 1",
            "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w - - 0 1",
            "8/2p5/3p4/KP5r/7k/8/4P1P1/8 w - - 0 1"
        )
        for (fen in positions) {
            assertEquals(
                eval(net, fen), -eval(net, flipColours(fen)),
                "colour symmetry broken for $fen"
            )
        }
    }

    /** Mirrors the board vertically and swaps the colours (and the side to move). */
    private fun flipColours(fen: String): String {
        val parts = fen.split(" ")
        val ranks = parts[0].split("/").reversed().joinToString("/") { rank ->
            rank.map { c ->
                when {
                    !c.isLetter() -> c
                    c.isUpperCase() -> c.lowercaseChar()
                    else -> c.uppercaseChar()
                }
            }.joinToString("")
        }
        val sideToMove = if (parts[1] == "w") "b" else "w"
        return "$ranks $sideToMove - - ${parts[4]} ${parts[5]}"
    }
}
