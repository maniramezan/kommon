package io.github.maniramezan.kommon.debugui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            MaterialTheme {
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
            MaterialTheme {
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
            MaterialTheme { AnalyticsDebugScreen(entries = entries, onClear = {}, zone = ZoneOffset.UTC) }
        }

        compose.onNodeWithText("purchase").assertIsDisplayed()
        compose.onNode(hasText("Flush") and hasClickAction()).performClick()

        compose.onNodeWithText("purchase").assertDoesNotExist()
        compose.onNodeWithText("flush").assertIsDisplayed()
    }

    @Test
    fun `analytics screen shows an empty state without entries`() {
        compose.setContent {
            MaterialTheme { AnalyticsDebugScreen(entries = emptyList(), onClear = {}) }
        }

        compose.onNodeWithText("No analytics calls yet").assertIsDisplayed()
    }

    @Test
    fun `remote config screen switches boolean overrides`() {
        val enabled = ConfigKey.bool("enabled", "Feature", false)
        val applied = mutableListOf<ConfigValue>()
        compose.setContent {
            MaterialTheme {
                RemoteConfigDebugScreen(
                    entries = listOf(ResolvedConfigEntry(enabled, enabled.defaultValue, ValueSource.DEFAULT)),
                    onOverride = { _, value -> applied += value },
                    onResetOverride = {},
                    onResetAll = {},
                )
            }
        }

        compose.onNodeWithContentDescription("enabled: Override value").performClick()

        assertEquals(listOf<ConfigValue>(ConfigValue.Bool(true)), applied)
    }

    @Test
    fun `remote config reset can be cancelled and a single key can be reset`() {
        val resets = mutableListOf<ConfigKey>()
        var resetAll = 0
        compose.setContent {
            MaterialTheme {
                RemoteConfigDebugScreen(
                    entries = listOf(ResolvedConfigEntry(retries, ConfigValue.Int(9), ValueSource.OVERRIDE)),
                    onOverride = { _, _ -> },
                    onResetOverride = { resets += it },
                    onResetAll = { resetAll++ },
                )
            }
        }

        compose.onNodeWithText("Reset all").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reset all overrides?").assertDoesNotExist()
        compose.onNodeWithText("Reset").performClick()

        assertEquals(0, resetAll)
        assertEquals(listOf(retries), resets)
    }

    @Test
    fun `analytics screen searches properties and clears captured calls`() {
        var clears = 0
        compose.setContent {
            MaterialTheme {
                AnalyticsDebugScreen(
                    entries =
                        listOf(
                            AnalyticsDebugEntry(1, 0, AnalyticsDebugAction.TRACK, "purchase", mapOf("currency" to "EUR")),
                            AnalyticsDebugEntry(2, 0, AnalyticsDebugAction.TRACK, "login", emptyMap()),
                        ),
                    onClear = { clears++ },
                )
            }
        }

        compose.onNodeWithText("Filter by name or property").performTextReplacement("EUR")
        compose.onNodeWithText("purchase").assertIsDisplayed()
        compose.onNodeWithText("login").assertDoesNotExist()
        compose.onNodeWithText("Clear filter").performClick()
        compose.onNodeWithText("login").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()

        assertEquals(1, clears)
    }
}
