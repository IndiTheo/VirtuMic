package com.indidevs.android.privilegehub

import io.mockk.coEvery
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [PrivilegeHub] is a singleton [object]; its registry and selection state persist across tests.
 * [PrivilegeHubTest] selects ID_NONE in setUp so tests stay isolated without touching any private
 * internals. Each test registers providers with unique IDs to avoid cross-test collisions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["com.indidevs.android.privilegehub"])
class PrivilegeHubTest {

    @Before
    fun setUp() = runTest {
        PrivilegeHub.unregisterAll()
        PrivilegeHub.select(PrivilegeProvider.ID_NONE)
        PrivilegeHub.setPolicy(DefaultPrivilegePolicy())
    }

    @After
    fun tearDown() = runTest {
        PrivilegeHub.setPolicy(DefaultPrivilegePolicy())
        PrivilegeHub.unregisterAll()
    }

    @Test
    fun `initialize resolves and selects policy when no preferred provider is given`() = runTest {
        val spy = spyk(DefaultPrivilegePolicy())
        PrivilegeHub.setPolicy(spy)
        coEvery { spy.resolve(null, any()) } returns "MOCKED-ID"

        PrivilegeHub.initialize(null)

        verify(exactly = 1) { spy.resolve(null, any()) }
        assertEquals("MOCKED-ID", PrivilegeHub.state.value.selectedId)
    }

    @Test
    fun `initialize selects a preferred provider and skips resolve`() = runTest {
        val spy = spyk(DefaultPrivilegePolicy())
        PrivilegeHub.setPolicy(spy)

        PrivilegeHub.register(TestProvider("EXPLICIT", throwOnAcquire = true))
        PrivilegeHub.initialize("EXPLICIT")

        verify(exactly = 0) { spy.resolve(any(), any()) }
        assertEquals("EXPLICIT", PrivilegeHub.state.value.selectedId)
    }

    @Test
    fun `initialize calls resolve with the preferred id when it is NOT registered`() = runTest {
        val spy = spyk(DefaultPrivilegePolicy())
        PrivilegeHub.setPolicy(spy)
        coEvery { spy.resolve("UNKNOWN", any()) } returns "RESOLVED-FALLBACK-ID"

        PrivilegeHub.initialize("UNKNOWN")

        verify(exactly = 1) { spy.resolve("UNKNOWN", any()) }
        assertEquals("RESOLVED-FALLBACK-ID", PrivilegeHub.state.value.selectedId)
    }

    @Test
    fun `initialize calls resolve with null when a provider is unavailable`() = runTest {
        val spy = spyk(DefaultPrivilegePolicy())
        PrivilegeHub.setPolicy(spy)
        coEvery { spy.resolve(null, any()) } returns "RESOLVED"

        PrivilegeHub.register(
            TestProvider("UNAVAILABLE", isAvailable = false, throwOnAcquire = true)
        )
        PrivilegeHub.initialize(null)

        verify(exactly = 1) { spy.resolve(null, any()) }
        assertEquals("RESOLVED", PrivilegeHub.state.value.selectedId)
    }

    @Test
    fun `unregister removes provider and resets state when it was selected`() = runTest {
        val provider = TestProvider("UNREGISTER-ME", isImmediate = true, throwOnAcquire = true)
        PrivilegeHub.register(provider)

        PrivilegeHub.initialize(null)
        assertEquals("UNREGISTER-ME", PrivilegeHub.state.value.selectedId)

        val removed = PrivilegeHub.unregister("UNREGISTER-ME")
        assertTrue(removed)

        // Provider is gone from the registry
        assertEquals(0, PrivilegeHub.getProviders().size)
        // Selection is reset to ID_NONE since the selected provider was removed
        assertEquals(PrivilegeProvider.ID_NONE, PrivilegeHub.state.value.selectedId)
        assertFalse(PrivilegeHub.state.value.isAlive)
        assertNull(PrivilegeHub.getHandleOrNull<Privilege>())
    }

    @Test
    fun `unregister returns false when provider is not registered`() = runTest {
        val removed = PrivilegeHub.unregister("DOES-NOT-EXIST")
        assertFalse(removed)
    }

    @Test
    fun `unregister removes provider but keeps selection when a different provider is selected`() =
        runTest {
            val removedProvider = TestProvider("REMOVED", throwOnAcquire = true)
            val keptProvider = TestProvider("KEPT", throwOnAcquire = true)
            PrivilegeHub.register(removedProvider, keptProvider)

            PrivilegeHub.initialize("KEPT")
            assertEquals("KEPT", PrivilegeHub.state.value.selectedId)

            val removed = PrivilegeHub.unregister("REMOVED")
            assertTrue(removed)

            // Selection is untouched since a different provider was selected
            assertEquals("KEPT", PrivilegeHub.state.value.selectedId)
            assertEquals(1, PrivilegeHub.getProviders().size)
        }

