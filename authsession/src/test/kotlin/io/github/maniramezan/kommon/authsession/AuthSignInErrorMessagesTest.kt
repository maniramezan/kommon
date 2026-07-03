package io.github.maniramezan.kommon.authsession

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSignInErrorMessagesTest {
    @Test
    fun `cert hash mismatch maps to a provider-specific unavailable message`() {
        val error = RuntimeException("package certificate hash mismatch for com.example.app")
        assertEquals(
            "Google sign-in is unavailable right now. Please try again later.",
            error.authSignInErrorMessage("Google"),
        )
    }

    @Test
    fun `INVALID_CERT_HASH code maps to a provider-specific unavailable message`() {
        val error = RuntimeException("Status{statusCode=INVALID_CERT_HASH}")
        assertEquals(
            "Apple sign-in is unavailable right now. Please try again later.",
            error.authSignInErrorMessage("Apple"),
        )
    }

    @Test
    fun `other failures pass through the exception message`() {
        val error = RuntimeException("network unreachable")
        assertEquals("network unreachable", error.authSignInErrorMessage("Google"))
    }

    @Test
    fun `a null message falls back to the generic failure message`() {
        val error = RuntimeException()
        assertEquals("Sign in failed. Please try again.", error.authSignInErrorMessage("Google"))
    }

    @Test
    fun `a custom generic failure message is honored`() {
        val error = RuntimeException()
        assertEquals(
            "Custom failure text",
            error.authSignInErrorMessage("Google", genericFailureMessage = "Custom failure text"),
        )
    }
}

class AuthUserTest {
    @Test
    fun `carries the expected fields`() {
        val user = AuthUser(uid = "u1", isAnonymous = true, displayName = "Ada", email = "ada@example.com")
        assertEquals("u1", user.uid)
        assertEquals(true, user.isAnonymous)
        assertEquals("Ada", user.displayName)
        assertEquals("ada@example.com", user.email)
        assertEquals(user, user.copy())
    }
}

class AuthSessionHintTest {
    @Test
    fun `carries the expected field`() {
        assertEquals(true, AuthSessionHint(hasAccount = true).hasAccount)
        assertEquals(AuthSessionHint(true), AuthSessionHint(true).copy())
    }
}
