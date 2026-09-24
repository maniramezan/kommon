package io.github.maniramezan.kommon.testing

import android.app.Activity
import io.github.maniramezan.kommon.authsession.AuthRepository
import io.github.maniramezan.kommon.authsession.AuthSessionHint
import io.github.maniramezan.kommon.authsession.AuthSessionStore
import io.github.maniramezan.kommon.authsession.AuthTokenProvider
import io.github.maniramezan.kommon.authsession.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AuthRepository] fake. Every sign-in succeeds with a deterministic user unless
 * [nextFailure] is set, in which case the next call fails with it (once). Sign-in state is
 * observable through [authStateFlow], so it drives an `AuthSessionInitializer` end to end.
 */
public class FakeAuthRepository(
    initialUser: AuthUser? = null,
) : AuthRepository {
    private val user = MutableStateFlow(initialUser)

    /** When non-null, the next suspending call returns `Result.failure(nextFailure)` and clears it. */
    public var nextFailure: Throwable? = null

    public val passwordResetEmails: MutableList<String> = mutableListOf()
    public val signInLinkEmails: MutableList<String> = mutableListOf()
    public var signOutCount: Int = 0
        private set

    override val currentUser: AuthUser? get() = user.value
    override val isAuthenticated: Boolean get() = user.value != null
    override val isAnonymous: Boolean get() = user.value?.isAnonymous == true

    override fun authStateFlow(): StateFlow<AuthUser?> = user.asStateFlow()

    /** Sets the signed-in user directly (e.g. to simulate a provider-side session change). */
    public fun emit(authUser: AuthUser?) {
        user.value = authUser
    }

    override suspend fun signInAnonymously(): Result<AuthUser> = signIn(AuthUser(uid = ANONYMOUS_UID, isAnonymous = true))

    override suspend fun signInWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser> = signIn(AuthUser(uid = "email:$email", isAnonymous = false, email = email))

    override suspend fun createAccountWithEmailAndPassword(
        email: String,
        password: String,
    ): Result<AuthUser> = signIn(AuthUser(uid = "email:$email", isAnonymous = false, email = email))

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> =
        signIn(AuthUser(uid = "google:$idToken", isAnonymous = false))

    override suspend fun signInWithApple(activity: Activity): Result<AuthUser> = signIn(AuthUser(uid = "apple", isAnonymous = false))

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = attempt { passwordResetEmails += email }

    override suspend fun sendSignInLinkToEmail(
        email: String,
        continueUrl: String,
    ): Result<Unit> = attempt { signInLinkEmails += email }

    override fun signOut() {
        signOutCount += 1
        user.value = null
    }

    override suspend fun deleteAccount(): Result<Unit> = attempt { user.value = null }

    private fun signIn(authUser: AuthUser): Result<AuthUser> = attempt { authUser.also { user.value = it } }

    private inline fun <T> attempt(block: () -> T): Result<T> {
        nextFailure?.let {
            nextFailure = null
            return Result.failure(it)
        }
        return Result.success(block())
    }

    public companion object {
        public const val ANONYMOUS_UID: String = "anonymous"
    }
}

/** [AuthTokenProvider] fake returning [token]; records every call's `forceRefresh` flag. */
public class FakeAuthTokenProvider(
    public var token: String? = "test-token",
) : AuthTokenProvider {
    public val requests: MutableList<Boolean> = mutableListOf()

    override suspend fun idToken(forceRefresh: Boolean): String? {
        requests += forceRefresh
        return token
    }
}

/** In-memory [AuthSessionStore]. */
public class InMemoryAuthSessionStore(
    private var hint: AuthSessionHint? = null,
) : AuthSessionStore {
    override fun record(hint: AuthSessionHint) {
        this.hint = hint
    }

    override fun read(): AuthSessionHint? = hint

    override fun clear() {
        hint = null
    }
}
