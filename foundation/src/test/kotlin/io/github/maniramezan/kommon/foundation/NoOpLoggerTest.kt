package io.github.maniramezan.kommon.foundation

import kotlin.test.Test

class NoOpLoggerTest {
    @Test
    fun `every call is a no-op that never throws`() {
        NoOpLogger.debug("tag", "message")
        NoOpLogger.info("tag", "message")
        NoOpLogger.warning("tag", "message", RuntimeException("boom"))
        NoOpLogger.error("tag", "message", RuntimeException("boom"))
    }
}
