package com.indidevs.android.privilegehub

/**
 * Represents the severity or category of a status hint.
 */
enum class HintType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * A polymorphic identifier for a privilege hint.
 * Providers should implement this with an Enum or a set of objects.
 */
interface HintId {
    /**
     * A human-readable name for this hint (e.g., "Elevated", "Disconnected").
     */
    val displayName: String
}

/**
 * A structured hint about a provider's status.
 */
data class PrivilegeHint(
    val id: HintId,
    val type: HintType = HintType.INFO
) {
    /**
     * The display text for this hint, derived from the ID.
     */
    val text: String get() = id.displayName

    companion object {
        fun info(id: HintId) = PrivilegeHint(id, HintType.INFO)
        fun success(id: HintId) = PrivilegeHint(id, HintType.SUCCESS)
        fun warning(id: HintId) = PrivilegeHint(id, HintType.WARNING)
        fun error(id: HintId) = PrivilegeHint(id, HintType.ERROR)
    }
}
