package net.swzo.brass.ui.kit.text

import com.google.gson.Gson
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.text.BrassSyntax.init
import java.awt.Color
import java.io.InputStreamReader
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Syntax highlighting for fenced code blocks, driven by `assets/brassui/syntax_rules.json` - 214
 * languages under 396 aliases, each a single regex with five named groups (`comment`, `string`,
 * `keyword`, `number`, `macro`).
 *
 * ### Loading and cost
 *
 * The JSON is read with Gson, once. What is *not* done up front is compiling the patterns: they run
 * up to 14 KB each, and compiling all 214 takes ~164 ms against ~20 ms to read the file - building
 * 213 automata a given screen will never use. Each pattern is compiled on the first request for that
 * language and cached, which costs ~14 ms once and nothing thereafter.
 *
 * Call [init] during mod startup to take the file read off the first render's critical path;
 * everything still works without it, just with the parse happening on first use.
 */
object BrassSyntax {

    /** One highlighted run of text within a line. */
    class Span(val text: String, val color: Color)

    private const val PATH = "/assets/brassui/syntax_rules.json"

    // ---- brass palette ---------------------------------------------------------------------------
    // The JSON ships VS Code's default colours; these replace them so highlighted code sits in the
    // same palette as the rest of the toolkit instead of importing a second, unrelated theme.

    /** Dim grey - comments recede. */
    private val COMMENT get() = Colors.SYNTAX_COMMENT

    /** Warm brass/amber - the metal tone, for string literals. */
    private val STRING get() = Colors.SYNTAX_STRING

    /** The accent green - keywords carry the toolkit's primary colour. */
    private val KEYWORD get() = Colors.SYNTAX_KEYWORD

    /** Patina teal - numbers, cool against the warm strings. */
    private val NUMBER get() = Colors.SYNTAX_NUMBER

    /** Copper - macros and preprocessor directives. */
    private val MACRO get() = Colors.SYNTAX_MACRO

    private val DEFAULT get() = Colors.SYNTAX_DEFAULT

    // ---- model -----------------------------------------------------------------------------------

    /** Gson binding for the file. Fields are populated reflectively, hence the nullable vars. */
    private class LangDef {
        var aliases: List<String>? = null
        var pattern: String? = null
    }

    private class SyntaxConfig {
        var languages: List<LangDef>? = null
    }

    /** Lowercased alias to language index. */
    private val aliasToLanguage = HashMap<String, Int>(512)

    /** Pattern source per language, kept as text until someone actually needs it compiled. */
    private var patterns: Array<String> = emptyArray()

    /** Compiled patterns, filled in on demand; null means "not compiled yet". */
    private var compiled = arrayOfNulls<Pattern>(0)

    /** Languages whose pattern failed to compile - recorded so a bad one is not retried per frame. */
    private val broken = HashSet<Int>()

    private var loaded = false

    /**
     * Why loading failed, if it did. A silent `runCatching` around a resource parse is how a feature
     * ends up quietly doing nothing; this makes the reason retrievable instead.
     */
    var lastError: String? = null
        private set

    /** Number of languages the index holds; 0 if the file is missing or malformed. */
    val languageCount: Int get() { load(); return patterns.size }

    /**
     * Parse the rules file now, rather than on the first highlighted code block. Safe to call more
     * than once and safe to skip entirely - it only moves the cost, it is not a prerequisite.
     */
    fun init() = load()

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val stream = BrassSyntax::class.java.getResourceAsStream(PATH) ?: return@runCatching
            val config = stream.use { Gson().fromJson(InputStreamReader(it), SyntaxConfig::class.java) }
            val langs = config?.languages ?: return@runCatching

            val pats = ArrayList<String>(langs.size)
            for (lang in langs) {
                val pattern = lang.pattern ?: continue
                val index = pats.size
                pats.add(pattern)
                for (alias in lang.aliases.orEmpty()) aliasToLanguage[alias.lowercase()] = index
            }
            patterns = pats.toTypedArray()
            compiled = arrayOfNulls(patterns.size)
        }.onFailure { e ->
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            aliasToLanguage.clear()
            patterns = emptyArray()
            compiled = arrayOfNulls(0)
        }
        if (patterns.isEmpty() && lastError == null) lastError = "no languages parsed from $PATH"
    }

    fun supports(language: String?): Boolean {
        if (language.isNullOrEmpty()) return false
        load()
        return aliasToLanguage.containsKey(language.lowercase())
    }

    private fun patternFor(index: Int): Pattern? {
        if (index in broken) return null
        compiled.getOrNull(index)?.let { return it }
        val p = runCatching { Pattern.compile(patterns[index]) }.getOrNull()
        if (p == null) { broken += index; return null }
        compiled[index] = p
        return p
    }

    /**
     * Highlight [code] as [language], returning one list of [Span]s per line. An unknown language (or
     * none) yields a single default-coloured span per line, so callers render the result the same way
     * either way.
     */
    fun highlight(language: String?, code: String): List<List<Span>> {
        val lines = code.split("\n")
        if (language.isNullOrEmpty()) return plain(lines)
        load()
        val index = aliasToLanguage[language.lowercase()] ?: return plain(lines)
        val pattern = patternFor(index) ?: return plain(lines)

        // Tokenize the whole block, then cut the tokens at newlines. Matching per line would break
        // any construct that spans lines - block comments and multi-line strings especially.
        val out = ArrayList<ArrayList<Span>>(lines.size)
        var current = ArrayList<Span>()
        var last = 0

        fun emit(text: String, color: Color) {
            if (text.isEmpty()) return
            var start = 0
            while (true) {
                val nl = text.indexOf('\n', start)
                if (nl < 0) {
                    if (start < text.length) current.add(Span(text.substring(start), color))
                    return
                }
                if (nl > start) current.add(Span(text.substring(start, nl), color))
                out.add(current)
                current = ArrayList()
                start = nl + 1
            }
        }

        // One try around the whole tokenisation rather than a runCatching per `find()`. A pathological
        // pattern can throw (stack overflow on a catastrophic backtrack, most often) and the guard is
        // worth keeping - but paying for an exception handler per *token* to get it was not, and a
        // throw partway through is handled the same way either way: fall back to unhighlighted text.
        try {
            val m = pattern.matcher(code)
            while (m.find()) {
                if (m.start() > last) emit(code.substring(last, m.start()), DEFAULT)
                emit(m.group(), colorOf(m))
                last = m.end()
                // a zero-width match would spin forever
                if (m.end() == m.start()) {
                    if (last >= code.length) break
                    emit(code.substring(last, last + 1), DEFAULT)
                    last++
                }
            }
        } catch (_: Throwable) {
            return plain(lines)
        }
        if (last < code.length) emit(code.substring(last), DEFAULT)
        out.add(current)
        return out
    }

    private fun colorOf(m: Matcher): Color = when {
        group(m, "comment") -> COMMENT
        group(m, "string") -> STRING
        group(m, "keyword") -> KEYWORD
        group(m, "number") -> NUMBER
        group(m, "macro") -> MACRO
        else -> DEFAULT
    }

    /** A named group may be absent from a pattern entirely, which throws rather than returning null. */
    private fun group(m: Matcher, name: String): Boolean =
        runCatching { m.group(name) != null }.getOrDefault(false)

    private fun plain(lines: List<String>): List<List<Span>> =
        lines.map { if (it.isEmpty()) emptyList() else listOf(Span(it, DEFAULT)) }
}
