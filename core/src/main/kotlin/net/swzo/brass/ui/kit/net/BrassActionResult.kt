package net.swzo.brass.ui.kit.net

/**
 * The outcome of an action, produced server-side and delivered back to the UI thread that sent it.
 * A [Success] may carry a JSON payload (whatever the handler chose to return); a [Failure] carries a
 * stable [code] plus string args so a UI can show a localised message without knowing the server's
 * language. The demo and widget helpers default to showing [message] in an error toast.
 */
sealed interface BrassActionResult {

    val ok: Boolean

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

fun ok(): BrassActionResult.Success = BrassActionResult.Success(null)

fun ok(value: Any): BrassActionResult.Success = BrassActionResult.Success(BrassJson.toJson(value))

fun err(code: String): BrassActionResult.Failure = BrassActionResult.Failure(code)

fun err(code: String, vararg args: Any?): BrassActionResult.Failure =
    BrassActionResult.Failure(code, args.map { it?.toString() ?: "null" })

fun <T : Any> BrassActionResult.payloadAs(type: Class<T>): T? =
    if (this is BrassActionResult.Success) BrassJson.fromJson(payload, type) else null
