package io.github.maniramezan.kommon.debugui

import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugAction
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugEntry
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalyticsDebugModelTest {
    private val entries =
        listOf(
            entry(1, AnalyticsDebugAction.TRACK, "purchase", mapOf("sku" to "pro_yearly")),
            entry(2, AnalyticsDebugAction.TRACK_SCREEN, "screen_view", mapOf("screen_name" to "home")),
            entry(3, AnalyticsDebugAction.TRACK, "share"),
        )

    @Test
    fun `newestFirst reverses and filters by action`() {
        assertEquals(listOf(3L, 2L, 1L), entries.newestFirst(null, "").map { it.id })
        assertEquals(listOf(3L, 1L), entries.newestFirst(AnalyticsDebugAction.TRACK, "").map { it.id })
    }

    @Test
    fun `query matches names, property keys and property values`() {
        assertEquals(listOf(3L), entries.newestFirst(null, "SHARE").map { it.id })
        assertEquals(listOf(1L), entries.newestFirst(null, "yearly").map { it.id })
        assertEquals(listOf(2L), entries.newestFirst(null, "screen_name").map { it.id })
    }

    @Test
    fun `actions lists present actions in declaration order`() {
        assertEquals(listOf(AnalyticsDebugAction.TRACK, AnalyticsDebugAction.TRACK_SCREEN), entries.reversed().actions())
    }

    @Test
    fun `property summary joins pairs and is null when empty`() {
        assertEquals("sku=pro_yearly", entries[0].propertySummary())
        assertNull(entries[2].propertySummary())
    }

    @Test
    fun `time is formatted to the millisecond in the given zone`() {
        val entry = entry(1, AnalyticsDebugAction.FLUSH, "flush", timestampMs = 3_723_004L)

        assertEquals("01:02:03.004", entry.formattedTime(ZoneOffset.UTC))
    }

    private fun entry(
        id: Long,
        action: AnalyticsDebugAction,
        name: String,
        properties: Map<String, Any> = emptyMap(),
        timestampMs: Long = 0L,
    ) = AnalyticsDebugEntry(id = id, timestampMs = timestampMs, action = action, name = name, properties = properties)
}
