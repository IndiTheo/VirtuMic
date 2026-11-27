package com.indidevs.android.privilegehub.providers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.indidevs.android.privilegehub.HintType
import com.indidevs.android.shellom.ShellExecutor
import com.indidevs.android.shellom.ShellProvider
import com.indidevs.android.shellom.ShellStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ShizukuPrivilegeProviderTest {

    private lateinit var context: Context
    private val failingExecutor = ShellExecutor {
        Result.failure(IllegalStateException("Shizuku binder is unavailable."))
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun TestScope.createFailingProvider(): ShizukuPrivilegeProvider {
        return createElevatingProvider(failingShellProviderFactory)
    }

    /**
     * A shell provider whose elevation attempt always fails.
     */
    private val failingShellProviderFactory: (CoroutineScope, ShellExecutor) -> ShellProvider =
        { _, _ ->
            FakeShellProvider(onAwait = {
                throw IllegalStateException("Shizuku binder is unavailable (mock).")
            })
        }

    private fun TestScope.createElevatingProvider(
        shellProviderFactory: (CoroutineScope, ShellExecutor) -> ShellProvider
    ): ShizukuPrivilegeProvider {
        return ShizukuPrivilegeProvider(
            context = context,
            requiredPermissions = REQUIRED_PERMISSIONS,
            priority = PRIORITY,
            scope = backgroundScope,
            executor = ShellExecutor { Result.success(Unit) },
            shellProviderFactory = shellProviderFactory,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
    }

    @Test
    fun `ShizukuPrivilege handle isAlive returns true`() = runTest {
        val privilege = ShizukuPrivilege(onRelease = {})
        assertTrue(privilege.isAlive())
    }

    @Test
    fun `ShizukuPrivilege release invokes onRelease callback`() = runTest {
        var released = false
        val privilege = ShizukuPrivilege(onRelease = { released = true })

        privilege.release()
        assertTrue(released)
    }

    @Test
    fun `hints initial state includes NOT_CONNECTED when Shizuku is not running`() = runTest {
        val provider = createFailingProvider()

        val hints = withTimeout(5_000) { provider.hints.first { it.isNotEmpty() } }
        assertTrue(
            "Expected NOT_CONNECTED hint when not acquired",
            hints.any { it.id == ShizukuPrivilegeProvider.ShizukuHint.NOT_CONNECTED }
        )
    }

    @Test
    fun `successful acquisition returns a privilege and reports ELEVATED`() = runTest {
        val fake = FakeShellProvider()
        fake.onAwait = {
            fake.markReady()
            context
        }
        val provider = createElevatingProvider { _, _ -> fake }

        val result = provider.acquire()

        assertTrue("Acquisition should succeed: ${result.exceptionOrNull()}", result.isSuccess)

        withTimeout(5_000) {
            provider.hints.first { set ->
                set.any { it.id == ShizukuPrivilegeProvider.ShizukuHint.ELEVATED }
            }
        }
    }

    @Test
    fun `onAcquire returns failure and sets ERROR hint when Shizuku executor fails`() = runTest {
        val provider = createFailingProvider()

        val result = provider.acquire()

        assertTrue("Acquisition should fail when executor fails", result.isFailure)

        val updatedHints = withTimeout(5_000) {
            provider.hints.first { hints ->
                hints.any { it.id == ShizukuPrivilegeProvider.ShizukuHint.ERROR }
            }
        }
        val firstHint = updatedHints.first { it.id == ShizukuPrivilegeProvider.ShizukuHint.ERROR }
        assertEquals(HintType.ERROR, firstHint.type)
    }

    @Test
    fun `cancelling the caller leaves the provider unharmed`() = runTest {
        val elevationStarted = CompletableDeferred<Unit>()
        val releaseElevation = CompletableDeferred<Unit>()
        val provider = createElevatingProvider { _, _ ->
            FakeShellProvider(onAwait = {
                elevationStarted.complete(Unit)
                releaseElevation.await()
                context
            })
        }

        // An isolated job so cancelling the caller does not cancel the test body.
        val callerJob = Job()
        val acquireJob = CoroutineScope(callerJob).launch {
            provider.acquire(timeoutMs = 10_000)
        }

        withContext(Dispatchers.IO) { elevationStarted.await() }

        // Wait for the hints flow to produce its first value, so the assertions below
        // read a settled snapshot instead of the initial empty set.
        withTimeout(5_000) { provider.hints.first { it.isNotEmpty() } }

        callerJob.cancel()

        // The hints flow must stay alive, and a cancelled caller must not be recorded
        // as a provisioning failure.
        val hintsAfterCancel = provider.hints.value
        assertTrue(
            "The hints flow must stay alive after the caller is cancelled",
            hintsAfterCancel.isNotEmpty()
        )
        assertTrue(
            "Cancelling the caller must not raise an ERROR hint",
            hintsAfterCancel.none { it.id == ShizukuPrivilegeProvider.ShizukuHint.ERROR }
        )
        assertTrue(
            "The provider must not report ELEVATED before the elevation completes",
            hintsAfterCancel.none { it.id == ShizukuPrivilegeProvider.ShizukuHint.ELEVATED }
        )

        // Let the elevation finish so the acquisition and the test can complete.
        releaseElevation.complete(Unit)
        acquireJob.join()
    }

    @Test
    fun `the elevation is sticky across acquisitions and releases`() = runTest {
        // Releasing the handle does not un-elevate the process, so the ELEVATED hint
        // stays and later acquisitions must not run the elevation again.
        val fake = FakeShellProvider()
        fake.onAwait = {
            fake.markReady()
            context
        }
        val provider = createElevatingProvider { _, _ -> fake }

        val privilege = provider.acquire().getOrThrow()

        withTimeout(5_000) {
            provider.hints.first { set ->
                set.any { it.id == ShizukuPrivilegeProvider.ShizukuHint.ELEVATED }
            }
        }

        // A second acquisition, without a release, must also reuse the elevation.
        assertTrue("A second acquisition must succeed", provider.acquire().isSuccess)

        privilege.release()

        val hintsAfterRelease = provider.hints.value
        assertTrue(
            "Release must not clear the sticky ELEVATED hint",
            hintsAfterRelease.any { it.id == ShizukuPrivilegeProvider.ShizukuHint.ELEVATED }
        )

        // And so must an acquisition after a release.
        assertTrue("An acquisition after a release must succeed", provider.acquire().isSuccess)

        assertEquals(
            "The elevation must only run once, no matter how often the privilege is acquired",
            1,
            fake.elevationAttempts
        )
    }

    @Test
    fun `a failed acquisition returns failure and clears the elevated state`() = runTest {
        // The elevation attempt fails, so the provider must report the failure and must
        // not leave an ELEVATED hint behind.
        val fake = FakeShellProvider()
        fake.onAwait = {
            fake.markReady()
            throw IllegalStateException("elevation failed")
        }
        val provider = createElevatingProvider { _, _ -> fake }

        val result = provider.acquire(timeoutMs = 10_000)

        assertTrue("A failed elevation must be reported as a failure", result.isFailure)

        val hints = withTimeout(5_000) {
            provider.hints.first { set ->
                set.any { it.id == ShizukuPrivilegeProvider.ShizukuHint.ERROR }
            }
        }
        assertTrue(
            "A failed acquisition must not leave an ELEVATED hint",
            hints.none { it.id == ShizukuPrivilegeProvider.ShizukuHint.ELEVATED }
        )
    }

    @Test
    fun `acquisition times out when elevation never completes`() = runTest {
        val provider = createElevatingProvider { _, _ ->
            FakeShellProvider(onAwait = { awaitCancellation() })
        }

        val result = provider.acquire(timeoutMs = 100)

        assertTrue("Acquisition should time out", result.isFailure)
        assertTrue(result.exceptionOrNull() is Exception)
    }

    companion object {
        private val REQUIRED_PERMISSIONS = listOf("android.permission.MODIFY_AUDIO_ROUTING")
        private const val PRIORITY = 20
    }
}

/**
 * A [ShellProvider] stand-in that lets tests drive the elevation routes
 * (success, failure, hang) without a real instrumentation process.
 *
 * The test owns the behaviour: [onAwait] decides what the elevation attempt does, and
 * [markReady] publishes the READY status the provider observes.
 */
private class FakeShellProvider(
    var onAwait: suspend () -> Context = { error("onAwait not configured") }
) : ShellProvider {

    private val _status = MutableStateFlow(ShellStatus.IDLE)
    override val status: StateFlow<ShellStatus> = _status

    /** How many times the elevation was actually attempted. */
    var elevationAttempts = 0
        private set

    override suspend fun awaitContext(): Context {
        elevationAttempts++
        _status.value = ShellStatus.ELEVATING
        return try {
            onAwait()
        } finally {
            if (_status.value == ShellStatus.ELEVATING) {
                _status.value = ShellStatus.ERROR
            }
        }
    }

    override fun reset() {
        _status.value = ShellStatus.IDLE
    }

    /** Publishes the READY status, which the provider observes to set the sticky flag. */
    fun markReady() {
        _status.value = ShellStatus.READY
    }
}
