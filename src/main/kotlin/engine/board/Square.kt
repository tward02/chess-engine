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
}

enum class SquareType {
    LIGHT, DARK
}
