package io.github.maniramezan.kommon.foundation

import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber

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
    fun `setup plants a debug tree in debug builds and a release tree otherwise`() {
        TimberLogger.setup(isDebug = true)
        TimberLogger(tagPrefix = "Test").info("tag", "debug message")

        TimberLogger.setup(isDebug = false)
        TimberLogger(tagPrefix = "Test").info("tag", "release message (suppressed)")
        TimberLogger(tagPrefix = "Test").error("tag", "release message (forwarded)")
    }
}
