package com.tward.monitor

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val json = Json { encodeDefaults = true }

fun main() {
    val log = LoggerFactory.getLogger("com.tward.monitor")
    val config = MonitorConfig.fromEnv(System.getenv())
    log.info(
        "Monitoring {} (live games: {}, MQTT: {}, process check: {})",
        config.bot, config.token != null, config.mqtt?.url ?: "off", config.processPattern ?: "off",
    )

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val source = LichessHttpSource(config)
    val poller = StatusPoller(config, source)
    poller.start(scope)
    val mqtt = config.mqtt?.let { settings -> MqttBridge(settings, config.bot).also { it.start(scope, poller.status) } }

    Runtime.getRuntime().addShutdownHook(Thread {
        mqtt?.close()
        scope.cancel()
        source.close()
    })

    embeddedServer(Netty, port = config.port, host = config.host) { monitorModule(poller.status) }.start(wait = true)
}

/** HTTP surface: the dashboard page, its JSON feed (also usable by an HA `rest` sensor) and a health probe. */
fun Application.monitorModule(status: StateFlow<MonitorStatus>) {
    install(ContentNegotiation) { json(json) }

    routing {
        get("/healthz") { call.respondText("ok") }
        get("/api/status") { call.respond(status.value) }
        staticResources("/", "dashboard")
    }
}
