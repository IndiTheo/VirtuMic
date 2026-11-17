package com.indidevs.android.privilegehub

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of a [PrivilegeProvider] for UI display.
 * Does not expose [acquire] or other triggering methods as all privilege lifecycle
 * goes through [PrivilegeHub].
 */
data class PrivilegeProviderView(
    val id: String,
    val name: String,
    val priority: Int,
    val isAvailable: Boolean,
    val hints: StateFlow<Set<PrivilegeHint>>
)

/**
 * Converts a [PrivilegeProvider] to its read-only [PrivilegeProviderView].
 */
fun PrivilegeProvider<*>.toView(): PrivilegeProviderView = PrivilegeProviderView(
    id = id,
    name = name,
    priority = priority,
    isAvailable = isAvailable,
    hints = hints
)
