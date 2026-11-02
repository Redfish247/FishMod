/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.neu

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.api.PvExecutors
import fishmod.utils.Http
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Official Hypixel SkyBlock resource pack (custom `hypixel_skyblock:` item textures).
 * Cached under `config/fishmod/pv/skyblock-pack`.
 */
object SkyBlockPackCache {
	private const val PACK_VERSION = "v2"

	/** Known dump of the Hypixel SkyBlock pack; mirrors keep first-run icons working offline of Hypixel later. */
	private val PACK_URLS = arrayOf(
		"https://resourcepacks.hypixel.net/SkyBlock/5c59e0a9-9865-4d4e-91d2-915515672cbd/84.zip",
		"https://github.com/EnderNon/skyblockpack-dump/raw/main/resourcepacks.hypixel.net/SkyBlock/5c59e0a9-9865-4d4e-91d2-915515672cbd/84.zip"
	)
	private val TIMEOUT: Duration = Duration.ofSeconds(120)
	private val HTTP = Http.REDIRECT
	private val EXECUTOR = PvExecutors.IO
	private const val NS = "hypixel_skyblock:"

	private val STARTED = AtomicBoolean(false)
	private val LOCK = Object()

	@Volatile
	private var ready = false

	// Lets blocking waiters return instead of sitting out the full timeout after a failed load.
	@Volatile
	private var loadFailed = false

	/** `.../extracted/assets/hypixel_skyblock` */
	@Volatile
	private var assetsRoot: Path? = null

	@JvmStatic
	fun start() {
		if (!STARTED.compareAndSet(false, true)) return
		EXECUTOR.execute(::ensureReadySafely)
	}

	@JvmStatic
	fun ensureReadyBlocking() {
		start()
		synchronized(LOCK) {
			val deadline = System.currentTimeMillis() + 180_000L
			while (!ready && !loadFailed && System.currentTimeMillis() < deadline) {
				try {
					(LOCK as Object).wait(500L)
				} catch (_: InterruptedException) {
					Thread.currentThread().interrupt()
					return
				}
			}
		}
	}

	@JvmStatic
	fun isReady(): Boolean {
		val root = assetsRoot
		return ready && root != null && Files.isDirectory(root)
	}

	/**
	 * Resolve GUI texture layers for an item model id such as
	 * `hypixel_skyblock:item/uncategorized/magma_chunk` or a composite drill / attuned dagger.
	 */
	@JvmStatic
	fun resolveTextureLayers(itemModel: String?): List<Path> {
		if (!isReady() || itemModel.isNullOrBlank()) return emptyList()
		val layers = LinkedHashSet<Path>()
		resolveRef(itemModel.trim(), layers, HashSet(), 0)
		return java.util.List.copyOf(layers)
	}

	private fun resolveRef(ref: String?, out: LinkedHashSet<Path>, seen: MutableSet<String>, depth: Int) {
		if (ref.isNullOrBlank() || depth > 16) return
		val key = ref.trim().lowercase(Locale.ROOT)
		if (!key.startsWith(NS)) return
		if (!seen.add(key)) return

		val rest = key.substring(NS.length)
		if (!rest.startsWith("item/")) return
		val root = assetsRoot ?: return

		val direct = root.resolve("textures").resolve("$rest.png")
		if (Files.isRegularFile(direct)) {
			out.add(direct)
			return
		}

		val itemDef = root.resolve("items").resolve("$rest.json")
		if (Files.isRegularFile(itemDef)) {
			val model = readJson(itemDef)?.get("model")
			if (model != null) {
				walkModelNode(model, out, seen, depth + 1)
				if (out.isNotEmpty()) return
			}
		}

		val modelDef = root.resolve("models").resolve("$rest.json")
		if (Files.isRegularFile(modelDef)) collectModelTextures(modelDef, out, seen, depth + 1)
	}

