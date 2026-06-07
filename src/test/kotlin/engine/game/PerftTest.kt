package engine.game

import com.tward.engine.board.Board
import com.tward.engine.game.MoveGenerator
import engine.LongRunning
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class PerftTest {

    @Test
    fun `test start position perft depth 1`() {
        val board = Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val generator = MoveGenerator(board)

        assertEquals(20L, generator.perft(1))
    }

    @Test
    fun `test start position perft depth 2`() {
        val board = Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val generator = MoveGenerator(board)

        assertEquals(400L, generator.perft(2))
    }

    @Test
    fun `test start position perft depth 3`() {
        val board = Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val generator = MoveGenerator(board)

        assertEquals(8902L, generator.perft(3))
    }

    @Test
    fun `test start position perft depth 4`() {
        val board = Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val generator = MoveGenerator(board)

        assertEquals(197281L, generator.perft(4))
    }

    @Test
    fun `test start position perft depth 5`() {
        val board = Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val generator = MoveGenerator(board)

        assertEquals(4865609L, generator.perft(5))
    }

    @Test
    @LongRunning
    fun `test start position perft depth 6`() {
        val board = Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val generator = MoveGenerator(board)

        assertEquals(119060324L, generator.perft(6))
    }

    @Test
    fun `test kiwipete position depth 1`() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(48L, generator.perft(1))
    }

    @Test
    fun `test kiwipete position depth 2`() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(2039L, generator.perft(2))
    }

    @Test
    fun `test kiwipete position depth 3`() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(97862L, generator.perft(3))
    }

    @Test
    fun `test kiwipete position depth 4`() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(4085603L, generator.perft(4))
    }

    @Test
    @LongRunning
    fun `test kiwipete position depth 5`() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(193690690L, generator.perft(5))
    }

    @Test
    fun `test chessprogramming org position 3 depth 1`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(14L, generator.perft(1))
    }

    @Test
    fun `test chessprogramming org position 3 depth 2`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(191L, generator.perft(2))
    }

    @Test
    fun `test chessprogramming org position 3 depth 3`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(2812L, generator.perft(3))
    }

    @Test
    fun `test chessprogramming org position 3 depth 4`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(43238, generator.perft(4))
    }

    @Test
    fun `test chessprogramming org position 3 depth 5`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(674624L, generator.perft(5))
    }

    @Test
    fun `test chessprogramming org position 3 depth 6`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(11030083L, generator.perft(6))
    }

    @Test
    @LongRunning
    fun `test chessprogramming org position 3 depth 7`() {
        val fen = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(178633661L, generator.perft(7))
    }

    @Test
    fun `test chessprogramming org position 4 depth 1`() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(6L, generator.perft(1))
    }

    @Test
    fun `test chessprogramming org position 4 depth 2`() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(264L, generator.perft(2))
    }

    @Test
    fun `test chessprogramming org position 4 depth 3`() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(9467, generator.perft(3))
    }

    @Test
    fun `test chessprogramming org position 4 depth 4`() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(422333L, generator.perft(4))
    }

    @Test
    fun `test chessprogramming org position 4 depth 5`() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(15833292L, generator.perft(5))
    }

    @Test
    @LongRunning
    fun `test chessprogramming org position 4 depth 6`() {
        val fen = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(706045033L, generator.perft(6))
    }

    @Test
    fun `test chessprogramming org position 4 reversed depth 1`() {
        val fen = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(6L, generator.perft(1))
    }

    @Test
    fun `test chessprogramming org position 4 reversed depth 2`() {
        val fen = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(264L, generator.perft(2))
    }

    @Test
    fun `test chessprogramming org position 4 reversed depth 3`() {
        val fen = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(9467, generator.perft(3))
    }

    @Test
    fun `test chessprogramming org position 4 reversed depth 4`() {
        val fen = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(422333L, generator.perft(4))
    }

    @Test
    fun `test chessprogramming org position 4 reversed depth 5`() {
        val fen = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(15833292L, generator.perft(5))
    }

    @Test
    @LongRunning
    fun `test chessprogramming org position 4 reversed depth 6`() {
        val fen = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(706045033L, generator.perft(6))
    }

    @Test
    fun `test chessprogramming org position 5 depth 1`() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(44L, generator.perft(1))
    }

    @Test
    fun `test chessprogramming org position 5 depth 2`() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(1486L, generator.perft(2))
    }

    @Test
    fun `test chessprogramming org position 5 depth 3`() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(62379L, generator.perft(3))
    }

    @Test
    fun `test chessprogramming org position 5 depth 4`() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(2103487L, generator.perft(4))
    }

    @Test
    @LongRunning
    fun `test chessprogramming org position 5 depth 5`() {
        val fen = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(89941194L, generator.perft(5))
    }

    @Test
    fun `test chessprogramming org position 6 depth 1`() {
        val fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(46L, generator.perft(1))
    }

    @Test
    fun `test chessprogramming org position 6 depth 2`() {
        val fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(2079L, generator.perft(2))
    }

    @Test
    fun `test chessprogramming org position 6 depth 3`() {
        val fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(89890L, generator.perft(3))
    }

    @Test
    fun `test chessprogramming org position 6 depth 4`() {
        val fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(3894594L, generator.perft(4))
    }

    @Test
    @LongRunning
    fun `test chessprogramming org position 6 depth 5`() {
        val fen = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
        val board = Board.fromFEN(fen)
        val generator = MoveGenerator(board)

        assertEquals(164075551L, generator.perft(5))
    }
}
