package io.github.maniramezan.kommon.crashreporting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingCrashlyticsClientTest {
    @Test
    fun `records errors, custom values, logs, and user id`() {
        val client = RecordingCrashlyticsClient()
        val error = IllegalStateException("boom")

        client.setUserId("user-1")
        client.recordError(error, mapOf("screen" to "home"))
        client.setCustomValue("plan", "free")
        client.log("hello")

        assertEquals("user-1", client.userId)
        assertEquals(1, client.recordedErrors.size)
        assertEquals(error, client.recordedErrors.first().throwable)
        assertEquals("home", client.recordedErrors.first().context["screen"])
        assertEquals("free", client.customValues["plan"])
        assertEquals(listOf("hello"), client.logMessages)

        client.clearUserId()
        assertNull(client.userId)
    }
}

class NoOpCrashlyticsClientTest {
    @Test
    fun `every call is a no-op that never throws`() {
        val client = NoOpCrashlyticsClient()
        client.setUserId("user-1")
        client.recordError(IllegalStateException("boom"))
        client.setCustomValue("k", "v")
        client.log("hello")
        client.clearUserId()
    }
}
