package io.github.maniramezan.kommon.authsession

import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the current authentication state and account lifecycle operations.
 */
public interface AuthStateProvider {
    /** The currently authenticated user, or `null` if unauthenticated. */
    public val currentUser: AuthUser?

    /** Whether a user is currently authenticated (either anonymously or with credentials). */
    public val isAuthenticated: Boolean

    /** Whether the currently authenticated user is anonymous. */
    public val isAnonymous: Boolean

    /** Emits authentication state changes over time. */
    public fun authStateFlow(): Flow<AuthUser?>

    /** Signs out the current user and clears authentication state. */
    public fun signOut()

    /** Deletes the currently authenticated user's account. */
    public suspend fun deleteAccount(): Result<Unit>
}
