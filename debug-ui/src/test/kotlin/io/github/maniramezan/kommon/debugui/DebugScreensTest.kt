package io.github.maniramezan.kommon.debugui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.maniramezan.kmpcomponents.KmpTheme
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugAction
import io.github.maniramezan.kommon.analytics.debug.AnalyticsDebugEntry
import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ResolvedConfigEntry
import io.github.maniramezan.kommon.remoteconfig.ValueSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DebugScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val retries = ConfigKey.int("max_retries", "Retry budget", 3)

    @Test
    fun `remote config screen applies only valid overrides`() {
        val applied = mutableListOf<Pair<ConfigKey, ConfigValue>>()
        compose.setContent {
            KmpTheme {
                RemoteConfigDebugScreen(
                    entries = listOf(ResolvedConfigEntry(retries, retries.defaultValue, ValueSource.DEFAULT)),
                    onOverride = { key, value -> applied += key to value },
                    onResetOverride = {},
                    onResetAll = {},
                )
            }
        }

        compose.onNodeWithText("3").performTextReplacement("abc")
        compose.onNodeWithText("Not a valid value for this key").assertIsDisplayed()
        compose.onNodeWithText("abc").performTextReplacement("7")
        compose.onNodeWithText("Apply").performClick()

        assertEquals(listOf<Pair<ConfigKey, ConfigValue>>(retries to ConfigValue.Int(7)), applied)
    }

    @Test
    fun `remote config screen confirms before resetting every override`() {
        var resetAll = 0
        compose.setContent {
            KmpTheme {
                RemoteConfigDebugScreen(
                    entries = listOf(ResolvedConfigEntry(retries, ConfigValue.Int(9), ValueSource.OVERRIDE)),
                    onOverride = { _, _ -> },
                    onResetOverride = {},
                    onResetAll = { resetAll += 1 },
                )
            }
        }

        compose.onNodeWithText("Reset all").performClick()
        compose.onNodeWithText("Reset all overrides?").assertIsDisplayed()
        assertEquals(0, resetAll)
        compose.onNode(hasText("Reset all") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(1, resetAll)
    }

    @Test
    fun `analytics screen filters entries by action`() {
        val entries =
            listOf(
                AnalyticsDebugEntry(id = 1, timestampMs = 0, action = AnalyticsDebugAction.TRACK, name = "purchase", properties = mapOf()),
                AnalyticsDebugEntry(id = 2, timestampMs = 0, action = AnalyticsDebugAction.FLUSH, name = "flush", properties = mapOf()),
            )
        compose.setContent {
            KmpTheme { AnalyticsDebugScreen(entries = entries, onClear = {}, zone = ZoneOffset.UTC) }
        }

        compose.onNodeWithText("purchase").assertIsDisplayed()
        // The filter chip, not the "Flush" badge on the entry row.
        compose.onNode(hasText("Flush") and hasClickAction()).performClick()

        compose.onNodeWithText("purchase").assertDoesNotExist()
        compose.onNodeWithText("flush").assertIsDisplayed()
    }

    @Test
    fun `analytics screen shows an empty state without entries`() {
        compose.setContent {
            KmpTheme { AnalyticsDebugScreen(entries = emptyList(), onClear = {}) }
        }

        compose.onNodeWithText("No analytics calls yet").assertIsDisplayed()
    }
}
