package io.github.maniramezan.kommon.authsession

import io.github.maniramezan.kommon.foundation.KommonLogger
import io.github.maniramezan.kommon.foundation.NoOpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Warms the auth session at cold start: mints/refreshes a token before the first API request can
 * race an unauthenticated call, then keeps [sessionStore] updated as [AuthRepository.authStateFlow]
 * changes. Call [start] once, early in app startup (this class owns its own background scope).
 */
public class AuthSessionInitializer(
    private val authRepository: AuthRepository,
    private val authTokenProvider: AuthTokenProvider,
    private val sessionStore: AuthSessionStore,
    private val logger: KommonLogger = NoOpLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    public fun start() {
        scope.launch {
            warmUp()
            observeAuthChanges()
        }
    }

    internal suspend fun warmUp() {
        val storedHint = sessionStore.read()
        val currentUser = authRepository.currentUser
        val isReturningSignedIn = currentUser?.let { !it.isAnonymous } == true
        logger.info(
            TAG,
            "warmUp: hasCurrentUser=${currentUser != null}, " +
                "isSignedIn=$isReturningSignedIn, storedHasAccount=${storedHint?.hasAccount}",
        )

        val token = authTokenProvider.idToken(forceRefresh = isReturningSignedIn)
        if (token.isNullOrBlank()) {
            logger.error(TAG, "warmUp: no ID token after warm-up; first requests may be unauthenticated")
        } else {
            logger.info(TAG, "warmUp: session ready")
        }
        recordHint(authRepository.currentUser)
    }

    private suspend fun observeAuthChanges() {
        authRepository.authStateFlow().collect(::recordHint)
    }

    private fun recordHint(user: AuthUser?) {
        sessionStore.record(AuthSessionHint(hasAccount = user?.let { !it.isAnonymous } == true))
    }

    private companion object {
        const val TAG = "AuthSessionInitializer"
    }
}
