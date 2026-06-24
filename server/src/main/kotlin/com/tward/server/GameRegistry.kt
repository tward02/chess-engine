package com.tward.server

import kotlinx.coroutines.CoroutineScope
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store of live games. A database-backed store (history, reconnection across restarts,
 * horizontal scale) is a later step; the interface stays the same. [scope] hosts each session's
 * background clock watcher.
 */
class GameRegistry(private val scope: CoroutineScope) {

    private val sessions = ConcurrentHashMap<String, GameSession>()

    fun create(
        white: Participant,
        black: Participant,
        initialTimeMillis: Long,
        incrementMillis: Long
    ): GameSession {
        val id = UUID.randomUUID().toString().take(8)
        return GameSession(id, white, black, initialTimeMillis, incrementMillis, scope).also { sessions[id] = it }
    }

    fun get(id: String): GameSession? = sessions[id]
}
