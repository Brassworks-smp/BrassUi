package net.swzo.brass.ui.kit.net

import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Resolves a player's profile from the **BrassWorks player API** — one request per key for the life of
 * the process, on a small pool of daemon threads.
 *
 * The API takes a UUID *or* a username at `/{key}` and answers with the player's canonical username,
 * UUID, a ready-made face avatar URL, and the raw skin texture:
 *
 * ```json
 * { "code": "player.found",
 *   "data": { "player": {
 *     "username": "swzo",
 *     "id": "f538f9ff-601c-45cf-b6c7-52afc25cfe3e",
 *     "avatar": "https://crafthead.net/avatar/f538f9ff...",
 *     "skin_texture": "https://textures.minecraft.net/texture/c21595..." } } }
 * ```
 *
 * The point of going through this rather than the game's own skin cache is that it works for **any**
 * player by id, online or not, in game or on the desktop — which is exactly what a team roster needs.
 * [BrassPlayerHead] with `Source.BRASSWORKS` is built on it; the avatar URL then flows through
 * [net.swzo.brass.ui.kit.media.BrassImageLoader] like any other remote image.
 *
 * Modelled on that loader deliberately: same daemon pool, same per-key cache with a short failure TTL
 * so a missing player is not re-requested every frame, same "never block a render on the network".
 */
object BrassworksProfile {

    /**
     * Base URL of the player API, without a trailing slash. A key is appended as `/{key}`.
     *
     * A `var` so a fork of the launcher can point it at its own deployment, but it defaults to the
     * public BrassWorks endpoint the toolkit is named for.
     */
    var baseUrl: String = "https://api.opnsoc.org/player"

    /** A resolved player. [avatarUrl] is a face PNG; [skinUrl] is the full skin texture, if the API had one. */
    data class Profile(
        val username: String,
        val uuid: String,
        val avatarUrl: String,
        val skinUrl: String?,
    )

    /** How long to wait for a response before giving up. */
    private val TIMEOUT: Duration = Duration.ofSeconds(10)

    /** How long a *failure* (not-found, timeout, bad JSON) is remembered before the key may be tried again. */
    private const val FAILURE_TTL_MS = 30_000L

    private val threads = AtomicInteger()

    private val pool = Executors.newFixedThreadPool(
        2,
        ThreadFactory { r -> Thread(r, "brassui-profile-${threads.incrementAndGet()}").apply { isDaemon = true } },
    )

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    private val gson = Gson()

    private class Entry(val future: CompletableFuture<Profile?>, val at: Long)

    /** Resolved profiles by the key that asked for them (uuid or username), newest access last. */
    private val cache = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > 256
    }

    /**
     * The profile for [key] (a UUID or a username), fetched once and cached.
     *
     * Returns a future that completes with null when the player could not be resolved. Safe to call
     * every frame: a hit returns the cached future, and a recent failure returns a completed-null future
     * without touching the network until [FAILURE_TTL_MS] has passed.
     */
    fun load(key: String): CompletableFuture<Profile?> {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return CompletableFuture.completedFuture(null)
        synchronized(cache) {
            val existing = cache[trimmed]
            val now = System.currentTimeMillis()
            // Keep a success forever; retry a failure once its TTL has lapsed.
            if (existing != null) {
                val settledToFailure = existing.future.isDone && existing.future.getNow(null) == null
                if (!settledToFailure || now - existing.at < FAILURE_TTL_MS) return existing.future
            }
            val future = CompletableFuture.supplyAsync({ fetch(trimmed) }, pool)
            cache[trimmed] = Entry(future, now)
            return future
        }
    }

    private fun fetch(key: String): Profile? = runCatching {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/${enc(key)}"))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null

        val parsed = gson.fromJson(response.body(), Response::class.java) ?: return null
        val player = parsed.data?.player ?: return null
        val username = player.username ?: return null
        val uuid = player.id ?: player.raw_id ?: return null
        val avatar = player.avatar ?: return null
        Profile(username, uuid, avatar, player.skin_texture)
    }.getOrNull()

    /** Percent-encode a path segment so a username with odd characters cannot break the URL. */
    private fun enc(segment: String): String =
        URI(null, null, segment, null).rawPath

    // ---- Gson bindings (populated reflectively, hence the nullable vars) ----------------------------

    private class Response {
        var code: String? = null
        var data: Data? = null
    }

    private class Data {
        var player: Player? = null
    }

    private class Player {
        var username: String? = null
        var id: String? = null
        var raw_id: String? = null
        var avatar: String? = null
        var skin_texture: String? = null
    }
}
