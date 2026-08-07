package net.swzo.brass.ui.kit.net

import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.surface.BrassToast

/**
 * A [BrassButton] wired to [action] end to end:
 *
 * - **Auth mirror** - disabled up front with the deny reason as tooltip when [BrassNet.can] says the
 *   current player cannot run the action, and re-evaluated whenever the server's synced permissions
 *   arrive or change.
 * - **Pending state** - disabled with "Sending…" while the request is in flight, re-enabled on the
 *   reply.
 * - **Errors** - failed replies surface as an error toast; successful ones are left to [onResult] so
 *   the caller can decide how loud success should be.
 * - **Optimistic updates** - pass [optimistic] to apply the change to local state before the server
 *   confirms it; the authoritative state push (or [onResult]) reconciles afterwards.
 *
 * The server-side handler runs independently of everything here - the button is only the client half.
 */
fun <T : Any> actionButton(
    label: String,
    action: BrassAction<T>,
    accent: BrassAccent = BrassAccent.DEFAULT,
    optimistic: (() -> Unit)? = null,
    onResult: (BrassActionResult) -> Unit = {},
    input: () -> T,
): BrassButton {
    lateinit var button: BrassButton
    var authDenied = false
    var permissionsHandle: (() -> Unit)? = null
    button = BrassButton(label, accent) {
        if (!button.active) return@BrassButton
        authDenied = false
        button.disable("Sending…")
        optimistic?.invoke()
        BrassNet.send(action, input()) { result ->
            if (!authDenied) button.enable()
            if (!result.ok) {
                BrassToast.show(BrassTree.rootOf(button), result.message, BrassToast.Type.ERROR)
            }
            onResult(result)
        }
    }

    fun applyMirror() {
        when (val decision = BrassNet.can(action)) {
            is AuthDecision.Deny -> {
                authDenied = true
                button.disable(decision.reason)
            }
            AuthDecision.Grant -> if (authDenied) {
                authDenied = false
                button.enable()
            }
        }
    }
    applyMirror()
    // Re-evaluate when the server's permission sync arrives/changes. The listener removes itself once
    // the button leaves the tree, so a screen full of buttons cannot leak listeners.
    permissionsHandle = BrassNet.onPermissionsChanged {
        if (!BrassTree.isAttached(button)) {
            permissionsHandle?.invoke()
            permissionsHandle = null
        } else {
            applyMirror()
        }
    }
    return button
}
