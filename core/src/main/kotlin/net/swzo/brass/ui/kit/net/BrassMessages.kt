package net.swzo.brass.ui.kit.net

import java.util.concurrent.ConcurrentHashMap

/**
 * The message catalog for action failure codes - the translation seam of the networking module.
 *
 * Every built-in code has an English template in [defaults]; `{0}`, `{1}`, ... are replaced with the
 * failure's args. Two layers can refine that:
 *
 * 1. **Translator** - installed by a platform. The NeoForge transport installs one that resolves
 *    `brassui.net.error.<code>` through Minecraft's own language system, so resource packs translate
 *    the built-in messages like any other in-game text (the keys ship in
 *    `assets/brassui/lang/en_us.json`).
 * 2. **Overrides** - a host mod can register its own templates (or its own locale's strings) for any
 *    code, built-in or its own, with [register].
 *
 * Resolution order: translator, then overrides, then the built-in defaults, then the raw code.
 */
object BrassMessages {

    /** A platform hook that turns a code + args into display text, or null to fall back. */
    fun interface Translator {
        fun translate(code: String, args: List<String>): String?
    }

    @Volatile
    var translator: Translator? = null

    private val overrides = ConcurrentHashMap<String, String>()

    private val defaults: Map<String, String> = linkedMapOf(
        "action.unknown" to "Unknown action: {0}",
        "denied" to "You don't have permission to do that ({0})",
        "rate.limited" to "Too many requests - try again shortly",
        "action.malformed" to "That request could not be understood",
        "action.failed" to "Something went wrong running that action ({0})",
        "net.unavailable" to "Networking isn't set up here",
        "send.failed" to "The action could not be sent ({0})",
        "wire.bad" to "The server sent an unreadable reply",
        "timeout" to "The server took too long to reply after {0}s",
        "action.disabled" to "That action is currently disabled",
        "no.connection" to "Not connected to a server",
        "version.mismatch" to "Networking version mismatch: client {0}, server {1}",
    )

    /**
     * Register (or replace) the template for [code]. Templates use `{0}`, `{1}`, ... for the failure's
     * args. This is how a host mod translates its own error codes, or overrides a built-in one.
     */
    fun register(code: String, template: String) {
        overrides[code] = template
    }

    /** The display text for [code] with [args], through the resolution order above. */
    fun format(code: String, args: List<String>): String {
        translator?.translate(code, args)?.let { return it }
        val template = overrides[code] ?: defaults[code] ?: return code
        var text = template
        for ((index, arg) in args.withIndex()) {
            text = text.replace("{$index}", arg)
        }
        return text
    }
}
