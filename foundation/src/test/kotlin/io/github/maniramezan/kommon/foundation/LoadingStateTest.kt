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

class LoadingErrorHeuristicsTest {
    @Test
    fun `digits embedded in a longer number are not treated as a status code`() {
        assertEquals(LoadingError.UNKNOWN, LoadingError.from(RuntimeException("failed to load order 4040")))
    }

    @Test
    fun `a stray 5 next to the word server is not a 5xx`() {
        assertEquals(LoadingError.UNKNOWN, LoadingError.from(RuntimeException("Server rejected 5 items")))
    }

    @Test
    fun `any 5xx status code maps to a server error`() {
        assertEquals(LoadingError.SERVER, LoadingError.from(RuntimeException("HTTP 503")))
    }

    @Test
    fun `403 maps to the unauthorized error`() {
        assertEquals(LoadingError.UNAUTHORIZED, LoadingError.from(RuntimeException("status: 403")))
    }

    @Test
    fun `self-referential cause chains terminate`() {
        val looping =
            object : RuntimeException("loop") {
                override val cause: Throwable get() = this
            }
        assertEquals(LoadingError.UNKNOWN, LoadingError.from(looping))
    }
}

class LoadingStateHelpersTest {
    @Test
    fun `map transforms only loaded data`() {
        assertEquals(LoadingState.Loaded(4), LoadingState.Loaded(2).map { it * 2 })
        assertEquals(LoadingState.Loading, (LoadingState.Loading as LoadingState<Int>).map { it * 2 })
        val failed = LoadingState.Failed(LoadingError.UNKNOWN)
        assertEquals(failed, (failed as LoadingState<Int>).map { it * 2 })
    }

    @Test
    fun `isLoaded and isFailed reflect the state`() {
        assertTrue(LoadingState.Loaded(1).isLoaded)
        assertTrue(LoadingState.Failed(LoadingError.UNKNOWN).isFailed)
        assertFalse(LoadingState.Idle.isLoaded)
    }

    @Test
    fun `loadingStateOf wraps success and classifies failure`() =
        kotlinx.coroutines.test.runTest {
            assertEquals(LoadingState.Loaded("ok"), loadingStateOf { "ok" })
            assertEquals(
                LoadingState.Failed(LoadingError.TIMEOUT),
                loadingStateOf<String> { throw java.net.SocketTimeoutException() },
            )
        }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `loadingStateOf rethrows cancellation`() =
        kotlinx.coroutines.test.runTest {
            loadingStateOf<String> { throw kotlinx.coroutines.CancellationException("cancelled") }
        }
}
