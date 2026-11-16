package io.github.maniramezan.kommon.authsession

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthWarmUpStateTest {
    private lateinit var authRepository: AuthRepository
    private lateinit var authTokenProvider: AuthTokenProvider
    private lateinit var sessionStore: AuthSessionStore

    @Before
    fun setup() {
        authRepository = mockk(relaxed = true)
        authTokenProvider = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { authRepository.currentUser } returns null
        every { authRepository.authStateFlow() } returns MutableSharedFlow()
    }

    @Test
    fun `state is idle before start`() =
        runTest {
            val initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore, scope = backgroundScope)

            assertEquals(AuthWarmUpState.IDLE, initializer.warmUpState.value)
        }

    @Test
    fun `awaitWarmUp suspends until the token arrives and then reports ready`() =
        runTest {
            val token = CompletableDeferred<String?>()
            coEvery { authTokenProvider.idToken(any()) } coAnswers { token.await() }
            val initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore, scope = backgroundScope)

            initializer.start()
            assertEquals(AuthWarmUpState.WARMING, initializer.warmUpState.value)
            val awaited = async { initializer.awaitWarmUp() }
            testScheduler.runCurrent()
            assertFalse(awaited.isCompleted)

            token.complete("token-1")
            testScheduler.runCurrent()

            assertEquals(AuthWarmUpState.READY, awaited.await())
            assertEquals(AuthWarmUpState.READY, initializer.warmUpState.value)
        }

    @Test
    fun `awaitWarmUp called before start waits for the next start`() =
        runTest {
            coEvery { authTokenProvider.idToken(any()) } returns "token"
            val initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore, scope = backgroundScope)

            val awaited = async { initializer.awaitWarmUp() }
            testScheduler.runCurrent()
            assertFalse(awaited.isCompleted)

            initializer.start()
            testScheduler.runCurrent()

            assertEquals(AuthWarmUpState.READY, awaited.await())
        }

    @Test
    fun `a blank token finishes as no token`() =
        runTest {
            coEvery { authTokenProvider.idToken(any()) } returns " "
            val initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore, scope = backgroundScope)

            initializer.start()
            testScheduler.runCurrent()

            assertEquals(AuthWarmUpState.NO_TOKEN, initializer.awaitWarmUp())
        }

    @Test
    fun `a provider failure finishes as failed`() =
        runTest {
            coEvery { authTokenProvider.idToken(any()) } throws IllegalStateException("provider down")
            val initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore, scope = backgroundScope)

            initializer.start()
            testScheduler.runCurrent()

            assertEquals(AuthWarmUpState.FAILED, initializer.awaitWarmUp())
            assertTrue(initializer.warmUpState.value.isFinished)
        }

    @Test
    fun `stop during warm-up returns to idle and a finished state survives stop`() =
        runTest {
            val token = CompletableDeferred<String?>()
            coEvery { authTokenProvider.idToken(any()) } coAnswers { token.await() }
            val initializer = AuthSessionInitializer(authRepository, authTokenProvider, sessionStore, scope = backgroundScope)

            initializer.start()
            testScheduler.runCurrent()
            initializer.stop()
            testScheduler.runCurrent()
            assertEquals(AuthWarmUpState.IDLE, initializer.warmUpState.value)

            coEvery { authTokenProvider.idToken(any()) } returns "token"
            initializer.start()
            testScheduler.runCurrent()
            initializer.stop()

            assertEquals(AuthWarmUpState.READY, initializer.warmUpState.value)
        }

    @Test
    fun `isFinished is true only for terminal states`() {
        assertEquals(
            setOf(AuthWarmUpState.READY, AuthWarmUpState.NO_TOKEN, AuthWarmUpState.FAILED),
            AuthWarmUpState.entries.filter { it.isFinished }.toSet(),
        )
    }
}