	private fun walkModelNode(element: JsonElement?, out: LinkedHashSet<Path>, seen: MutableSet<String>, depth: Int) {
		if (element == null || !element.isJsonObject || depth > 16) return
		val obj = element.asJsonObject
		val typeEl = obj.get("type")
		val type = if (typeEl != null && typeEl.isJsonPrimitive) {
			typeEl.asString.lowercase(Locale.ROOT).replace("minecraft:", "")
		} else {
			"model"
		}

		when (type) {
			"condition" -> {
				if (obj.has("on_false")) walkModelNode(obj.get("on_false"), out, seen, depth + 1)
				if (out.isEmpty() && obj.has("on_true")) walkModelNode(obj.get("on_true"), out, seen, depth + 1)
			}
			"composite" -> {
				obj.get("models")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { child ->
					walkModelNode(child, out, seen, depth + 1)
				}
			}
			"select" -> walkFallbackThenFirst(obj, "cases", out, seen, depth)
			"range_dispatch" -> walkFallbackThenFirst(obj, "entries", out, seen, depth)
			// "model" and unknown types both follow a primitive model ref
			else -> {
				val model = obj.get("model")
				if (model != null && model.isJsonPrimitive) resolveRef(model.asString, out, seen, depth + 1)
			}
		}
	}

	private fun walkFallbackThenFirst(
		obj: JsonObject,
		listKey: String,
		out: LinkedHashSet<Path>,
		seen: MutableSet<String>,
		depth: Int
	) {
		if (obj.has("fallback")) walkModelNode(obj.get("fallback"), out, seen, depth + 1)
		if (out.isNotEmpty()) return
		val list = obj.get(listKey)?.takeIf { it.isJsonArray }?.asJsonArray ?: return
		if (!list.isEmpty && list.get(0).isJsonObject) {
			val first = list.get(0).asJsonObject
			if (first.has("model")) walkModelNode(first.get("model"), out, seen, depth + 1)
		}
	}

	private fun collectModelTextures(modelDef: Path, out: LinkedHashSet<Path>, seen: MutableSet<String>, depth: Int) {
		val root = readJson(modelDef) ?: return
		val textures = root.get("textures")?.takeIf { it.isJsonObject }?.asJsonObject
		if (textures != null) {
			val keys = textures.keySet().sortedWith(compareBy<String>({ layerRank(it) }, { it }))
			for (key in keys) {
				val value = textures.get(key)
				if (value != null && value.isJsonPrimitive) resolveTextureId(value.asString, out, seen, depth)
			}
		}
		val parentEl = root.get("parent")
		if (out.isEmpty() && parentEl != null && parentEl.isJsonPrimitive) {
			val parent = parentEl.asString
			if (parent.lowercase(Locale.ROOT).startsWith(NS)) resolveRef(parent, out, seen, depth)
		}
	}

	private fun layerRank(key: String?): Int {
		if (key == null) return 100
		if (key == "layer0" || key == "particle") return 0
		if (key.startsWith("layer")) return key.substring(5).toIntOrNull() ?: 50
		return 40
	}

	private fun resolveTextureId(textureId: String?, out: LinkedHashSet<Path>, seen: MutableSet<String>, depth: Int) {
		if (textureId.isNullOrBlank()) return
		val root = assetsRoot ?: return
		val id = textureId.trim().lowercase(Locale.ROOT)
		if (id.startsWith(NS)) {
			val rest = id.substring(NS.length)
			val file = root.resolve("textures").resolve("$rest.png")
			if (Files.isRegularFile(file)) {
				out.add(file)
				return
			}
			// Sometimes texture ids omit the item/ prefix or point at another model.
			resolveRef(if (id.startsWith("${NS}item/")) id else "${NS}item/$rest", out, seen, depth + 1)
			return
		}
		if (!id.contains(":")) {
			val file = root.resolve("textures").resolve("item").resolve("$id.png")
			if (Files.isRegularFile(file)) out.add(file)
		}
	}

