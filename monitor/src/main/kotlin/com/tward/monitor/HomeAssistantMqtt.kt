package com.tward.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builds the Home Assistant MQTT-discovery payloads: one retained config message per entity, all
 * reading a single flat JSON state topic. Pure, so it is unit-tested without a broker.
 */
class HomeAssistantDiscovery(private val settings: MqttSettings, private val bot: String) {

    private val nodeId = "chessbot_" + bot.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    val stateTopic = "${settings.baseTopic}/state"
    val availabilityTopic = "${settings.baseTopic}/availability"
    val haStatusTopic = "${settings.discoveryPrefix}/status"

    private data class Entity(
        val component: String,
        val key: String,
        val name: String,
        val icon: String,
        val extra: Map<String, String> = emptyMap(),
    )

    private val entities: List<Entity> = buildList {
        add(Entity("sensor", "state", "State", "mdi:chess-king"))
        add(Entity("binary_sensor", "online", "Lichess online", "mdi:web", mapOf("device_class" to "connectivity")))
        add(Entity("binary_sensor", "process", "Process running", "mdi:cog", mapOf("device_class" to "running")))
        add(Entity("binary_sensor", "in_game", "In game", "mdi:chess-pawn", mapOf("device_class" to "running")))
        add(Entity("sensor", "live_games", "Live games", "mdi:chess-knight", mapOf("state_class" to "measurement")))
        add(Entity("sensor", "opponent", "Current opponent", "mdi:account"))
        add(Entity("sensor", "eval", "Current eval", "mdi:scale-balance", mapOf("unit_of_measurement" to "cp", "state_class" to "measurement")))
        add(Entity("sensor", "last_result", "Last result", "mdi:flag-checkered"))
        LichessParsing.SPEEDS.forEach { speed ->
            add(Entity("sensor", "rating_$speed", "${speed.replaceFirstChar(Char::uppercase)} rating", "mdi:chart-line",
                mapOf("state_class" to "measurement")))
        }
        add(Entity("sensor", "games_today", "Games today", "mdi:counter", mapOf("state_class" to "total")))
        add(Entity("sensor", "wins_today", "Wins today", "mdi:trophy", mapOf("state_class" to "total")))
        add(Entity("sensor", "losses_today", "Losses today", "mdi:close-circle", mapOf("state_class" to "total")))
        add(Entity("sensor", "draws_today", "Draws today", "mdi:handshake", mapOf("state_class" to "total")))
        add(Entity("sensor", "rating_change_today", "Rating change today", "mdi:delta", mapOf("state_class" to "measurement")))
    }

    /** (topic, payload) pairs, published retained. */
    fun configMessages(): List<Pair<String, String>> = entities.map { e ->
        val payload = buildJsonObject {
            put("name", e.name)
            put("unique_id", "${nodeId}_${e.key}")
            put("has_entity_name", true)
            put("state_topic", stateTopic)
            // A missing key renders "None", which HA shows as "unknown" rather than logging a template error.
            put("value_template", "{{ value_json.${e.key} | default('None') }}")
            put("availability_topic", availabilityTopic)
            put("icon", e.icon)
            if (e.component == "binary_sensor") {
                put("payload_on", "ON")
                put("payload_off", "OFF")
            }
            e.extra.forEach { (k, v) -> put(k, v) }
            putJsonObject("device") {
                putJsonArray("identifiers") { add(JsonPrimitive(nodeId)) }
                put("name", "Chess bot $bot")
                put("manufacturer", "tward")
                put("model", "chess-engine monitor")
            }
        }
        "${settings.discoveryPrefix}/${e.component}/$nodeId/${e.key}/config" to payload.toString()
    }

