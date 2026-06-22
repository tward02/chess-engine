package com.tward.server

/** Who occupies a side of a game: a connected human, or a catalog bot. */
sealed interface Participant {
    data class Human(val playerId: String, val name: String) : Participant
    data class Bot(val spec: BotSpec) : Participant
}
