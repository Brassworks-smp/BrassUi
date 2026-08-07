package net.swzo.brass.ui.kit.net

/**
 * A per-player, per-action request budget: at most [max] actions inside any rolling [perSeconds]
 * window. Enforced server-side in [BrassActionRegistry.tryAcquire] before the handler runs, so a UI
 * that spams a button (or a hostile client) is throttled regardless of what the client does.
 */
class BrassRateLimit(val max: Int, val perSeconds: Long) {
    init {
        require(max > 0) { "max must be positive" }
        require(perSeconds > 0) { "perSeconds must be positive" }
    }
}