    /**
     * The flat state message. Keys with no value (e.g. the eval when no game is on) are omitted,
     * which the value templates turn into HA's "unknown".
     */
    fun statePayload(status: MonitorStatus): String {
        val fields = linkedMapOf<String, Any?>()
        fun onOff(value: Boolean?) = value?.let { if (it) "ON" else "OFF" }
        val current = status.liveGames.firstOrNull()

        fields["state"] = status.state.name.lowercase()
        fields["online"] = onOff(status.lichessOnline)
        fields["process"] = onOff(status.processRunning)
        fields["in_game"] = onOff(status.state == BotState.PLAYING)
        fields["live_games"] = status.liveGames.size
        fields["opponent"] = current?.let { g -> g.opponent + (g.opponentRating?.let { " ($it)" } ?: "") } ?: "none"
        fields["eval"] = current?.evalCp
        fields["last_result"] = status.recentGames.firstOrNull()?.let(::describe) ?: "none"
        LichessParsing.SPEEDS.forEach { fields["rating_$it"] = status.ratings[it]?.rating }
        fields["games_today"] = status.today.games
        fields["wins_today"] = status.today.wins
        fields["losses_today"] = status.today.losses
        fields["draws_today"] = status.today.draws
        fields["rating_change_today"] = status.today.ratingChange

        return JsonObject(fields.filterValues { it != null }.mapValues { (_, v) ->
            when (v) {
                is Number -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        }).toString()
    }

    private fun describe(game: RecentGame): String {
        val verb = when (game.outcome) {
            Outcome.WIN -> "Won vs"
            Outcome.LOSS -> "Lost to"
            Outcome.DRAW -> "Drew with"
            Outcome.ABORTED -> "Aborted vs"
        }
        val diff = game.ratingDiff?.let { if (it >= 0) " +$it" else " $it" } ?: ""
        return "$verb ${game.opponent} (${game.speed})$diff"
    }
}

/**
 * Publishes [HomeAssistantDiscovery] to a broker: discovery on every (re)connect and whenever HA
 * announces itself on `homeassistant/status`, state whenever it changes. Paho reconnects
 * automatically; a last-will marks every entity unavailable if the monitor dies.
 */
class MqttBridge(private val settings: MqttSettings, bot: String) : AutoCloseable {

    private val log = LoggerFactory.getLogger(MqttBridge::class.java)
    private val discovery = HomeAssistantDiscovery(settings, bot)
    private val client = MqttClient(settings.url, "chess-monitor-${bot.lowercase()}", MemoryPersistence())

    @Volatile private var lastState: String? = null

    // Announcing runs here, never on Paho's callback thread: a publish made from a callback can't
    // wait for its ack, so a burst of them overflows the in-flight window and gets dropped.
    private val announcer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "mqtt-announce").apply { isDaemon = true }
    }

    fun start(scope: CoroutineScope, status: StateFlow<MonitorStatus>): Job {
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                log.info("MQTT connected to {} (reconnect={})", serverURI, reconnect)
                announcer.execute {
                    runCatching {
                        client.subscribe(discovery.haStatusTopic, 1)
                        announce()
                    }.onFailure { log.warn("MQTT announce failed: {}", it.message) }
                }
            }

            override fun connectionLost(cause: Throwable?) = log.warn("MQTT connection lost: {}", cause?.message)

            override fun messageArrived(topic: String, message: MqttMessage) {
                // HA publishes "online" here on startup; re-send discovery so entities reappear.
                if (topic == discovery.haStatusTopic && message.toString() == "online") {
                    announcer.execute { runCatching { announce() } }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            maxInflight = 100
            settings.username?.let { userName = it }
            settings.password?.let { password = it.toCharArray() }
            setWill(discovery.availabilityTopic, "offline".toByteArray(), 1, true)
        }

        return scope.launch(Dispatchers.IO) {
            connectWithRetry(options)
            status
                .filter { it.updatedAt != null }
                .map(discovery::statePayload)
                .distinctUntilChanged()
                .collect { payload ->
                    lastState = payload
                    publish(discovery.stateTopic, payload)
                }
        }
    }

    private suspend fun connectWithRetry(options: MqttConnectOptions) {
        while (true) {
            try {
                client.connect(options)
                return
            } catch (e: Exception) {
                log.warn("MQTT connect to {} failed: {} — retrying in 30s", settings.url, e.message)
                kotlinx.coroutines.delay(30_000.milliseconds)
            }
        }
    }

    private fun announce() {
        discovery.configMessages().forEach { (topic, payload) -> publish(topic, payload) }
        publish(discovery.availabilityTopic, "online")
        lastState?.let { publish(discovery.stateTopic, it) }
        log.info("MQTT discovery published ({} entities)", discovery.configMessages().size)
    }

    private fun publish(topic: String, payload: String) {
        if (!client.isConnected) return
        runCatching { client.publish(topic, payload.toByteArray(), 1, true) }
            .onFailure { log.warn("MQTT publish to {} failed: {}", topic, it.message) }
    }

    override fun close() {
        runCatching {
            publish(discovery.availabilityTopic, "offline")
            client.disconnect(2_000)
        }
        announcer.shutdownNow()
        client.close()
    }
}
