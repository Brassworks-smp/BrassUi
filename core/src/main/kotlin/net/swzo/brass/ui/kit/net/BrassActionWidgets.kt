package net.swzo.brass.ui.kit.net

import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.surface.BrassToast

/**
 * A [BrassButton] wired to [action]: auth-mirrored, disabled while pending, failed replies toast.
 * Pass [optimistic] to apply the change locally before the server confirms it.
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
