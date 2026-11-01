package com.tward.monitor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeAssistantDiscoveryTest {

    private val settings = MqttSettings("tcp://x:1883", null, null, baseTopic = "chessbot/tbot", discoveryPrefix = "homeassistant")
    private val discovery = HomeAssistantDiscovery(settings, "TBot")

    @Test
    fun `config messages use discovery topics and share one state topic`() {
        val messages = discovery.configMessages().toMap()
        val rating = Json.parseToJsonElement(messages.getValue("homeassistant/sensor/chessbot_tbot/rating_blitz/config")).jsonObject
        assertEquals("chessbot/tbot/state", rating.getValue("state_topic").jsonPrimitive.content)
        assertEquals("chessbot/tbot/availability", rating.getValue("availability_topic").jsonPrimitive.content)
        assertEquals("chessbot_tbot_rating_blitz", rating.getValue("unique_id").jsonPrimitive.content)
        assertEquals("{{ value_json.rating_blitz | default('None') }}", rating.getValue("value_template").jsonPrimitive.content)

        val online = Json.parseToJsonElement(messages.getValue("homeassistant/binary_sensor/chessbot_tbot/online/config")).jsonObject
        assertEquals("connectivity", online.getValue("device_class").jsonPrimitive.content)

        // Every entity's unique_id is distinct, or HA would merge them.
        val ids = messages.values.map { Json.parseToJsonElement(it).jsonObject.getValue("unique_id").jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `state payload flattens status and omits unknown values`() {
        val status = MonitorStatus(
            bot = "TBot",
            state = BotState.IDLE,
            lichessOnline = true,
            processRunning = null,
            ratings = mapOf("blitz" to PerfRating(1822, 340, false, 31)),
            today = DaySummary(games = 4, wins = 3, losses = 1, draws = 0, ratingChange = 12),
            recentGames = listOf(RecentGame("g", "u", "white", "Human", 1900, "blitz", true, Outcome.WIN, "mate", 7, 0)),
        )
        val state = Json.parseToJsonElement(discovery.statePayload(status)).jsonObject
        assertEquals("idle", state.getValue("state").jsonPrimitive.content)
        assertEquals("ON", state.getValue("online").jsonPrimitive.content)
        assertEquals("OFF", state.getValue("in_game").jsonPrimitive.content)
        assertEquals("1822", state.getValue("rating_blitz").jsonPrimitive.content)
        assertEquals("12", state.getValue("rating_change_today").jsonPrimitive.content)
        assertEquals("Won vs Human (blitz) +7", state.getValue("last_result").jsonPrimitive.content)
        assertFalse("process" in state)
        assertFalse("rating_bullet" in state)
        assertFalse("eval" in state)
        assertTrue(state.getValue("rating_blitz").jsonPrimitive.isString.not())
    }
}
