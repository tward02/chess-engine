package com.tward.monitor

import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.ServerSocket
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs [MqttBridge] against a real (embedded) broker, the way Home Assistant's Mosquitto sees it. */
class MqttBridgeTest {

    private val port = ServerSocket(0).use { it.localPort }
    private val broker = Server()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun startBroker() {
        broker.startServer(MemoryConfig(Properties().apply {
            setProperty("host", "127.0.0.1")
            setProperty("port", port.toString())
            setProperty("allow_anonymous", "true")
            setProperty("persistence_enabled", "false")
        }))
    }

    @AfterTest
    fun stopBroker() {
        scope.cancel()
        broker.stopServer()
    }

    @Test
    fun `publishes discovery, availability and state to the broker`() {
        val received = ConcurrentHashMap<String, String>()
        val listener = MqttClient("tcp://127.0.0.1:$port", "test-listener", MemoryPersistence())
        listener.connect()
        listener.subscribe("#") { topic, message -> received[topic] = message.toString() }

        val settings = MqttSettings("tcp://127.0.0.1:$port", null, null, "chessbot/tbot", "homeassistant")
        val status = MutableStateFlow(MonitorStatus(bot = "TBot", state = BotState.IDLE, lichessOnline = true, updatedAt = 1))
        val bridge = MqttBridge(settings, "TBot")
        bridge.start(scope, status)

        val expected = HomeAssistantDiscovery(settings, "TBot").configMessages().size
        val deadline = System.currentTimeMillis() + 10_000
        fun discoveryCount() = received.keys.count { it.startsWith("homeassistant/") && it.endsWith("/config") }
        while (System.currentTimeMillis() < deadline &&
            (discoveryCount() < expected || !received.containsKey("chessbot/tbot/state"))) {
            Thread.sleep(50)
        }

        assertEquals(expected, discoveryCount(), "discovery configs received: ${received.keys}")
        assertEquals("online", received["chessbot/tbot/availability"])
        assertTrue(received.getValue("chessbot/tbot/state").contains("\"state\":\"idle\""))

        bridge.close()
        listener.disconnect()
    }
}
