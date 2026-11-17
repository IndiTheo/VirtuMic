# Privilege Hub

**Generic privilege orchestration for power-user apps.**

Privilege Hub is a lightweight state library to manage multiple privilege providers.
It ensures only one privilege is active at a time, managing transitions and automatic resource recycling.

## Usage

### Define Privilege & Provider

```kotlin
class AdbPrivilege(val session: AdbSession) : Privilege {
    override suspend fun release() = myAdbSession.close()
    override suspend fun isAlive() = myAdbSession.isAlive()
}

object AdbProvider : PrivilegeProvider<AdbPrivilege> {
    override val id = PrivilegeIds.ADB
    override val name = "Wireless ADB"
    // isImmediate = true when the privilege is already granted (e.g. system signature module),
    // so acquire() succeeds without user interaction. Auto-selected by the default policy.
    override val isImmediate: Boolean get() = false

    override suspend fun acquire(): Result<AdbPrivilege> {
        val session = Adb.open() // Implementation specific
        return Result.success(AdbPrivilege(session))
    }
}
```

### Registration & Discovery

```kotlin
PrivilegeHub.register(AdbProvider, RootProvider)

// List available modes for UI
val modes = PrivilegeHub.getProviders()

// Optionally customize the selection policy (replaces the `DefaultPrivilegePolicy`)
PrivilegeHub.setPolicy(MyCustomPolicy())
```

> `PrivilegeProvider.ID_NONE` is reserved — registering a provider with that ID throws `IllegalArgumentException`.

### Selection & Initialization

You can either let the hub automatically select the best available mode, or manually select a specific one:

```kotlin
scope.launch {
    // Automatic selection based on policy (e.g., highest priority available).
    // Pass null (default) or omit the argument for "no preference".
    PrivilegeHub.initialize()

    // Or specify a preferred ID (will fall back to policy if unavailable or not registered)
    PrivilegeHub.initialize("ADB")

    // Manual selection (tries to use the given ID; must be registered)
    PrivilegeHub.select("ADB")

    // Or use pre-defined constants
    PrivilegeHub.select(PrivilegeIds.ADB)
}
```

If `initialize` is called without a preferred ID (or with `null`),
the `PrivilegePolicy.resolve()` decides which provider to use.

### Acquisition

`provide` is a suspending function. It triggers the acquisition of the currently selected privilege.

```kotlin
scope.launch {
    // Provide the handle and dispatch
    PrivilegeHub.provide().onSuccess { privilege ->
        when (privilege) {
            is AdbPrivilege -> privilege.session.shell("uptime")
            is RootPrivilege -> privilege.run("...")
        }
    }

    // Force re-acquisition even if a handle is already provided
    PrivilegeHub.provide(force = true)
}
```

### Policy Resolution

The default policy (`DefaultPrivilegePolicy`) resolves providers when `initialize()` is called with no preference:
1. **Explicit Preference**: Uses `preferredId` if registered and available.
2. **`isImmediate`**: Auto-selects any available provider where `isImmediate == true` (zero-interaction readiness), bypassing higher priority tiers for fast cold-starts.
3. **`priority`**: Falls back to the available provider with the highest `priority` integer.

### Manual Release

While privileges are automatically released when selecting a new mode, you can also release the current privilege manually:

```kotlin
PrivilegeHub.release()
```

### Unregistering Providers

```kotlin
// Returns true if a provider was removed, false if the ID wasn't registered
PrivilegeHub.unregister("ADB")
```

```kotlin
// Remove every registered provider and release the active handle. Returns the number of providers that were removed.
val removed = PrivilegeHub.unregisterAll()
```

### Observe State & Events

```kotlin
// Reactive state
PrivilegeHub.state.collect { state ->
    when (state.status) {
        PrivilegeStatus.PROVIDED -> println("Active: ${state.selectedId} (Alive: ${state.isAlive})")
        PrivilegeStatus.ACQUIRING -> println("Connecting...")
        else -> println("Disconnected")
    }
}

// Events
PrivilegeHub.events.collect { event ->
    when (event) {
        is PrivilegeEvent.ProvisionError -> 
            showError("Failed to acquire ${event.modeId}: ${event.throwable.message}")
        is PrivilegeEvent.PrivilegeProvided -> 
            showToast("Successfully acquired ${event.modeId}!")
    }
}

// Observe the active handle directly for pattern matching
PrivilegeHub.activePrivilege.collect { privilege ->
    when (privilege) {
        is AdbPrivilege -> println("ADB Session Active")
        is RootPrivilege -> println("Root Session Active")
        null -> println("No privilege active")
    }
}
```

### Health Checks & Global Access

```kotlin
// Check if the current handle is still functional
val stillAlive = PrivilegeHub.checkHealth()

// Access the active handle from anywhere
val current = PrivilegeHub.getHandleOrNull<Privilege>()

// Pattern match on the current handle
if (current is AdbPrivilege) {
    current.session.shell("uptime")
}

// Convenience: Direct typed access (returns null on wrong type)
val adb = PrivilegeHub.getHandleOrNull<AdbPrivilege>()
adb?.session?.shell("ls /data/local/tmp")

// Alternative: Access the active handle directly from the StateFlow
val active = PrivilegeHub.activePrivilege.value
```

## License

Privilege Hub is licensed under the [MIT License](../../LICENSE).
