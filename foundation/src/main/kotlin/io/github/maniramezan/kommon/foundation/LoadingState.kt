package io.github.maniramezan.kommon.foundation

import kotlinx.coroutines.CancellationException

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

    public val isLoaded: Boolean
        get() = this is Loaded

    public val isFailed: Boolean
        get() = this is Failed

    public val value: T?
        get() = (this as? Loaded)?.data

    public val errorValue: LoadingError?
        get() = (this as? Failed)?.error
}

/** Transforms the [LoadingState.Loaded] payload, leaving every other state untouched. */
public inline fun <T, R> LoadingState<T>.map(transform: (T) -> R): LoadingState<R> =
    when (this) {
        is LoadingState.Loaded -> LoadingState.Loaded(transform(data))
        is LoadingState.Failed -> this
        LoadingState.Idle -> LoadingState.Idle
        LoadingState.Loading -> LoadingState.Loading
    }

/**
 * Runs [block] and wraps its outcome as [LoadingState.Loaded] or [LoadingState.Failed] (classified
 * through [LoadingError.from]). Coroutine cancellation is rethrown, never reported as a failure.
 *
 * ```kotlin
 * updateState { copy(items = LoadingState.Loading) }
 * val items = loadingStateOf { repository.fetchItems() }
 * updateState { copy(items = items) }
 * ```
 */
@Suppress("TooGenericExceptionCaught") // Converting arbitrary failures into UI state is the point.
public suspend fun <T> loadingStateOf(block: suspend () -> T): LoadingState<T> =
    try {
        LoadingState.Loaded(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        LoadingState.Failed(LoadingError.from(throwable))
    }
