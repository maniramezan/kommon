package io.github.maniramezan.kommon.authsession

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Warms the auth session at cold start: mints/refreshes a token before the first API request can
 * race an unauthenticated call, then keeps [sessionStore] updated as [AuthStateProvider.authStateFlow]
 * changes. Call [start] early in app startup and [stop] when its owning app scope is torn down.
 *
 * Failures from the token provider or the auth-state flow are logged and contained: they never
 * escape into [scope] (where an uncaught exception would crash the process). A failed warm-up
 * still proceeds to observing auth changes, so the session hint keeps tracking the real state.
 *
 * [warmUpState] reports warm-up progress. An HTTP layer can call [awaitWarmUp] (with its own
 * timeout) before sending the first request, instead of racing the token refresh:
 *
 * ```kotlin
 * withTimeoutOrNull(2.seconds) { initializer.awaitWarmUp() }
 * ```
 */
public class AuthSessionInitializer(
    private val authRepository: AuthStateProvider,
    private val authTokenProvider: AuthTokenProvider,
    private val sessionStore: AuthSessionStore,
    private val logger: KommonLogger = NoOpLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var observationJob: Job? = null
    private val _warmUpState = MutableStateFlow(AuthWarmUpState.IDLE)

    /**
     * Warm-up progress for the current [start]. It moves to [AuthWarmUpState.WARMING] when [start]
     * is called and to a finished state when warm-up completes. A warm-up interrupted by [stop] or
     * by cancellation of [scope] returns to [AuthWarmUpState.IDLE]; a finished state is kept.
     */
    public val warmUpState: StateFlow<AuthWarmUpState> = _warmUpState.asStateFlow()

    /**
     * Suspends until warm-up has finished and returns its outcome. If [start] has not been called
     * yet, this waits for it. Callers own the timeout.
     */
    public suspend fun awaitWarmUp(): AuthWarmUpState = warmUpState.first { it.isFinished }

    /** Starts session warming and observation. Repeated calls while running are ignored. */
    @Synchronized
    public fun start() {
        if (observationJob?.isActive == true) return
        _warmUpState.value = AuthWarmUpState.WARMING
        observationJob =
            scope.launch {
                try {
                    val warmed = runContained("warmUp") { warmUp() }
                    _warmUpState.value = warmed ?: AuthWarmUpState.FAILED
                } catch (cancellation: CancellationException) {
                    _warmUpState.compareAndSet(AuthWarmUpState.WARMING, AuthWarmUpState.IDLE)
                    throw cancellation
                }
                runContained("observeAuthChanges") { observeAuthChanges() }
            }
    }

    /** Stops this initializer's work without cancelling the caller-owned [scope]. */
    @Synchronized
    public fun stop() {
        observationJob?.cancel()
        observationJob = null
        _warmUpState.compareAndSet(AuthWarmUpState.WARMING, AuthWarmUpState.IDLE)
    }

    /** Mints or refreshes the token and records the session hint; returns the warm-up outcome. */
    internal suspend fun warmUp(): AuthWarmUpState {
        val storedHint = sessionStore.read()
        val currentUser = authRepository.currentUser
        val isReturningSignedIn = currentUser?.let { !it.isAnonymous } == true
        logSafely {
            logger.info(
                TAG,
                "warmUp: hasCurrentUser=${currentUser != null}, " +
                    "isSignedIn=$isReturningSignedIn, storedHasAccount=${storedHint?.hasAccount}",
            )
        }

        val token = authTokenProvider.idToken(forceRefresh = isReturningSignedIn)
        val outcome =
            if (token.isNullOrBlank()) {
                logSafely { logger.error(TAG, "warmUp: no ID token after warm-up; first requests may be unauthenticated") }
                AuthWarmUpState.NO_TOKEN
            } else {
                logSafely { logger.info(TAG, "warmUp: session ready") }
                AuthWarmUpState.READY
            }
        recordHint(authRepository.currentUser)
        return outcome
    }

    private suspend fun observeAuthChanges() {
        authRepository.authStateFlow().collect(::recordHint)
    }

    @Suppress("TooGenericExceptionCaught") // Contain any provider failure; see class KDoc.
    private suspend fun <T> runContained(
        stage: String,
        block: suspend () -> T,
    ): T? =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logSafely { logger.error(TAG, "$stage failed", throwable) }
            null
        }

    private inline fun logSafely(block: () -> Unit) {
        runCatching(block).onFailure { if (it is CancellationException) throw it }
    }

    private fun recordHint(user: AuthUser?) {
        sessionStore.record(AuthSessionHint(hasAccount = user?.let { !it.isAnonymous } == true))
    }

    private companion object {
        const val TAG = "AuthSessionInitializer"
    }
}
