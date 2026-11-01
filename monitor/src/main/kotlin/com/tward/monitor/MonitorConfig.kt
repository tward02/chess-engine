package com.tward.monitor

/**
 * Monitor settings, read from environment variables so the systemd unit can supply them from an
 * `EnvironmentFile` (see `deploy/monitor.env.example`).
 */
data class MonitorConfig(
    val bot: String,
    val token: String?,
    val lichessUrl: String = "https://lichess.org",
    val host: String = "0.0.0.0",
    val port: Int = 8090,
    val livePollSeconds: Long = 5,
    val idlePollSeconds: Long = 15,
    val profilePollSeconds: Long = 60,
    val processPattern: String? = "lichess-bot.py",
    val mqtt: MqttSettings? = null,
) {
    val botId: String get() = bot.lowercase()

    companion object {
        fun fromEnv(env: Map<String, String>): MonitorConfig {
            fun get(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun long(name: String, default: Long) = get(name)?.toLongOrNull() ?: default

            val bot = requireNotNull(get("LICHESS_BOT")) { "LICHESS_BOT (the bot's Lichess username) must be set" }
            val mqtt = get("MQTT_URL")?.let { url ->
                MqttSettings(
                    url = url,
                    username = get("MQTT_USERNAME"),
                    password = get("MQTT_PASSWORD"),
                    baseTopic = get("MQTT_TOPIC") ?: "chessbot/${bot.lowercase()}",
                    discoveryPrefix = get("MQTT_DISCOVERY_PREFIX") ?: "homeassistant",
                )
            }
            return MonitorConfig(
                bot = bot,
                token = get("LICHESS_TOKEN"),
                lichessUrl = (get("LICHESS_URL") ?: "https://lichess.org").trimEnd('/'),
                host = get("MONITOR_HOST") ?: "0.0.0.0",
                port = get("MONITOR_PORT")?.toIntOrNull() ?: 8090,
                livePollSeconds = long("LIVE_POLL_SECONDS", 5),
                idlePollSeconds = long("IDLE_POLL_SECONDS", 15),
                profilePollSeconds = long("PROFILE_POLL_SECONDS", 60),
                // "none" disables the process check (e.g. when the monitor runs on another host).
                processPattern = get("PROCESS_PATTERN").let { if (it == "none") null else it ?: "lichess-bot.py" },
                mqtt = mqtt,
            )
        }
    }
}

data class MqttSettings(
    val url: String,
    val username: String?,
    val password: String?,
    val baseTopic: String,
    val discoveryPrefix: String,
)
