package io.github.maniramezan.kommon.authsession

/**
 * Authentication operations using email and password, or email sign-in links.
 */
public interface EmailPasswordAuth {
    /** Signs in with an existing email and password. */
    public suspend fun signInWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser>

    /** Creates a new account with email and password. */
    public suspend fun createAccountWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser>

    /** Sends a password reset email to [email]. */
    public suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Sends a passwordless sign-in link to [email]. */
    public suspend fun sendSignInLinkToEmail(
        email: String,
        continueUrl: String,
    ): Result<Unit>
}
