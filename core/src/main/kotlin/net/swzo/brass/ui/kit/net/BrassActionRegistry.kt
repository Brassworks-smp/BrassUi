package net.swzo.brass.ui.kit.net

/**
 * The id -> action index, shared by both sides, plus the per-player rate limiting state.
 * Registration is idempotent: the same action object is constructed on the client and the server (and
 * possibly twice on one side if discovery runs again), and the first registration wins. Duplicate ids
 * with different handlers are a developer error - the registry keeps the first and the second is
 * silently ignored, which is the failure mode that is safe in a multiplayer context.
 */
class BrassActionRegistry {

    private val actions = LinkedHashMap<String, BrassAction<*>>()
    private val windows = HashMap<String, Window>()

    @Synchronized
    fun register(action: BrassAction<*>): Boolean {
        if (actions.containsKey(action.id)) return false
        actions[action.id] = action
        return true
    }

    @Synchronized
    fun <T : Any> get(id: String): BrassAction<T>? {
        @Suppress("UNCHECKED_CAST")
        return actions[id] as BrassAction<T>?
    }

    @Synchronized
    fun all(): List<BrassAction<*>> = actions.values.toList()

    @Synchronized
    fun tryAcquire(action: BrassAction<*>, playerId: String?): Boolean {
        val limit = action.rateLimit ?: return true
        val key = "${action.id}\u0000${playerId ?: ""}"
        val now = System.currentTimeMillis()
        val window = windows.getOrPut(key) { Window(now, 0) }
        if (now - window.startedAt >= limit.perSeconds * 1000) {
            window.startedAt = now
            window.count = 0
        }
        if (window.count >= limit.max) return false
        window.count++
        return true
    }

    @Synchronized
    fun clearPlayer(playerId: String) {
        val suffix = "\u0000$playerId"
        windows.keys.removeIf { it.endsWith(suffix) }
    }

    private class Window(var startedAt: Long, var count: Int)
}
