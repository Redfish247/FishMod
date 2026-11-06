package fishmod.features.gamble

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.Http
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors

// Worker client: name-only login (never touches the session token), cached Bearer token, one retry on 401
object GambleApi {

    class Result(val status: Int, val json: JsonObject?, val arr: com.google.gson.JsonArray? = null)

    private const val DEFAULT_TTL_MS = 25 * 60_000L
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-Gamble").apply { isDaemon = true } }

    @Volatile private var token: String? = null
    @Volatile private var tokenExpiry = 0L
    @Volatile private var tokenFor = ""
    @Volatile private var authFailUntil = 0L

    private fun base(): String = GambleSettings.API_BASE.trim().trimEnd('/')

    // Runs on the worker thread; cb is called on the same thread (use mc.execute for UI)
    fun call(method: String, path: String, body: JsonObject?, cb: (Result?) -> Unit) {
        pool.execute {
            val res = try { callBlocking(method, path, body) } catch (t: Throwable) { null }
            try { cb(res) } catch (_: Throwable) {}
        }
    }

    // Why the last call returned nothing (shown in chat so "offline" is diagnosable)
    @Volatile @JvmField var lastError: String = ""

    // Public boards need no session; skip the Mojang login for them
    private fun isPublic(method: String, path: String) = method == "GET" && (path.startsWith("/leaderboard") || path.startsWith("/crown"))

    private fun callBlocking(method: String, path: String, body: JsonObject?): Result? {
        if (isPublic(method, path)) {
            return try { send(method, path, body, null) } catch (t: Throwable) { lastError = "${t.javaClass.simpleName}: ${t.message}"; null }
        }
        var res = send(method, path, body, authToken(false) ?: return null)
        if (res.status == 401) res = send(method, path, body, authToken(true) ?: return null)
        return res
    }

    private fun send(method: String, path: String, body: JsonObject?, tok: String?): Result {
        val b = HttpRequest.newBuilder().uri(URI.create(base() + path))
            .header("User-Agent", "FishMod").timeout(Duration.ofSeconds(10))
        if (tok != null) b.header("Authorization", "Bearer $tok")
        val req = if (method == "POST") b.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString((body ?: JsonObject()).toString())).build() else b.GET().build()
        val resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString())
        val el = try { JsonParser.parseString(resp.body()) } catch (_: Throwable) { null }
        return Result(resp.statusCode(), el?.takeIf { it.isJsonObject }?.asJsonObject, el?.takeIf { it.isJsonArray }?.asJsonArray)
    }

    private fun authToken(force: Boolean): String? {
        val mc = Minecraft.getInstance()
        val user = mc.user
        val name = user.name
        val now = System.currentTimeMillis()
        val t = token
        if (!force && t != null && now < tokenExpiry && tokenFor == name) return t
        if (now < authFailUntil) return null
        try {
            val body = JsonObject().apply { addProperty("name", name) }
            val r = Http.CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(base() + "/auth/verify")).header("Content-Type", "application/json")
                    .header("User-Agent", "FishMod").timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString()
            )
            val o = JsonParser.parseString(r.body()).asJsonObject
            val newTok = o.get("token")?.asString
            if (r.statusCode() != 200 || newTok == null) throw IllegalStateException("verify ${r.statusCode()}")
            token = newTok; tokenFor = name
            val ttl = o.get("expiresInMs")?.asLong ?: DEFAULT_TTL_MS
            tokenExpiry = now + ttl.coerceAtMost(DEFAULT_TTL_MS)
            return newTok
        } catch (t: Throwable) {
            authFailUntil = now + 60_000L
            lastError = "login: ${t.javaClass.simpleName}: ${t.message}"
            fishmod.utils.debug.FishDebug.LOGGER.warn("[Gamble] auth failed", t)
            return null
        }
    }
}