    @Test
    fun `unregisterAll removes all providers and resets state`() = runTest {
        PrivilegeHub.register(
            TestProvider("A", throwOnAcquire = true),
            TestProvider("B", throwOnAcquire = true)
        )

        val removed = PrivilegeHub.unregisterAll()

        assertEquals(2, removed)
        assertEquals(0, PrivilegeHub.getProviders().size)
        assertEquals(PrivilegeProvider.ID_NONE, PrivilegeHub.state.value.selectedId)
        assertFalse(PrivilegeHub.state.value.isAlive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `register throws IllegalArgumentException when provider id is ID_NONE`() {
        PrivilegeHub.register(TestProvider(PrivilegeProvider.ID_NONE, throwOnAcquire = true))
    }

    @Test
    fun `provide acquires selected provider and updates state on success`() = runTest {
        val fakePrivilege = FakePrivilege()
        val provider = TestProvider("SUCCESS-MODE", result = Result.success(fakePrivilege))
        PrivilegeHub.register(provider)
        PrivilegeHub.select("SUCCESS-MODE")
        assertEquals("SUCCESS-MODE", PrivilegeHub.state.value.selectedId)

        val result = PrivilegeHub.provide()

        assertTrue(result.isSuccess)
        assertEquals(fakePrivilege, result.getOrNull())
        assertEquals(PrivilegeStatus.PROVIDED, PrivilegeHub.state.value.status)
        assertTrue(PrivilegeHub.state.value.isAlive)
        assertEquals("SUCCESS-MODE", PrivilegeHub.state.value.selectedId)
        assertEquals(fakePrivilege, PrivilegeHub.getHandleOrNull<FakePrivilege>())
    }

    @Test
    fun `provide sets state to NOT_SUPPLIED on acquisition failure`() = runTest {
        val exception = RuntimeException("Acquire failed")
        val provider = TestProvider("FAIL-MODE", result = Result.failure(exception))
        PrivilegeHub.register(provider)
        PrivilegeHub.select("FAIL-MODE")
        assertEquals("FAIL-MODE", PrivilegeHub.state.value.selectedId)

        val result = PrivilegeHub.provide()

        assertTrue(result.isFailure)
        assertEquals(PrivilegeStatus.NOT_SUPPLIED, PrivilegeHub.state.value.status)
        assertFalse(PrivilegeHub.state.value.isAlive)
        assertEquals("FAIL-MODE", PrivilegeHub.state.value.selectedId)
        assertNull(PrivilegeHub.getHandleOrNull<Privilege>())
    }

    @Test
    fun `provide returns failure when no provider is registered for selected id`() = runTest {
        PrivilegeHub.select("UNREGISTERED-MODE")
        assertEquals("UNREGISTERED-MODE", PrivilegeHub.state.value.selectedId)

        val result = PrivilegeHub.provide()

        assertTrue(result.isFailure)
        assertEquals(PrivilegeStatus.NOT_SUPPLIED, PrivilegeHub.state.value.status)
        assertEquals("UNREGISTERED-MODE", PrivilegeHub.state.value.selectedId)
    }

    @Test
    fun `provide returns existing handle when already provided unless force is true`() = runTest {
        val provider = TestProvider("REUSE-MODE")
        PrivilegeHub.register(provider)
        PrivilegeHub.select("REUSE-MODE")
        assertEquals("REUSE-MODE", PrivilegeHub.state.value.selectedId)

        PrivilegeHub.provide()
        assertEquals(1, provider.acquireCount)
        assertEquals("REUSE-MODE", PrivilegeHub.state.value.selectedId)

        // Second call without force reuses active handle
        PrivilegeHub.provide(force = false)
        assertEquals(1, provider.acquireCount)
        assertEquals("REUSE-MODE", PrivilegeHub.state.value.selectedId)

        // Call with force = true triggers re-acquisition
        PrivilegeHub.provide(force = true)
        assertEquals(2, provider.acquireCount)
        assertEquals("REUSE-MODE", PrivilegeHub.state.value.selectedId)
    }

    @Test
    fun `release clears handle and releases active privilege`() = runTest {
        val fakePrivilege = FakePrivilege()
        val provider = TestProvider("RELEASE-MODE", result = Result.success(fakePrivilege))
        PrivilegeHub.register(provider)
        PrivilegeHub.select("RELEASE-MODE")
        assertEquals("RELEASE-MODE", PrivilegeHub.state.value.selectedId)
        PrivilegeHub.provide()

        PrivilegeHub.release()

        assertTrue(fakePrivilege.released)
        assertEquals(PrivilegeStatus.NOT_SUPPLIED, PrivilegeHub.state.value.status)
        assertFalse(PrivilegeHub.state.value.isAlive)
        assertEquals("RELEASE-MODE", PrivilegeHub.state.value.selectedId)
        assertNull(PrivilegeHub.getHandleOrNull<Privilege>())
    }

    @Test
    fun `checkHealth returns true when active handle is alive and false when dead or missing`() =
        runTest {
            assertFalse(PrivilegeHub.checkHealth()) // No handle

            val fakePrivilege = FakePrivilege(initialAlive = true)
            val provider = TestProvider("HEALTH-MODE", result = Result.success(fakePrivilege))
            PrivilegeHub.register(provider)
            PrivilegeHub.select("HEALTH-MODE")
            assertEquals("HEALTH-MODE", PrivilegeHub.state.value.selectedId)
            PrivilegeHub.provide()

            assertTrue(PrivilegeHub.checkHealth())
            assertEquals("HEALTH-MODE", PrivilegeHub.state.value.selectedId)

            fakePrivilege.alive = false
            assertFalse(PrivilegeHub.checkHealth())
            assertFalse(PrivilegeHub.state.value.isAlive)
            assertEquals("HEALTH-MODE", PrivilegeHub.state.value.selectedId)
        }
}

class DefaultPrivilegePolicyTest {

    private val policy = DefaultPrivilegePolicy()

    @Test
    fun `resolve returns ID_NONE when no providers are available`() {
        assertEquals(
            PrivilegeProvider.ID_NONE,
            policy.resolve(PrivilegeProvider.ID_NONE, emptyList())
        )
    }

    @Test
    fun `resolve returns ID_NONE when preferred id is not registered`() {
        val providers = listOf(TestProvider("A", throwOnAcquire = true))
        assertEquals(PrivilegeProvider.ID_NONE, policy.resolve("UNKNOWN", providers))
    }

    @Test
    fun `resolve returns the preferred id when it is available`() {
        val providers = listOf(
            TestProvider("A", throwOnAcquire = true),
            TestProvider("B", throwOnAcquire = true)
        )
        assertEquals("B", policy.resolve("B", providers))
    }

    @Test
    fun `resolve skips a preferred id that is unavailable`() {
        val providers = listOf(
            TestProvider("A", isAvailable = false, throwOnAcquire = true),
            TestProvider("B", isAvailable = true, throwOnAcquire = true)
        )
        assertEquals(PrivilegeProvider.ID_NONE, policy.resolve("A", providers))
    }

    @Test
    fun `resolve auto-selects an isImmediate provider when no preference is given`() {
        val providers = listOf(
            TestProvider("A", priority = 100, throwOnAcquire = true),
            TestProvider("B", isImmediate = true, priority = 1, throwOnAcquire = true)
        )
        assertEquals("B", policy.resolve(PrivilegeProvider.ID_NONE, providers))
    }

    @Test
    fun `resolve picks the highest priority provider on no preference and no immediate`() {
        val providers = listOf(
            TestProvider("A", priority = 10, throwOnAcquire = true),
            TestProvider("B", priority = 50, throwOnAcquire = true),
            TestProvider("C", priority = 5, throwOnAcquire = true)
        )
        assertEquals("B", policy.resolve(PrivilegeProvider.ID_NONE, providers))
    }

    @Test
    fun `resolve ignores unavailable providers for priority-based selection`() {
        val providers = listOf(
            TestProvider("A", isAvailable = false, priority = 100, throwOnAcquire = true),
            TestProvider("B", isAvailable = true, priority = 10, throwOnAcquire = true)
        )
        assertEquals("B", policy.resolve(PrivilegeProvider.ID_NONE, providers))
    }
}

private class FakePrivilege(initialAlive: Boolean = true) : Privilege {
    var alive: Boolean = initialAlive
    var released: Boolean = false

    override suspend fun release() {
        released = true
        alive = false
    }

    override suspend fun isAlive(): Boolean = alive
}

private class TestProvider(
    override val id: String,
    private val result: Result<Privilege> = Result.success(FakePrivilege()),
    override val isAvailable: Boolean = true,
    override val isImmediate: Boolean = false,
    override val priority: Int = 0,
    private val throwOnAcquire: Boolean = false
) : PrivilegeProvider<Privilege> {
    override val name: String = "Test $id"
    var acquireCount = 0

    override suspend fun onAcquire(timeoutMs: Long): Result<Privilege> {
        if (throwOnAcquire) {
            throw AssertionError("should not be acquired in these tests")
        }
        acquireCount++
        return result
    }
}
