package engine.board

import com.tward.engine.board.Square
import com.tward.engine.board.SquareType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SquareTest {

    @Test
    fun `a8 converts to algebraic notation`() {
        assertEquals(
            "a8",
            Square(0, 0).toString()
        )
    }

    @Test
    fun `h1 converts to algebraic notation`() {
        assertEquals(
            "h1",
            Square(7, 7).toString()
        )
    }

    @Test
    fun `e4 converts to algebraic notation`() {
        assertEquals(
            "e4",
            Square(4, 4).toString()
        )
    }

    @Test
    fun `fromString parses a8`() {
        assertEquals(
            Square(0, 0),
            Square.fromString("a8")
        )
    }

    @Test
    fun `fromString parses h1`() {
        assertEquals(
            Square(7, 7),
            Square.fromString("h1")
        )
    }

    @Test
    fun `fromString parses e4`() {
        assertEquals(
            Square(4, 4),
            Square.fromString("e4")
        )
    }

    @Test
    fun `toString and fromString are inverse operations`() {

        for (row in 0..7) {
            for (col in 0..7) {

                val square = Square(col, row)

                assertEquals(
                    square,
                    Square.fromString(square.toString())
                )
            }
        }
    }

    @Test
    fun `a8 is light square`() {
        assertEquals(
            SquareType.LIGHT,
            Square(0, 0).getSquareType()
        )
    }

    @Test
    fun `b8 is dark square`() {
        assertEquals(
            SquareType.DARK,
            Square(1, 0).getSquareType()
        )
    }

    @Test
    fun `a7 is dark square`() {
        assertEquals(
            SquareType.DARK,
            Square(0, 1).getSquareType()
        )
    }

    @Test
    fun `b7 is light square`() {
        assertEquals(
            SquareType.LIGHT,
            Square(1, 1).getSquareType()
        )
    }

    @Test
    fun `reject negative column`() {
        assertFailsWith<IllegalArgumentException> {
            Square(-1, 0)
        }
    }

    @Test
    fun `reject negative row`() {
        assertFailsWith<IllegalArgumentException> {
            Square(0, -1)
        }
    }

    @Test
    fun `reject column greater than seven`() {
        assertFailsWith<IllegalArgumentException> {
            Square(8, 0)
        }
    }

    @Test
    fun `reject row greater than seven`() {
        assertFailsWith<IllegalArgumentException> {
            Square(0, 8)
        }
    }

    @Test
    fun `all squares round trip correctly`() {

        for (file in 'a'..'h') {
            for (rank in 1..8) {

                val notation = "$file$rank"

                assertEquals(
                    notation,
                    Square.fromString(notation).toString()
                )
            }
        }
    }

    @Test
    fun `reject invalid file`() {
        assertFailsWith<IllegalArgumentException> {
            Square.fromString("i4")
        }
    }

    @Test
    fun `reject invalid rank`() {
        assertFailsWith<IllegalArgumentException> {
            Square.fromString("e9")
        }
    }

    @Test
    fun `reject invalid length`() {
        assertFailsWith<IllegalArgumentException> {
            Square.fromString("abc")
        }
    }
}