	private fun readJson(path: Path): JsonObject? = try {
		Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
			JsonParser.parseReader(reader)?.takeIf { it.isJsonObject }?.asJsonObject
		}
	} catch (_: Exception) {
		null
	}

	private fun ensureReadySafely() {
		try {
			ensureReady()
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("[FishPV] SkyBlock pack load failed: {}", exception.toString())
			synchronized(LOCK) {
				ready = false
				loadFailed = true
				(LOCK as Object).notifyAll()
			}
		}
	}

	private fun ensureReady() {
		val root = ProfileViewer.DIR.resolve("skyblock-pack")
		val zipPath = root.resolve("pack.zip")
		val extracted = root.resolve("extracted")
		val marker = root.resolve("pack.ok")
		Files.createDirectories(root)

		val markerOk = Files.isRegularFile(marker) && PACK_VERSION == Files.readString(marker).trim()
		if (!markerOk || !Files.isRegularFile(zipPath) || !hasAssets(extracted)) {
			if (!Files.isRegularFile(zipPath)) downloadPack(zipPath)
			extractAssets(zipPath, extracted)
			Files.writeString(marker, PACK_VERSION + "\n")
		}

		val assets = extracted.resolve("assets").resolve("hypixel_skyblock")
		if (!Files.isDirectory(assets)) throw IOException("SkyBlock pack missing hypixel_skyblock assets")
		synchronized(LOCK) {
			assetsRoot = assets
			ready = true
			(LOCK as Object).notifyAll()
		}
		ProfileViewer.LOGGER.info("[FishPV] SkyBlock pack assets ready at {}", assets)
	}

	private fun hasAssets(extracted: Path): Boolean {
		val base = extracted.resolve("assets").resolve("hypixel_skyblock")
		val sampleTex = base.resolve("textures").resolve("item").resolve("uncategorized").resolve("magma_chunk.png")
		val sampleModel = base.resolve("items").resolve("item").resolve("uncategorized").resolve("divans_drill.json")
		return Files.isRegularFile(sampleTex) && Files.isRegularFile(sampleModel)
	}

	private fun downloadPack(zipPath: Path) {
		val tmp = zipPath.resolveSibling("pack.zip.part")
		Files.deleteIfExists(tmp)
		var last: IOException? = null
		for (url in PACK_URLS) {
			try {
				val request = HttpRequest.newBuilder(URI.create(url))
					.timeout(TIMEOUT)
					.header("User-Agent", "FishMod-PV")
					.GET()
					.build()
				val response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream())
				if (response.statusCode() !in 200..299) throw IOException("HTTP " + response.statusCode() + " for " + url)
				response.body().use { input -> Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING) }
				try {
					Files.move(tmp, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
				} catch (_: IOException) {
					Files.move(tmp, zipPath, StandardCopyOption.REPLACE_EXISTING)
				}
				ProfileViewer.LOGGER.info("[FishPV] Downloaded SkyBlock pack from {}", url)
				return
			} catch (exception: IOException) {
				last = exception
				Files.deleteIfExists(tmp)
			}
		}
		throw last ?: IOException("No SkyBlock pack URL succeeded")
	}

	private fun extractAssets(zipPath: Path, extracted: Path) {
		val assets = extracted.resolve("assets").resolve("hypixel_skyblock")
		if (Files.exists(assets)) deleteRecursive(assets)
		Files.createDirectories(extracted)
		ZipInputStream(Files.newInputStream(zipPath)).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				if (entry.isDirectory) continue
				val name = entry.name.replace('\\', '/')
				val keep = name.startsWith("assets/hypixel_skyblock/textures/") ||
					name.startsWith("assets/hypixel_skyblock/models/") ||
					name.startsWith("assets/hypixel_skyblock/items/")
				if (!keep) continue
				val out = extracted.resolve(name).normalize()
				if (!out.startsWith(extracted.normalize())) throw IOException("Zip slip: $name")
				Files.createDirectories(out.parent)
				Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING)
			}
		}
	}

	private fun deleteRecursive(root: Path) {
		if (!Files.exists(root)) return
		Files.walk(root).use { walk ->
			walk.sorted(Comparator.reverseOrder()).forEach { path ->
				try {
					Files.deleteIfExists(path)
				} catch (_: IOException) {
				}
			}
		}
	}
}
