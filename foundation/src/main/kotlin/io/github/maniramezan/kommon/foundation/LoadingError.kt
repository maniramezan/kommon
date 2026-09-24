package io.github.maniramezan.kommon.foundation

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Error wrapper for loading failures with user-friendly messages.
 *
 * [from] classifies a [Throwable] into one of a small set of user-facing messages by inspecting
 * common network exception types anywhere in the cause chain, then HTTP-status-shaped exception
 * messages (e.g. Ktor's `Client request(...) invalid: 404 Not Found`). Status codes are matched as
 * whole three-digit tokens, so ids such as `order 4040` are not mistaken for a 404. The
 * message-based matching is a pragmatic heuristic that works across HTTP client libraries without
 * adding a hard dependency on any one of them.
 */
public data class LoadingError(
    val message: String,
    val isRetryable: Boolean = true,
) {
    public companion object {
        public val NO_CONNECTION: LoadingError =
            LoadingError("Unable to connect. Please check your internet connection and try again.", isRetryable = true)
        public val TIMEOUT: LoadingError = LoadingError("The request timed out. Please try again.", isRetryable = true)
        public val NOT_FOUND: LoadingError =
            LoadingError("The content you're looking for could not be found.", isRetryable = false)
        public val UNAUTHORIZED: LoadingError =
            LoadingError("You don't have access to this content. Please sign in and try again.", isRetryable = false)
        public val RATE_LIMITED: LoadingError =
            LoadingError("Too many requests. Please wait a moment and try again.", isRetryable = true)
        public val SERVER: LoadingError = LoadingError("Something went wrong on our end. Please try again later.", isRetryable = true)
        public val UNKNOWN: LoadingError = LoadingError("Something went wrong. Please try again.", isRetryable = true)

        private val STATUS_CODE = Regex("""(?<!\d)([1-5]\d{2})(?!\d)""")
        private val SERVER_PHRASES =
            listOf("Internal Server Error", "Bad Gateway", "Service Unavailable", "Gateway Timeout")

        public fun from(throwable: Throwable): LoadingError =
            when (findNetworkCause(throwable)) {
                is UnknownHostException, is ConnectException -> NO_CONNECTION
                is SocketTimeoutException -> TIMEOUT
                else -> fromHttpStatus(throwable.message.orEmpty())
            }

        private fun findNetworkCause(throwable: Throwable): Throwable? =
            generateSequence(throwable) { current -> current.cause?.takeUnless { it === current } }
                .take(MAX_CAUSE_DEPTH)
                .firstOrNull { it is UnknownHostException || it is ConnectException || it is SocketTimeoutException }

        private fun fromHttpStatus(message: String): LoadingError {
            val statusCodes = STATUS_CODE.findAll(message).map { it.value.toInt() }.toSet()
            return when {
                HTTP_NOT_FOUND in statusCodes || message.contains("Not Found", ignoreCase = true) -> NOT_FOUND
                HTTP_UNAUTHORIZED in statusCodes ||
                    HTTP_FORBIDDEN in statusCodes ||
                    message.contains("Unauthorized", ignoreCase = true) ||
                    message.contains("Forbidden", ignoreCase = true) -> UNAUTHORIZED
                HTTP_TOO_MANY_REQUESTS in statusCodes || message.contains("Too Many Requests", ignoreCase = true) -> RATE_LIMITED
                statusCodes.any { it in SERVER_ERROR_RANGE } ||
                    SERVER_PHRASES.any { message.contains(it, ignoreCase = true) } -> SERVER
                else -> UNKNOWN
            }
        }

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_CAUSE_DEPTH = 32
        private val SERVER_ERROR_RANGE = 500..599
    }
}
