package net.swzo.brass.ui.kit.net

import com.google.gson.JsonParser
import net.swzo.brass.ui.kit.media.BrassImageLoader
import java.awt.image.BufferedImage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object BrassSkins {

    enum class Provider { PLAYERDB, ELY_BY, MOJANG }

    data class Skin(val name: String?, val uuid: String?, val skinUrl: String, val provider: Provider)

    @Volatile
    var providers: List<Provider> = listOf(Provider.PLAYERDB, Provider.ELY_BY, Provider.MOJANG)

    var userAgent: String = "brassui (+https://github.com/Brassworks-smp/BrassUi)"

    private val TIMEOUT: Duration = Duration.ofSeconds(8)
    private const val FAILURE_TTL_MS = 60_000L
    private const val MAX_ENTRIES = 256

    private val threads = AtomicInteger()
    private val pool = Executors.newFixedThreadPool(2) {
        Thread(it, "brassui-skins-${threads.incrementAndGet()}").apply { isDaemon = true }
    }

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build()
    }

    private class Entry<T>(val future: CompletableFuture<T?>, val at: Long)

    private val skins = lru<Skin>()
    private val faces = lru<BufferedImage>()

    private fun <T> lru() = object : LinkedHashMap<String, Entry<T>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<T>>) = size > MAX_ENTRIES
    }

    fun load(player: String): CompletableFuture<Skin?> {
        val key = player.trim()
        if (key.isEmpty()) return CompletableFuture.completedFuture(null)
        return cached(skins, key.lowercase()) { CompletableFuture.supplyAsync({ resolve(key) }, pool) }
    }

    fun face(player: String): CompletableFuture<BufferedImage?> {
        val key = player.trim()
        if (key.isEmpty()) return CompletableFuture.completedFuture(null)
        return cached(faces, key.lowercase()) {
            load(key).thenCompose { skin ->
                if (skin == null) CompletableFuture.completedFuture(null)
                else BrassImageLoader.load(https(skin.skinUrl)).thenApply { it?.let(::faceOf) }
            }
        }
    }

    fun clear() {
        synchronized(skins) { skins.clear() }
        synchronized(faces) { faces.clear() }
    }

    private fun <T> cached(
        map: LinkedHashMap<String, Entry<T>>,
        key: String,
        start: () -> CompletableFuture<T?>,
    ): CompletableFuture<T?> = synchronized(map) {
        val now = System.currentTimeMillis()
        map[key]?.let { e ->
            val failed = e.future.isDone && e.future.getNow(null) == null
            if (!failed || now - e.at < FAILURE_TTL_MS) return e.future
        }
        val future = start().exceptionally { null }
        map[key] = Entry(future, now)
        future
    }

    private fun resolve(key: String): Skin? {
        for (provider in providers) {
            val skin = runCatching {
                when (provider) {
                    Provider.PLAYERDB -> fromPlayerDb(key)
                    Provider.ELY_BY -> fromElyBy(key)
                    Provider.MOJANG -> fromMojang(key)
                }
            }.getOrNull()
            if (skin != null) return skin
        }
        return null
    }

    private fun fromPlayerDb(key: String): Skin? =
        get("https://playerdb.co/api/player/minecraft/${enc(key)}")?.let(::parsePlayerDb)

    private fun fromElyBy(key: String): Skin? {
        if (isUuid(key)) return null
        val url = get("https://skinsystem.ely.by/textures/${enc(key)}")?.let(::parseElyTextures) ?: return null
        val uuid = get("https://authserver.ely.by/api/users/profiles/minecraft/${enc(key)}")?.let(::parseIdName)
        return Skin(uuid?.second ?: key, uuid?.first, url, Provider.ELY_BY)
    }

    private fun fromMojang(key: String): Skin? {
        val uuid = if (isUuid(key)) undash(key)
        else get("https://api.mojang.com/users/profiles/minecraft/${enc(key)}")?.let(::parseIdName)?.first ?: return null
        return get("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")?.let(::parseMojangSession)
    }

    private fun get(url: String): String? {
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return if (response.statusCode() == 200 && response.body().isNotBlank()) response.body() else null
    }

    fun parsePlayerDb(json: String): Skin? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        if (root["code"]?.asString != "player.found") return null
        val p = root.getAsJsonObject("data").getAsJsonObject("player")
        val url = p["skin_texture"]?.takeIf { !it.isJsonNull }?.asString ?: return null
        Skin(p["username"]?.asString, p["raw_id"]?.asString ?: p["id"]?.asString?.let(::undash), url, Provider.PLAYERDB)
    }.getOrNull()

    fun parseElyTextures(json: String): String? = runCatching {
        JsonParser.parseString(json).asJsonObject.getAsJsonObject("SKIN")?.get("url")?.asString
    }.getOrNull()

    fun parseIdName(json: String): Pair<String, String>? = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        (o["id"]?.asString ?: return null) to (o["name"]?.asString ?: return null)
    }.getOrNull()

    fun parseMojangSession(json: String): Skin? = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        val textures = o.getAsJsonArray("properties")
            .map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == "textures" }
            ?.get("value")?.asString ?: return null
        val decoded = JsonParser.parseString(String(Base64.getDecoder().decode(textures))).asJsonObject
        val url = decoded.getAsJsonObject("textures")?.getAsJsonObject("SKIN")?.get("url")?.asString ?: return null
        Skin(o["name"]?.asString, o["id"]?.asString, url, Provider.MOJANG)
    }.getOrNull()

    fun faceOf(skin: BufferedImage): BufferedImage? {
        if (skin.width < 64 || skin.height < 32) return null
        val scale = skin.width / 64
        val out = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val legacy = skin.height < skin.width
        val hatOpaque = legacy && (0 until 64).all { i ->
            (skin.getRGB((40 + i % 8) * scale, (8 + i / 8) * scale) ushr 24) == 0xFF
        }
        for (y in 0 until 8) for (x in 0 until 8) {
            val base = skin.getRGB((8 + x) * scale, (8 + y) * scale) or (0xFF shl 24)
            val hat = if (hatOpaque) 0 else skin.getRGB((40 + x) * scale, (8 + y) * scale)
            out.setRGB(x, y, over(hat, base))
        }
        return out
    }

    fun isUuid(key: String): Boolean = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(key)

    fun https(url: String): String = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    private fun undash(uuid: String) = uuid.replace("-", "").lowercase()

    private fun enc(segment: String): String = URI(null, null, segment, null).rawPath

    private fun over(top: Int, bottom: Int): Int {
        val a = (top ushr 24) / 255f
        if (a <= 0f) return bottom
        if (a >= 1f) return top
        fun ch(shift: Int) = (((top ushr shift) and 0xFF) * a + ((bottom ushr shift) and 0xFF) * (1 - a)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
