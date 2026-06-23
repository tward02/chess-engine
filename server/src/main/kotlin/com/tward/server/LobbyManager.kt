package com.tward.server

import com.tward.shared.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** One connected lobby participant and its outbound message queue (drained by its WebSocket). */
class LobbyPlayer(val id: String) {
    @Volatile
    var name: String = ""
    @Volatile
    var status: PlayerStatus = PlayerStatus.AVAILABLE
    val outbox = Channel<LobbyServerMessage>(Channel.BUFFERED)
    val joined: Boolean get() = name.isNotEmpty()
}

/**
 * Tracks who is connected and brokers challenges. Challenging a bot starts a game immediately;
 * challenging a player sends them an invite they accept or decline, which then creates the game and
 * tells both sides their colour. When a game ends, its players are returned to "available".
 *
 * Mutations run under a [Mutex] so presence and the challenge handshakes stay consistent.
 */
class LobbyManager(private val registry: GameRegistry, private val scope: CoroutineScope) {

    private val players = ConcurrentHashMap<String, LobbyPlayer>()
    private val challenges = ConcurrentHashMap<String, ChallengeInfo>()
    private val mutex = Mutex()

    /** Called when a socket connects, before [join]; the player isn't listed until it has a name. */
    fun register(): LobbyPlayer = LobbyPlayer(UUID.randomUUID().toString().take(8)).also { players[it.id] = it }

    suspend fun join(player: LobbyPlayer, name: String) = mutex.withLock {
        player.name = name.ifBlank { "Anon-${player.id.take(4)}" }
        player.status = PlayerStatus.AVAILABLE
        send(player, LobbyServerMessage.Welcome(player.id))
        send(player, LobbyServerMessage.Bots(BotCatalog.infos()))
        send(player, LobbyServerMessage.Players(playerInfos()))
        broadcastPlayers()
    }

    suspend fun challengePlayer(from: LobbyPlayer, msg: LobbyClientMessage.ChallengePlayer) = mutex.withLock {
        val opponent = players[msg.opponentId]
        if (opponent == null || !opponent.joined) {
            send(from, LobbyServerMessage.LobbyError("That player is no longer available"))
            return@withLock
        }
        val challenge = ChallengeInfo(
            id = UUID.randomUUID().toString().take(8),
            fromId = from.id, fromName = from.name, toId = opponent.id,
            challengerColour = normaliseColour(msg.colour),
            initialTimeMillis = msg.initialTimeMillis, incrementMillis = msg.incrementMillis
        )
        challenges[challenge.id] = challenge
        send(opponent, LobbyServerMessage.IncomingChallenge(challenge))
    }

    suspend fun challengeBot(from: LobbyPlayer, msg: LobbyClientMessage.ChallengeBot) = mutex.withLock {
        val spec = BotCatalog.get(msg.botId)
        if (spec == null) {
            send(from, LobbyServerMessage.LobbyError("No such bot: ${msg.botId}"))
            return@withLock
        }
        val colour = normaliseColour(msg.colour)
        val human = Participant.Human(from.id, from.name)
        val bot = Participant.Bot(spec)
        val (white, black) = if (colour == "black") bot to human else human to bot
        val session = registry.create(white, black, msg.initialTimeMillis, msg.incrementMillis)

        from.status = PlayerStatus.IN_GAME
        send(from, LobbyServerMessage.GameStarted(session.id, colour))
        broadcastPlayers()
        watchForEnd(session, listOf(from.id))
        scope.launch { session.start() }   // let the bot move first if it has White
    }

    suspend fun accept(accepter: LobbyPlayer, challengeId: String) = mutex.withLock {
        val challenge = challenges.remove(challengeId)
        if (challenge == null || challenge.toId != accepter.id) {
            send(accepter, LobbyServerMessage.LobbyError("That challenge is no longer available"))
            return@withLock
        }
        val challenger = players[challenge.fromId]
        if (challenger == null || !challenger.joined) {
            send(accepter, LobbyServerMessage.LobbyError("The challenger has left"))
            return@withLock
        }

        val challengerP = Participant.Human(challenger.id, challenger.name)
        val accepterP = Participant.Human(accepter.id, accepter.name)
        val challengerColour = challenge.challengerColour
        val accepterColour = if (challengerColour == "white") "black" else "white"
        val (white, black) = if (challengerColour == "white") challengerP to accepterP else accepterP to challengerP

        val session = registry.create(white, black, challenge.initialTimeMillis, challenge.incrementMillis)
        challenger.status = PlayerStatus.IN_GAME
        accepter.status = PlayerStatus.IN_GAME
        send(challenger, LobbyServerMessage.GameStarted(session.id, challengerColour))
        send(accepter, LobbyServerMessage.GameStarted(session.id, accepterColour))
        broadcastPlayers()
        watchForEnd(session, listOf(challenger.id, accepter.id))
    }

    suspend fun decline(player: LobbyPlayer, challengeId: String) = mutex.withLock {
        val challenge = challenges.remove(challengeId) ?: return@withLock
        players[challenge.fromId]?.let { send(it, LobbyServerMessage.ChallengeDeclined(challengeId)) }
    }

    suspend fun disconnect(player: LobbyPlayer) = mutex.withLock {
        players.remove(player.id)
        challenges.values.removeIf { it.fromId == player.id || it.toId == player.id }
        player.outbox.close()
        broadcastPlayers()
    }

    // When the game ends, return its still-connected players to the available pool.
    private fun watchForEnd(session: GameSession, playerIds: List<String>) {
        scope.launch {
            session.events.first { it is ServerMessage.GameOver }
            mutex.withLock {
                playerIds.forEach { players[it]?.status = PlayerStatus.AVAILABLE }
                broadcastPlayers()
            }
        }
    }

    private fun playerInfos(): List<PlayerInfo> =
        players.values.filter { it.joined }.map { PlayerInfo(it.id, it.name, it.status) }

    private fun broadcastPlayers() {
        val infos = playerInfos()
        players.values.filter { it.joined }.forEach { it.outbox.trySend(LobbyServerMessage.Players(infos)) }
    }

    private fun send(player: LobbyPlayer, message: LobbyServerMessage) {
        player.outbox.trySend(message)
    }

    private fun normaliseColour(colour: String): String = if (colour.lowercase() == "black") "black" else "white"
}
