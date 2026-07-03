package io.github.maniramezan.kommon.remoteconfig.debug

import androidx.test.core.app.ApplicationProvider
import io.github.maniramezan.kommon.remoteconfig.ConfigKey
import io.github.maniramezan.kommon.remoteconfig.ConfigValue
import io.github.maniramezan.kommon.remoteconfig.ConfigValueType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LocalOverrideStoreTest {
    private val key =
        ConfigKey(
            id = "test_flag",
            description = "test flag",
            valueType = ConfigValueType.BOOL,
            defaultValue = ConfigValue.Bool(false),
        )

    @Test
    fun `override returns null when no override is set`() {
        val store = LocalOverrideStore(ApplicationProvider.getApplicationContext(), isDebug = true, prefsName = "test-1")
        assertNull(store.override(key))
    }

    @Test
    fun `setOverride then override round-trips the value`() {
        val store = LocalOverrideStore(ApplicationProvider.getApplicationContext(), isDebug = true, prefsName = "test-2")
        store.setOverride(key, ConfigValue.Bool(true))
        assertEquals(ConfigValue.Bool(true), store.override(key))
        assertTrue(store.hasOverride(key))
    }

    @Test
    fun `release builds never read or write overrides`() {
        val store = LocalOverrideStore(ApplicationProvider.getApplicationContext(), isDebug = false, prefsName = "test-3")
        store.setOverride(key, ConfigValue.Bool(true))
        assertNull(store.override(key))
        assertFalse(store.hasOverride(key))
    }

    @Test
    fun `removeOverride clears a single key`() {
        val store = LocalOverrideStore(ApplicationProvider.getApplicationContext(), isDebug = true, prefsName = "test-4")
        store.setOverride(key, ConfigValue.Bool(true))
        store.removeOverride(key)
        assertNull(store.override(key))
    }

    @Test
    fun `removeAllOverrides clears every key`() {
        val store = LocalOverrideStore(ApplicationProvider.getApplicationContext(), isDebug = true, prefsName = "test-5")
        store.setOverride(key, ConfigValue.Bool(true))
        store.removeAllOverrides()
        assertFalse(store.hasOverride(key))
    }
}
