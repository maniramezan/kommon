package io.github.maniramezan.kommon.authsession

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic authentication repository. Bridge this to Firebase Auth, Supabase Auth, or
 * any other auth backend.
 */
public interface AuthRepository {
    public val currentUser: AuthUser?
    public val isAuthenticated: Boolean
    public val isAnonymous: Boolean

    public fun authStateFlow(): Flow<AuthUser?>

    public suspend fun signInAnonymously(): Result<AuthUser>

    public suspend fun signInWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser>

    public suspend fun createAccountWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser>

    public suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    public suspend fun signInWithApple(activity: Activity): Result<AuthUser>

    public suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    public suspend fun sendSignInLinkToEmail(
        email: String,
        continueUrl: String,
    ): Result<Unit>

    public fun signOut()

    public suspend fun deleteAccount(): Result<Unit>
}
