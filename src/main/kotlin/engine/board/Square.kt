package com.tward.engine.board

data class Square(val col: Int, val row: Int) {

    init {
        require(col in 0..7 && row in 0..7) { "Coordinates must be between 0 and 7" }
    }

    override fun toString(): String {
        val file = ('a' + col).toString()
        val rank = (8 - row).toString()
        return (
            file + rank
        )
    }

    fun getSquareType(): SquareType {
        return if ((col + row) % 2 == 0) SquareType.LIGHT else SquareType.DARK
    }

    companion object {

        fun fromString(str: String): Square {

            require(str.length == 2) {
                "Square must be exactly 2 characters: '$str'"
            }

            val file = str[0].lowercaseChar()
            val rank = str[1]

            require(file in 'a'..'h') {
                "File must be between a and h: '$str'"
            }

            require(rank in '1'..'8') {
                "Rank must be between 1 and 8: '$str'"
            }

            val col = file - 'a'
            val row = 8 - rank.digitToInt()

            return Square(col, row)
        }
    }

}

enum class SquareType {
    LIGHT, DARK
}
