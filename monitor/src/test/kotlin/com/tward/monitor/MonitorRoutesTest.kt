package com.tward.monitor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitorRoutesTest {

    private val status = MutableStateFlow(MonitorStatus(bot = "TBot", state = BotState.IDLE, lichessOnline = true))

    @Test
    fun `serves status json, the dashboard and a health probe`() = testApplication {
        application { monitorModule(status) }

        assertEquals("ok", client.get("/healthz").bodyAsText())

        val decoded = Json.decodeFromString<MonitorStatus>(client.get("/api/status").bodyAsText())
        assertEquals(BotState.IDLE, decoded.state)
        assertEquals("TBot", decoded.bot)

        val page = client.get("/")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("api/status" in page.bodyAsText())
    }
}
