package logging

import com.tward.logging.Log
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingTest {

    private class CapturingHandler : Handler() {
        val records = mutableListOf<LogRecord>()
        override fun publish(record: LogRecord) { records.add(record) }
        override fun flush() {}
        override fun close() {}
    }

    @Test
    fun `info messages are emitted at info level`() {

        val name = "test.logger.info"
        val handler = attach(name, Level.INFO)

        Log.of(name).info { "hello" }

        assertEquals(1, handler.records.size)
        assertEquals("hello", handler.records.first().message)
        assertEquals(Level.INFO, handler.records.first().level)
    }

    @Test
    fun `debug messages are suppressed when level is info`() {

        val name = "test.logger.debugSuppressed"
        val handler = attach(name, Level.INFO)

        Log.of(name).debug { "should not appear" }

        assertTrue(handler.records.isEmpty())
    }

    @Test
    fun `debug messages are emitted when level is fine`() {

        val name = "test.logger.debugEnabled"
        val handler = attach(name, Level.FINE)

        Log.of(name).debug { "appears now" }

        assertEquals(1, handler.records.size)
        assertEquals("appears now", handler.records.first().message)
    }

    @Test
    fun `lazy message is not built when level disabled`() {

        val name = "test.logger.lazy"
        attach(name, Level.INFO)

        var built = false
        Log.of(name).debug {
            built = true
            "expensive"
        }

        assertTrue(!built, "Disabled log level should not invoke the message lambda")
    }

    private open class BaseBot { val log = Log.of(this) }
    private class SubBot : BaseBot()

    @Test
    fun `instance logger is named after the runtime class, not the declaring superclass`() {

        val name = SubBot::class.qualifiedName!!
        val handler = attach(name, Level.INFO)

        SubBot().log.info { "from subclass" }

        assertEquals(1, handler.records.size)
        assertEquals(name, handler.records.first().loggerName)
    }

    private fun attach(name: String, level: Level): CapturingHandler {
        val logger = Logger.getLogger(name)
        logger.useParentHandlers = false
        logger.handlers.forEach { logger.removeHandler(it) }
        val handler = CapturingHandler().apply { this.level = Level.ALL }
        logger.addHandler(handler)
        logger.level = level
        return handler
    }
}
