package fishmod.utils.update

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.Location
import fishmod.utils.debug.Debug
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

object UpdateManager {

    private const val MOD_ID = "fishmod-dungeons"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val FAILURE_BACKOFF_MS = 30 * 60 * 1000L
    private const val JOIN_SETTLE_TICKS = 100
    private const val MAX_JAR_BYTES = 64L * 1024 * 1024

    data class Release(
        val version: String = "",
        val tag: String = "",
        val name: String = "",
        val body: String = "",
        val htmlUrl: String = "",
        val publishedAt: String = "",
        val assetName: String = "",
        val assetUrl: String = "",
        val assetSize: Long = 0,
        val assetSha256: String = "",
    )

    private class State {
        var lastCheckAt = 0L
        var nextCheckAt = 0L
        var etag: String? = null
        var release: Release? = null
        var lastShownAt = 0L
        var dismissedVersion: String? = null
        var stagedVersion: String? = null
        var stagedFile: String? = null
    }

    enum class DownloadState { IDLE, DOWNLOADING, STAGED, FAILED }

    private val container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null)
    val currentVersion: String = container?.metadata?.version?.friendlyString ?: "0.0.0"
    private val mcVersion: String = FabricLoader.getInstance().getModContainer("minecraft")
        .map { it.metadata.version.friendlyString }.orElse("")
    private val updaterMeta = container?.metadata?.getCustomValue("fishmod:updater")?.asObject
    private val githubRepo: String? = updaterMeta?.get("github")?.asString?.takeIf { it.contains('/') }
    val modrinthUrl: String? = container?.metadata?.contact?.get("homepage")?.orElse(null)

    private val gameDir: Path = FabricLoader.getInstance().gameDir
    private val stateFile: Path = FabricLoader.getInstance().configDir.resolve("fishmod").resolve("updater.json")
    private val stagingDir: Path = gameDir.resolve(".fishmod-update")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lock = Any()
    private var state = State()

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-Updater").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build()
    }
    private val checking = AtomicBoolean(false)

    @Volatile var downloadState = DownloadState.IDLE; private set
    @Volatile var downloadProgress = 0f; private set
    @Volatile var downloadError: String? = null; private set

    private var onHypixel = false
    private var shownThisLogin = false
    private var ticksSinceJoin = 0

    private val userAgent = "FishModDungeons/$currentVersion (+https://github.com/${githubRepo ?: "Redfish247/FishModDungeons"})"

    @JvmStatic
    fun init() {
        if (githubRepo == null) return
        load()
        synchronized(lock) {
            val staged = state.stagedFile?.let(Path::of)
            if (staged != null && Files.isRegularFile(staged) && isNewer(state.stagedVersion)) downloadState = DownloadState.STAGED
            else if (state.stagedFile != null) { state.stagedVersion = null; state.stagedFile = null; save() }
        }
        checkAsync(false)

        ClientPlayConnectionEvents.JOIN.register { _, _, mc ->
            val wasOnHypixel = onHypixel
            onHypixel = isHypixel(mc)
            ticksSinceJoin = 0
            if (onHypixel && !wasOnHypixel) shownThisLogin = false
            if (onHypixel) checkAsync(false)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> onHypixel = false }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientLifecycleEvents.CLIENT_STOPPING.register { launchInstaller() }
    }

    // Hypixel proxies are *.hypixel.net; the Mod API location packet confirms it once it arrives.
    private fun isHypixel(mc: Minecraft): Boolean {
        if (mc.isLocalServer) return false
        val host = mc.currentServer?.ip?.substringBefore(':')?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return false
        return host == "hypixel.net" || host.endsWith(".hypixel.net")
    }

    private fun tick(mc: Minecraft) {
        if (!onHypixel || shownThisLogin || mc.player == null || mc.screen != null) return
        if (++ticksSinceJoin < JOIN_SETTLE_TICKS || ticksSinceJoin % 20 != 0) return
        if (Location.inDungeon() || Location.`in`(Location.KUUDRA)) return
        val release = pendingRelease() ?: return
        // Once per Hypixel login, never again while connected.
        shownThisLogin = true
        synchronized(lock) { state.lastShownAt = System.currentTimeMillis(); save() }
        mc.setScreen(UpdateScreen(release))
    }

    fun pendingRelease(): Release? = synchronized(lock) {
        val r = state.release ?: return null
        if (!isNewer(r.version)) return null
        if (state.dismissedVersion == r.version) return null
        if (downloadState == DownloadState.STAGED && state.stagedVersion == r.version) return null
        r
    }

    fun canInstall(release: Release): Boolean = release.assetUrl.isNotEmpty() && currentJar() != null

    fun dismiss(release: Release) = synchronized(lock) { state.dismissedVersion = release.version; save() }

    private fun isNewer(version: String?): Boolean {
        val remote = SemVer.parse(version, mcVersion) ?: return false
        val local = SemVer.parse(currentVersion, mcVersion) ?: return false
        return remote > local
    }

    fun checkAsync(force: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!force && (now < state.nextCheckAt || now - state.lastCheckAt < CHECK_INTERVAL_MS)) return
        }
        if (!checking.compareAndSet(false, true)) return
        worker.execute {
            try { fetchLatest() } catch (t: Throwable) {
                Debug.LOGGER.warn("[FishMod updater] check failed: {}", t.toString())
                synchronized(lock) { state.nextCheckAt = System.currentTimeMillis() + FAILURE_BACKOFF_MS; save() }
            } finally { checking.set(false) }
        }
    }

    private fun fetchLatest() {
        val etag = synchronized(lock) { state.etag.takeIf { state.release != null } }
        val req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/$githubRepo/releases/latest"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        val now = System.currentTimeMillis()
        when (res.statusCode()) {
            200 -> {
                val release = parseRelease(JsonParser.parseString(res.body()).asJsonObject)
                synchronized(lock) {
                    state.release = release
                    state.etag = res.headers().firstValue("etag").orElse(null)
                    state.lastCheckAt = now; state.nextCheckAt = 0
                    save()
                }
            }
            304 -> synchronized(lock) { state.lastCheckAt = now; state.nextCheckAt = 0; save() }
            404 -> synchronized(lock) { state.release = null; state.etag = null; state.lastCheckAt = now; save() }
            403, 429 -> synchronized(lock) { state.nextCheckAt = rateLimitResetAt(res, now); save() }
            else -> synchronized(lock) { state.nextCheckAt = now + FAILURE_BACKOFF_MS; save() }
        }
    }

    private fun rateLimitResetAt(res: HttpResponse<*>, now: Long): Long {
        val h = res.headers()
        h.firstValue("retry-after").orElse(null)?.toLongOrNull()?.let { return now + it.coerceIn(60, 86_400) * 1000 }
        h.firstValue("x-ratelimit-reset").orElse(null)?.toLongOrNull()?.let { return (it * 1000).coerceIn(now + 60_000, now + 86_400_000) }
        return now + FAILURE_BACKOFF_MS
    }

    private fun parseRelease(o: JsonObject): Release? {
        if (o.bool("draft") || o.bool("prerelease")) return null
        val tag = o.str("tag_name")
        if (SemVer.parse(tag, mcVersion) == null) return null
        val jars = o.getAsJsonArray("assets")?.mapNotNull { it as? JsonObject }
            ?.filter { it.str("name").endsWith(".jar") && !it.str("name").contains("-sources") } ?: emptyList()
        val asset = jars.firstOrNull { mcVersion.isNotEmpty() && it.str("name").endsWith("-$mcVersion.jar") }
            ?: jars.singleOrNull()?.takeIf { mcVersion.isEmpty() || !Regex("""-\d+\.\d+(\.\d+)?\.jar$""").containsMatchIn(it.str("name")) }
        val safeName = asset?.str("name")?.takeIf { Regex("""^[\w.+-]+\.jar$""").matches(it) }
        val url = asset?.str("browser_download_url")?.takeIf { it.startsWith("https://github.com/") }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            tag = tag,
            name = o.str("name").ifBlank { tag },
            body = o.str("body").take(8000),
            htmlUrl = o.str("html_url").takeIf { it.startsWith("https://github.com/") } ?: "",
            publishedAt = o.str("published_at"),
            assetName = if (url != null) safeName ?: "" else "",
            assetUrl = if (safeName != null) url ?: "" else "",
            assetSize = asset?.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0,
            assetSha256 = asset?.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(Locale.ROOT) ?: "",
        )
    }

    fun download(release: Release) {
        if (!canInstall(release)) return
        if (downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.STAGED) return
        downloadState = DownloadState.DOWNLOADING
        downloadProgress = 0f
        downloadError = null
        worker.execute {
            val part = stagingDir.resolve(release.assetName + ".part")
            try {
                Files.createDirectories(stagingDir)
                val req = HttpRequest.newBuilder(URI.create(release.assetUrl))
                    .timeout(Duration.ofMinutes(3)).header("User-Agent", userAgent).header("Accept", "application/octet-stream").GET().build()
                val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
                if (res.statusCode() != 200) { res.body().close(); throw IllegalStateException("HTTP ${res.statusCode()}") }
                val total = res.headers().firstValueAsLong("content-length").orElse(release.assetSize)
                if (total > MAX_JAR_BYTES) { res.body().close(); throw IllegalStateException("file too large") }
                val sha = MessageDigest.getInstance("SHA-256")
                var read = 0L
                res.body().use { input ->
                    Files.newOutputStream(part).use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            read += n
                            if (read > MAX_JAR_BYTES) throw IllegalStateException("file too large")
                            sha.update(buf, 0, n)
                            out.write(buf, 0, n)
                            if (total > 0) downloadProgress = (read.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
                if (release.assetSize > 0 && read != release.assetSize) throw IllegalStateException("size mismatch")
                if (release.assetSha256.isNotEmpty() && hex(sha.digest()) != release.assetSha256) throw IllegalStateException("checksum mismatch")
                verifyJar(part, release)

                val staged = stagingDir.resolve(release.assetName)
                Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                synchronized(lock) {
                    state.stagedFile?.let(Path::of)?.takeIf { it != staged }?.let { runCatching { Files.deleteIfExists(it) } }
                    state.stagedVersion = release.version
                    state.stagedFile = staged.toString()
                    save()
                }
                downloadProgress = 1f
                downloadState = DownloadState.STAGED
            } catch (t: Throwable) {
                runCatching { Files.deleteIfExists(part) }
                Debug.LOGGER.warn("[FishMod updater] download failed: {}", t.toString())
                downloadError = when {
                    t is java.net.http.HttpTimeoutException -> "Timed out"
                    t is java.io.IOException -> "Network error"
                    else -> t.message ?: "Download failed"
                }
                downloadState = DownloadState.FAILED
            }
        }
    }

    private fun verifyJar(file: Path, release: Release) {
        ZipFile(file.toFile()).use { zip ->
            val entry = zip.getEntry("fabric.mod.json") ?: throw IllegalStateException("not a Fabric mod")
            val meta = zip.getInputStream(entry).reader().use { JsonParser.parseReader(it).asJsonObject }
            if (meta.str("id") != MOD_ID) throw IllegalStateException("wrong mod id")
            val v = SemVer.parse(meta.str("version"), mcVersion)
            if (v == null || v != SemVer.parse(release.version, mcVersion)) throw IllegalStateException("version mismatch")
        }
    }

    private fun currentJar(): Path? {
        val origin = container?.origin ?: return null
        if (origin.kind != ModOrigin.Kind.PATH) return null
        val path = origin.paths.singleOrNull() ?: return null
        return path.takeIf { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") }
    }

    // Runs in a separate JDK-only JVM so the locked jar can be swapped once this process has exited.
    private fun launchInstaller() {
        if (downloadState != DownloadState.STAGED) return
        try {
            val staged = synchronized(lock) { state.stagedFile }?.let(Path::of)?.takeIf(Files::isRegularFile) ?: return
            val oldJar = currentJar() ?: return
            val java = ProcessHandle.current().info().command().orElse(null) ?: return
            val classDir = stagingDir.resolve("installer")
            val classFile = classDir.resolve("fishmod/utils/update/UpdateInstaller.class")
            Files.createDirectories(classFile.parent)
            UpdateManager::class.java.getResourceAsStream("/fishmod/utils/update/UpdateInstaller.class")?.use {
                Files.copy(it, classFile, StandardCopyOption.REPLACE_EXISTING)
            } ?: return
            ProcessBuilder(
                java, "-Xmx32m", "-cp", classDir.toString(), "fishmod.utils.update.UpdateInstaller",
                ProcessHandle.current().pid().toString(), oldJar.toString(), staged.toString(),
                oldJar.resolveSibling(staged.fileName.toString()).toString(),
            ).redirectErrorStream(true).redirectOutput(stagingDir.resolve("installer.log").toFile()).start()
        } catch (t: Throwable) {
            Debug.LOGGER.warn("[FishMod updater] could not launch installer: {}", t.toString())
        }
    }

    private fun load() {
        try {
            if (Files.isRegularFile(stateFile)) {
                Files.newBufferedReader(stateFile).use { state = gson.fromJson(it, State::class.java) ?: State() }
            }
        } catch (t: Throwable) {
            Debug.LOGGER.warn("[FishMod updater] resetting unreadable state: {}", t.toString())
            state = State()
        }
    }

    private fun save() {
        try {
            Files.createDirectories(stateFile.parent)
            val tmp = stateFile.resolveSibling("updater.json.tmp")
            Files.writeString(tmp, gson.toJson(state))
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (t: Throwable) {
            Debug.LOGGER.warn("[FishMod updater] could not save state: {}", t.toString())
        }
    }

    private fun JsonObject.str(k: String): String = get(k)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
    private fun JsonObject.bool(k: String): Boolean = get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
