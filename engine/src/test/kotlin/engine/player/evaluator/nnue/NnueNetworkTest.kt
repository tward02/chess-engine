package engine.player.evaluator.nnue

import com.tward.engine.board.Colour
import com.tward.engine.board.Piece
import com.tward.engine.board.PieceType
import com.tward.engine.board.Square
import com.tward.engine.player.evaluator.nnue.NnueFeatures
import com.tward.engine.player.evaluator.nnue.NnueNetwork
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NnueNetworkTest {

    @Test
    fun `feature indices are unique and in range`() {
        val seen = HashSet<Int>()
        for (colour in Colour.entries) {
            for (type in PieceType.entries) {
                for (col in 0..7) for (row in 0..7) {
                    val index = NnueFeatures.index(Colour.WHITE, Piece(type, colour), Square(col, row))
                    assertTrue(index in 0 until NnueFeatures.COUNT)
                    assertTrue(seen.add(index), "duplicate feature index $index")
                }
            }
        }
        assertEquals(NnueFeatures.COUNT, seen.size)
    }

    @Test
    fun `mirror converts the white-perspective index to the black-perspective index`() {
        for (colour in Colour.entries) {
            for (type in PieceType.entries) {
                for (col in 0..7) for (row in 0..7) {
                    val piece = Piece(type, colour)
                    val square = Square(col, row)
                    assertEquals(
                        NnueFeatures.index(Colour.BLACK, piece, square),
                        NnueFeatures.mirror(NnueFeatures.index(Colour.WHITE, piece, square))
                    )
                }
            }
        }
    }

    @Test
    fun `mirror is its own inverse`() {
        for (index in 0 until NnueFeatures.COUNT) {
            assertEquals(index, NnueFeatures.mirror(NnueFeatures.mirror(index)))
        }
    }

    @Test
    fun `a saved network loads back identically`(@TempDir dir: Path) {
        val original = NnueNetwork.random(hiddenSize = 16, random = Random(7))
        val file = dir.resolve("net.nnue")
        original.save(file)

        val loaded = NnueNetwork.load(file)

        assertEquals(original.hiddenSize, loaded.hiddenSize)
        assertContentEquals(original.ftWeights, loaded.ftWeights)
        assertContentEquals(original.ftBias, loaded.ftBias)
        assertContentEquals(original.outWeights, loaded.outWeights)
        assertEquals(original.outBias, loaded.outBias)
    }

    @Test
    fun `the bundled default network loads`() {
        assertTrue(NnueNetwork.default().hiddenSize > 0)
    }
}
