package io.github.maniramezan.kommon.debugui

import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugAction
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugLabelsTest {
    @Test
    fun `every analytics action has a distinct label`() {
        val labels = AnalyticsDebugLabels()

        val shown = AnalyticsDebugAction.entries.map(labels::label)

        assertEquals(AnalyticsDebugAction.entries.size, shown.toSet().size)
    }

    @Test
    fun `analytics labels can be localized per action`() {
        val labels = AnalyticsDebugLabels(track = "Ereignis", flush = "Senden")

        assertEquals("Ereignis", labels.label(AnalyticsDebugAction.TRACK))
        assertEquals("Senden", labels.label(AnalyticsDebugAction.FLUSH))
    }

    @Test
    fun `remote config labels default to English and can be replaced`() {
        val labels = RemoteConfigDebugLabels(resetAll = "Alles zurücksetzen")

        assertEquals("Remote config", labels.title)
        assertEquals("Alles zurücksetzen", labels.resetAll)
    }

    @Test
    fun `every config source uses its localized label`() {
        val labels = RemoteConfigDebugLabels(sourceDefault = "Fallback", sourceRemote = "Server", sourceOverride = "Local")

        assertEquals("Fallback", labels.sourceLabel(io.github.maniramezan.kommon.remoteconfig.ValueSource.DEFAULT))
        assertEquals("Server", labels.sourceLabel(io.github.maniramezan.kommon.remoteconfig.ValueSource.REMOTE))
        assertEquals("Local", labels.sourceLabel(io.github.maniramezan.kommon.remoteconfig.ValueSource.OVERRIDE))
    }
}
