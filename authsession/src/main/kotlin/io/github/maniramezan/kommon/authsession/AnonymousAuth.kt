package io.github.maniramezan.kommon.authsession

/**
 * Authentication operations for anonymous sessions.
 */
public interface AnonymousAuth {
    /** Signs in anonymously without persistent credentials. */
    public suspend fun signInAnonymously(): Result<AuthUser>
}
