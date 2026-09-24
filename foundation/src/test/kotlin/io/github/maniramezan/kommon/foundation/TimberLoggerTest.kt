package io.github.maniramezan.kommon.foundation

import android.util.Log
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TimberLoggerTest {
    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `every call runs without a planted tree`() {
        val logger = TimberLogger(tagPrefix = "Test")
        logger.debug("tag", "message")
        logger.info("tag", "message")
        logger.warning("tag", "message", RuntimeException("boom"))
        logger.error("tag", "message", RuntimeException("boom"))
    }

    @Test
    fun `release tree forwards only warnings and errors to the sink with the prefixed tag`() {
        val forwarded = mutableListOf<Triple<Int, String?, String>>()
        TimberLogger.setup(isDebug = false) { priority, tag, message, _ -> forwarded += Triple(priority, tag, message) }
        val logger = TimberLogger(tagPrefix = "App")

        logger.debug("tag", "dropped")
        logger.info("tag", "dropped")
        logger.warning("tag", "warned")
        logger.error("tag", "failed", RuntimeException("boom"))

        assertEquals(
            listOf(Triple(Log.WARN, "App:tag", "warned"), Triple(Log.ERROR, "App:tag", "failed")),
            forwarded.map { (priority, tag, message) -> Triple(priority, tag, message.lineSequence().first()) },
        )
    }

    @Test
    fun `repeated setup replaces the previously planted tree instead of duplicating it`() {
        TimberLogger.setup(isDebug = true)
        TimberLogger.setup(isDebug = true)
        TimberLogger.setup(isDebug = false)

        assertEquals(1, Timber.treeCount)
    }
}
