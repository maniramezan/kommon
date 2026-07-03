package io.github.maniramezan.kommon.foundation

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class EmailValidationTest {
    @Test
    fun `valid email addresses pass`() {
        assertTrue("person@example.com".isValidEmail())
        assertTrue("  person@example.com  ".isValidEmail())
    }

    @Test
    fun `invalid email addresses fail`() {
        assertFalse("not-an-email".isValidEmail())
        assertFalse("".isValidEmail())
    }
}
