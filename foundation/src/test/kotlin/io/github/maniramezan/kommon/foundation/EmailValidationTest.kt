package io.github.maniramezan.kommon.foundation

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Plain JVM test on purpose: isValidEmail() must not depend on android.util.Patterns.
class EmailValidationTest {
    @Test
    fun `valid email addresses pass`() {
        assertTrue("person@example.com".isValidEmail())
        assertTrue("  person@example.com  ".isValidEmail())
        assertTrue("first.last+tag@sub.example.co.uk".isValidEmail())
        assertTrue("a_b%c-d@my-host.io".isValidEmail())
    }

    @Test
    fun `invalid email addresses fail`() {
        assertFalse("not-an-email".isValidEmail())
        assertFalse("".isValidEmail())
        assertFalse("person@".isValidEmail())
        assertFalse("@example.com".isValidEmail())
        assertFalse("person@example".isValidEmail())
        assertFalse("person@-example.com".isValidEmail())
        assertFalse("per son@example.com".isValidEmail())
    }
}
