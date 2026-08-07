package net.swzo.brass.ui.kit.net

/**
 * The outcome of an action, produced server-side and delivered back to the UI thread that sent it.
 *
 * A [Success] may carry a JSON [payload] (whatever the handler chose to return); a [Failure] carries a
 * stable [code] plus string [args] so a UI can show a localised message without knowing the server's
 * language. The demo and widget helpers default to showing [message] in an error toast.
 */
sealed interface BrassActionResult {

    val ok: Boolean

    /**
     * A short human-readable summary for toasts and logs. Failures resolve through
     * [BrassMessages], so the text is translated (and overridable) rather than a raw code.
     */
    val message: String

    data class Success(val payload: String? = null) : BrassActionResult {
        override val ok = true
        override val message get() = "ok"
    }

    data class Failure(val code: String, val args: List<String> = emptyList()) : BrassActionResult {
        override val ok = false
        override val message: String get() = BrassMessages.format(code, args)
    }
}

/** An action succeeded with no payload. */
fun ok(): BrassActionResult.Success = BrassActionResult.Success(null)

/** An action succeeded, serialising [value] as the reply payload (JSON). */
fun ok(value: Any): BrassActionResult.Success = BrassActionResult.Success(BrassJson.toJson(value))

/** An action failed with a stable [code]; the UI can map it to a message. */
fun err(code: String): BrassActionResult.Failure = BrassActionResult.Failure(code)

/** An action failed with a code plus substitution [args] for a localised message. */
fun err(code: String, vararg args: Any?): BrassActionResult.Failure =
    BrassActionResult.Failure(code, args.map { it?.toString() ?: "null" })

/**
 * Decode a successful reply's [BrassActionResult.Success.payload] into [type], or null if the reply
 * failed, had no payload, or did not match [type].
 */
fun <T : Any> BrassActionResult.payloadAs(type: Class<T>): T? =
    if (this is BrassActionResult.Success) BrassJson.fromJson(payload, type) else null
