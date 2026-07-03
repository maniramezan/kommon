package io.github.maniramezan.kommon.foundation

/**
 * Generic loading state for async data-fetching operations backed by a [kotlinx.coroutines.flow.StateFlow].
 *
 * Matches the classic iOS `LoadingState` enum pattern for consistent state management across
 * platforms.
 */
public sealed class LoadingState<out T> {
    public data object Idle : LoadingState<Nothing>()

    public data object Loading : LoadingState<Nothing>()

    public data class Loaded<T>(
        val data: T,
    ) : LoadingState<T>()

    public data class Failed(
        val error: LoadingError,
    ) : LoadingState<Nothing>()

    public val isLoading: Boolean
        get() = this is Loading

    public val value: T?
        get() = (this as? Loaded)?.data

    public val errorValue: LoadingError?
        get() = (this as? Failed)?.error
}

/**
 * Error wrapper for loading failures with user-friendly messages.
 *
 * [from] classifies a [Throwable] into one of a small set of user-facing messages by inspecting
 * common network exception types and HTTP-status-shaped exception messages (e.g. Ktor's
 * `Client request(...) invalid: 404 Not Found`). The exact status-code substring matching is a
 * pragmatic heuristic that works across HTTP client libraries without adding a hard dependency on
 * any one of them.
 */
public data class LoadingError(
    val message: String,
    val isRetryable: Boolean = true,
) {
    public companion object {
        public fun from(throwable: Throwable): LoadingError {
            val rootCause = findNetworkCause(throwable) ?: throwable
            return when (rootCause) {
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                ->
                    LoadingError(
                        message = "Unable to connect. Please check your internet connection and try again.",
                        isRetryable = true,
                    )
                is java.net.SocketTimeoutException ->
                    LoadingError(
                        message = "The request timed out. Please try again.",
                        isRetryable = true,
                    )
                else -> fromHttpStatus(throwable)
            }
        }

        private fun findNetworkCause(throwable: Throwable): Throwable? {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is java.net.UnknownHostException ||
                    current is java.net.ConnectException ||
                    current is java.net.SocketTimeoutException
                ) {
                    return current
                }
                current = current.cause
            }
            return null
        }

        private fun fromHttpStatus(throwable: Throwable): LoadingError {
            val message = throwable.message ?: ""
            return when {
                message.contains("404") || message.contains("Not Found") ->
                    LoadingError(
                        message = "The content you're looking for could not be found.",
                        isRetryable = false,
                    )
                message.contains("401") ||
                    message.contains("403") ||
                    message.contains("Unauthorized") ||
                    message.contains("Forbidden") ->
                    LoadingError(
                        message = "You don't have access to this content. Please sign in and try again.",
                        isRetryable = false,
                    )
                message.contains("429") || message.contains("Too Many") ->
                    LoadingError(
                        message = "Too many requests. Please wait a moment and try again.",
                        isRetryable = true,
                    )
                message.contains("5") && message.contains("Server") ->
                    LoadingError(
                        message = "Something went wrong on our end. Please try again later.",
                        isRetryable = true,
                    )
                else ->
                    LoadingError(
                        message = "Something went wrong. Please try again.",
                        isRetryable = true,
                    )
            }
        }
    }
}
