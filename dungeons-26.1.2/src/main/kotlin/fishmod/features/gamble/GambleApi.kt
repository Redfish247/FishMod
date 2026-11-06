package fishmod.features.gamble

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.Http
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors

// Worker client: Mojang session auth, cached Bearer token, one retry on 401
object GambleApi {

    class Result(val status: Int, val json: JsonObject?, val arr: com.google.gson.JsonArray? = null)

    private const val DEFAULT_TTL_MS = 25 * 60_000L
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-Gamble").apply { isDaemon = true } }
    private val rng = SecureRandom()

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

    private fun callBlocking(method: String, path: String, body: JsonObject?): Result? {
        var res = send(method, path, body, authToken(false) ?: return null)
        if (res.status == 401) res = send(method, path, body, authToken(true) ?: return null)
        return res
    }

    private fun send(method: String, path: String, body: JsonObject?, tok: String): Result {
        val b = HttpRequest.newBuilder().uri(URI.create(base() + path))
            .header("Authorization", "Bearer $tok").header("User-Agent", "FishMod").timeout(Duration.ofSeconds(10))
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
            val serverId = ByteArray(20).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            joinSession(mc, user.profileId, user.accessToken, serverId)
            val body = JsonObject().apply { addProperty("name", name); addProperty("serverId", serverId) }
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
            return null
        }
    }

    // Reflection: the session service API differs between the Minecraft versions we build for
    private fun joinSession(mc: Minecraft, profileId: UUID, accessToken: String, serverId: String) {
        val services = mc.javaClass.getMethod("services").invoke(mc)
        val svc = services.javaClass.getMethod("sessionService").invoke(services)
        val m = svc.javaClass.methods.first { it.name == "joinServer" && it.parameterCount == 3 }
        if (m.parameterTypes[0] == UUID::class.java) m.invoke(svc, profileId, accessToken, serverId)
        else m.invoke(svc, mc.player!!.gameProfile, accessToken, serverId)
    }
}
