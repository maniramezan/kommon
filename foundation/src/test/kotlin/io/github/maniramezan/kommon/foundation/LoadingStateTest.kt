package io.github.maniramezan.kommon.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadingStateTest {
    @Test
    fun `isLoading, value, and errorValue reflect the current state`() {
        assertFalse(LoadingState.Idle.isLoading)
        assertTrue(LoadingState.Loading.isLoading)
        assertEquals("data", LoadingState.Loaded("data").value)
        assertEquals(null, LoadingState.Idle.value)
        val error = LoadingError("oops")
        assertEquals(error, LoadingState.Failed(error).errorValue)
    }
}

class LoadingErrorTest {
    @Test
    fun `unknown host maps to a connectivity message`() {
        val error = LoadingError.from(java.net.UnknownHostException())
        assertTrue(error.isRetryable)
        assertTrue(error.message.contains("internet connection"))
    }

    @Test
    fun `socket timeout maps to a timeout message`() {
        val error = LoadingError.from(java.net.SocketTimeoutException())
        assertTrue(error.isRetryable)
        assertTrue(error.message.contains("timed out"))
    }

    @Test
    fun `404 message maps to a non-retryable not-found error`() {
        val error = LoadingError.from(RuntimeException("Client request invalid: 404 Not Found"))
        assertFalse(error.isRetryable)
    }

    @Test
    fun `401 message maps to a non-retryable auth error`() {
        val error = LoadingError.from(RuntimeException("401 Unauthorized"))
        assertFalse(error.isRetryable)
    }

    @Test
    fun `unrecognized failure maps to a generic retryable error`() {
        val error = LoadingError.from(RuntimeException("something weird"))
        assertTrue(error.isRetryable)
    }

    @Test
    fun `429 message maps to a retryable rate-limit error`() {
        val error = LoadingError.from(RuntimeException("429 Too Many Requests"))
        assertTrue(error.isRetryable)
        assertTrue(error.message.contains("Too many requests"))
    }

    @Test
    fun `5xx server message maps to a retryable server error`() {
        val error = LoadingError.from(RuntimeException("500 Internal Server Error"))
        assertTrue(error.isRetryable)
        assertTrue(error.message.contains("our end"))
    }

    @Test
    fun `wrapped network cause is unwrapped from nested exceptions`() {
        val wrapped = RuntimeException("wrapper", java.net.ConnectException())
        val error = LoadingError.from(wrapped)
        assertTrue(error.message.contains("internet connection"))
    }
}
