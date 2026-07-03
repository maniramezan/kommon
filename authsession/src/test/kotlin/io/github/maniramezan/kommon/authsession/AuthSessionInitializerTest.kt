package io.github.maniramezan.kommon.authsession

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AuthSessionInitializerTest {
    private lateinit var authRepository: AuthRepository
    private lateinit var authTokenProvider: AuthTokenProvider
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var authStateFlow: MutableSharedFlow<AuthUser?>
    private lateinit var initializer: AuthSessionInitializer

    @Before
    fun setup() {
        authRepository = mockk(relaxed = true)
        authTokenProvider = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        authStateFlow = MutableSharedFlow(replay = 1)
        every { authRepository.authStateFlow() } returns authStateFlow
    }

    @Test
    fun `warmUp requests a token and records a session hint for a signed-in user`() =
        runTest {
            val user = AuthUser(uid = "u1", isAnonymous = false)
            every { authRepository.currentUser } returns user
            coEvery { authTokenProvider.idToken(forceRefresh = true) } returns "token-123"
            initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore)

            initializer.warmUp()

            coVerify { authTokenProvider.idToken(forceRefresh = true) }
            verify { sessionStore.record(AuthSessionHint(hasAccount = true)) }
        }

    @Test
    fun `warmUp does not force-refresh for an anonymous or signed-out user`() =
        runTest {
            every { authRepository.currentUser } returns null
            coEvery { authTokenProvider.idToken(forceRefresh = false) } returns "guest-token"
            initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore)

            initializer.warmUp()

            coVerify { authTokenProvider.idToken(forceRefresh = false) }
            verify { sessionStore.record(AuthSessionHint(hasAccount = false)) }
        }

    @Test
    fun `observing auth changes records a hint for every emission`() =
        runTest {
            every { authRepository.currentUser } returns null
            coEvery { authTokenProvider.idToken(any()) } returns "token"
            initializer =
                AuthSessionInitializer(
                    authRepository,
                    authTokenProvider,
                    sessionStore,
                    scope = backgroundScope,
                )

            initializer.start()
            testScheduler.runCurrent()
            authStateFlow.emit(AuthUser(uid = "u2", isAnonymous = false))
            testScheduler.runCurrent()

            verify { sessionStore.record(AuthSessionHint(hasAccount = true)) }
        }
}
