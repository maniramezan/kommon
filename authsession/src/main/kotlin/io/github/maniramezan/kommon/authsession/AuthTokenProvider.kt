package io.github.maniramezan.kommon.authsession

/**
 * Supplies the bearer token attached to every outgoing API request.
 *
 * The implementation returns the current provider's ID/session token — including for anonymous
 * (guest) users, who typically also hold a valid token — or `null` when no user is signed in, in
 * which case no `Authorization` header should be sent.
 */
public interface AuthTokenProvider {
    /**
     * @param forceRefresh when true, bypasses any cached token and mints a fresh one.
     * @return the token to send as `Bearer <token>`, or `null` if unavailable.
     */
    public suspend fun idToken(forceRefresh: Boolean = false): String?
}
