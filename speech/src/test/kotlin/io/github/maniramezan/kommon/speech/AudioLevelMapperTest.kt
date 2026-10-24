package io.github.maniramezan.kommon.speech

import org.junit.Test
import kotlin.test.assertEquals

class AudioLevelMapperTest {
    @Test
    fun `the silence floor maps to zero and the loud ceiling to one`() {
        assertEquals(0f, AudioLevelMapper.level(AudioLevelMapper.SILENCE_DB))
        assertEquals(1f, AudioLevelMapper.level(AudioLevelMapper.LOUD_DB))
    }

    @Test
    fun `values between map linearly`() {
        assertEquals(0.5f, AudioLevelMapper.level(4f), 1e-6f)
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(0f, AudioLevelMapper.level(-40f))
        assertEquals(1f, AudioLevelMapper.level(80f))
    }

    @Test
    fun `NaN is silence`() {
        assertEquals(0f, AudioLevelMapper.level(Float.NaN))
    }
}
