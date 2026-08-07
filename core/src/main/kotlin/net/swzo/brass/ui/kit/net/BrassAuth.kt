package net.swzo.brass.ui.kit.net

/**
 * Who is performing an action, in loader-independent terms.
 *
 * The game server fills [playerId] from the sender's `GameProfile` UUID and [opLevel] from the ops
 * list; the desktop transport fills both from its configuration. The toolkit never sees a
 * `ServerPlayer` - that stays on the platform side of the seam.
 */
class AuthContext(
    val playerId: String?,
    val opLevel: Int,
    val roles: Set<String> = emptySet(),
)

/** The result of checking an action against a player. */
sealed interface AuthDecision {

    data object Grant : AuthDecision

    /** [reason] is shown to the user (tooltip on the disabled control, toast on a server denial). */
    data class Deny(val reason: String) : AuthDecision
}

/**
 * Decides whether [action] may run for the player described by [ctx].
 *
 * The server is always the source of truth: [BrassNet.dispatch] runs this before any handler. The
 * client mirror ([BrassNetTransport.can]) is only an optimisation for greying buttons out - it can
 * never be trusted.
 */
fun interface BrassAuthorizer {
    fun check(action: BrassAction<*>, ctx: AuthContext): AuthDecision
}

/** The default authorizer: grant when the player's op level meets the action's declared minimum. */
object BrassAuthorizers {
    val byOpLevel: BrassAuthorizer = BrassAuthorizer { action, ctx ->
        if (ctx.opLevel >= action.minOpLevel) AuthDecision.Grant
        else AuthDecision.Deny("requires op level ${action.minOpLevel}")
    }
}

/**
 * Wire forms of a decision, for the permission-sync payload: "grant", or "deny:<reason>". The reason
 * survives the round trip so the client can show the same tooltip text the server would produce.
 */
fun AuthDecision.encode(): String = when (this) {
    AuthDecision.Grant -> "grant"
    is AuthDecision.Deny -> "deny:$reason"
}

fun decodeAuthDecision(encoded: String): AuthDecision =
    if (encoded == "grant") AuthDecision.Grant
    else AuthDecision.Deny(encoded.removePrefix("deny:").ifEmpty { "denied" })
