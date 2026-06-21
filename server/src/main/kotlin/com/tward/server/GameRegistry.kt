package com.tward.server

import com.tward.shared.CreateGameRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store of live games. A database-backed store (for history, reconnection across restarts
 * and horizontal scale) is a later step; the interface stays the same.
 */
class GameRegistry(private val engineService: EngineService) {

    private val sessions = ConcurrentHashMap<String, GameSession>()

    fun create(request: CreateGameRequest): GameSession {
        val id = UUID.randomUUID().toString().take(8)
        return GameSession(id, request, engineService).also { sessions[id] = it }
    }

    fun get(id: String): GameSession? = sessions[id]
}
