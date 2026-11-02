/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.neu

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.api.PvExecutors
import fishmod.pv.data.AttributeShardsData
import fishmod.pv.data.BestiaryData
import fishmod.pv.data.ForagingHotfData
import fishmod.pv.data.HoppityRabbitsData
import fishmod.pv.data.MiningHotmData
import fishmod.pv.data.TrophyFishData
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.utils.Http
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import kotlin.math.max

/**
 * NotEnoughUpdates-REPO item definitions (skull textures, leather colors, itemids).
 * Full zip is cached under `config/fishmod/pv/neu-repo`; individual items can be fetched on demand.
 */
object NeuRepoCache {
	private const val REPO_USER = "NotEnoughUpdates"
	private const val REPO_NAME = "NotEnoughUpdates-REPO"
	private const val REPO_BRANCH = "master"
	private const val RAW_ITEM =
		"https://raw.githubusercontent.com/$REPO_USER/$REPO_NAME/$REPO_BRANCH/items/"
	private val TIMEOUT: Duration = Duration.ofSeconds(90)
	private val ITEM_TIMEOUT: Duration = Duration.ofSeconds(8)
	private const val REFRESH_HOURS = 24L
	private const val UPDATED_AT = "updated-at.txt"

	private val HTTP = Http.REDIRECT
	private val EXECUTOR = PvExecutors.IO
	private val SCHEDULER = PvExecutors.SCHEDULER

	private val ITEMS: MutableMap<String, JsonObject> = ConcurrentHashMap()
	private val MISS: MutableMap<String, Boolean> = ConcurrentHashMap()
	private val FULL_LOAD_STARTED = AtomicBoolean(false)
	private val REFRESH_IN_FLIGHT = AtomicBoolean(false)
	private val FULL_LOAD_LOCK = Object()

	@Volatile
	private var fullLoadDone = false

	@JvmStatic
	fun start() {
		if (!FULL_LOAD_STARTED.compareAndSet(false, true)) return
		EXECUTOR.execute(::loadFullRepoSafely)
		SCHEDULER.scheduleAtFixedRate(::refreshIfStaleSafely, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS)
	}

	/** Wait for the zip index (or finish a sync load) before warming item stacks. */
	@JvmStatic
	fun ensureLoadedBlocking() {
		start()
		synchronized(FULL_LOAD_LOCK) {
			val deadline = System.currentTimeMillis() + 120_000L
			while (!fullLoadDone && System.currentTimeMillis() < deadline) {
				try {
					(FULL_LOAD_LOCK as Object).wait(500L)
				} catch (_: InterruptedException) {
					Thread.currentThread().interrupt()
					return
				}
			}
		}
	}

	@JvmStatic
	fun isReady(): Boolean = ITEMS.isNotEmpty()

	@JvmStatic
	fun size(): Int = ITEMS.size

	/** Sack name → item ids (from NEU `constants/sacks.json`). */
	@JvmStatic
	fun sackDefinitions(): Map<String, List<String>> {
		ensureSacksLoaded()
		return sacks
	}

	/**
	 * NEU skull / item id for a sack display name (e.g. `Agronomy` → `LARGE_AGRONOMY_SACK`).
	 * Also accepts titles like `Rune Sack`.
	 */
	@JvmStatic
	fun sackItemId(sackName: String?): String? {
		ensureSacksLoaded()
		if (sackName.isNullOrBlank()) return null
		sackItems[sackName]?.let { return it }
		if (sackName.regionMatches(sackName.length - 5, " Sack", 0, 5, ignoreCase = true)) {
			return sackItems[sackName.substring(0, sackName.length - 5)]
		}
		return sackItems.entries.firstOrNull { it.key.equals(sackName, ignoreCase = true) }?.value
	}

	/** All NEU sack icon item ids (for prefetch). */
	@JvmStatic
	fun sackItemIds(): Set<String> {
		ensureSacksLoaded()
		return java.util.Set.copyOf(sackItems.values)
	}

	/** Minion internal name (e.g. `WHEAT_GENERATOR`) → max tier from NEU `constants/misc.json`. */
	@JvmStatic
	fun minionMaxTiers(): Map<String, Int> {
		ensureMinionsLoaded()
		return minions
	}

	/** Accessory id → every higher tier it upgrades into (NEU `misc.json` `talisman_upgrades`). */
	@JvmStatic
	fun talismanUpgrades(): Map<String, List<String>> {
		ensureTalismansLoaded()
		return TALISMAN_UPGRADES
	}

	/** Accessories NEU excludes from missing-accessory lists (`misc.json` `ignored_talisman`). */
	@JvmStatic
	fun ignoredTalismans(): Set<String> {
		ensureTalismansLoaded()
		return IGNORED_TALISMANS
	}

	@Volatile
	private var abiphoneContacts: JsonObject? = null

	/** Contact display name → `requirement` / `callNames` (NEU `constants/abiphone.json`). */
	@JvmStatic
	fun abiphoneContacts(): JsonObject {
		abiphoneContacts?.let { return it }
		val path = constantsPath("abiphone.json") ?: return JsonObject()
		return try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				JsonParser.parseReader(reader).asJsonObject.also { abiphoneContacts = it }
			}
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.debug("Failed loading NEU abiphone.json", exception)
			JsonObject()
		}
	}

	private val TALISMAN_UPGRADES: MutableMap<String, List<String>> = ConcurrentHashMap()
	private val IGNORED_TALISMANS: MutableSet<String> = ConcurrentHashMap.newKeySet()

	@Volatile
	private var talismansLoaded = false

	private fun ensureTalismansLoaded() {
		if (talismansLoaded) return
		synchronized(TALISMAN_UPGRADES) {
			if (talismansLoaded) return
			val path = constantsPath("misc.json") ?: return
			try {
				Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
					val root = JsonParser.parseReader(reader).asJsonObject
					root.get("talisman_upgrades")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (key, value) ->
						if (!value.isJsonArray) return@forEach
						val upgrades = value.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString.uppercase(Locale.ROOT) }
						TALISMAN_UPGRADES[key.uppercase(Locale.ROOT)] = java.util.List.copyOf(upgrades)
					}
					root.get("ignored_talisman")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
						if (el.isJsonPrimitive) IGNORED_TALISMANS.add(el.asString.uppercase(Locale.ROOT))
					}
					talismansLoaded = true
				}
			} catch (exception: Exception) {
				ProfileViewer.LOGGER.debug("Failed loading NEU misc.json talismans", exception)
			}
		}
	}

	// Immutable snapshots swapped whole: parse threads iterate them while a repo update reloads.
	@Volatile
	private var sacks: Map<String, List<String>> = emptyMap()

	/** Sack display name → NEU item id (player-head sack skins). */
	@Volatile
	private var sackItems: Map<String, String> = emptyMap()

	@Volatile
	private var minions: Map<String, Int> = emptyMap()
	private val SACKS_LOCK = Any()
	private val MINIONS_LOCK = Any()

	private fun ensureSacksLoaded() {
		if (sacks.isNotEmpty() && sackItems.isNotEmpty()) return
		synchronized(SACKS_LOCK) {
			if (sacks.isNotEmpty() && sackItems.isNotEmpty()) return
			loadSacksFromDisk()
		}
	}

	private fun ensureMinionsLoaded() {
		if (minions.isNotEmpty()) return
		synchronized(MINIONS_LOCK) {
			if (minions.isNotEmpty()) return
			loadMinionsFromDisk()
		}
	}

	private fun loadSacksFromDisk() {
		val path = constantsPath("sacks.json") ?: return
		val sacksOut = LinkedHashMap<String, List<String>>()
		val sackItemsOut = LinkedHashMap<String, String>()
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				val root = JsonParser.parseReader(reader).asJsonObject
				val sackDefs = root.get("sacks")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
				for ((key, value) in sackDefs.entrySet()) {
					if (!value.isJsonObject) continue
					val def = value.asJsonObject
					def.get("contents")?.takeIf { it.isJsonArray }?.asJsonArray?.let { arr ->
						val contents = arr.filter { it.isJsonPrimitive }.map { it.asString }
						if (contents.isNotEmpty()) sacksOut[key] = java.util.List.copyOf(contents)
					}
					val itemEl = def.get("item")
					if (itemEl != null && itemEl.isJsonPrimitive) {
						val item = itemEl.asString
						if (!item.isNullOrBlank()) sackItemsOut[key] = item
					}
				}
				sacks = Collections.unmodifiableMap(sacksOut)
				sackItems = Collections.unmodifiableMap(sackItemsOut)
			}
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.debug("Failed loading NEU sacks.json", exception)
		}
	}

	private fun loadMinionsFromDisk() {
		val path = constantsPath("misc.json") ?: return
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				val root = JsonParser.parseReader(reader).asJsonObject
				val minionsObj = root.get("minions")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
				val minionsOut = LinkedHashMap<String, Int>()
				for ((key, value) in minionsObj.entrySet()) {
					if (!value.isJsonPrimitive) continue
					try {
						val maxTier = value.asInt
						if (maxTier > 0) minionsOut[key.uppercase(Locale.ROOT)] = maxTier
					} catch (_: Exception) {
					}
				}
				minions = Collections.unmodifiableMap(minionsOut)
			}
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.debug("Failed loading NEU misc.json minions", exception)
		}
	}

	private fun constantsPath(fileName: String): Path? {
		var path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", fileName)
		if (Files.isRegularFile(path)) return path
		start()
		path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", fileName)
		return if (Files.isRegularFile(path)) path else null
	}

	@JvmStatic
	fun get(internalName: String?): JsonObject? {
		if (internalName.isNullOrBlank()) return null
		return ITEMS[internalName.uppercase(Locale.ROOT)]
	}

	@Volatile
	private var displayNameIndex: Map<String, List<String>> = emptyMap()

	@Volatile
	private var displayNameIndexSize = -1

	private val FORMATTING = Regex("§.")

	/**
	 * Item id whose NEU display name (formatting stripped) matches exactly, ignoring case.
	 * Null when unknown or when several items share the name.
	 */
	@JvmStatic
	fun idForDisplayName(name: String?): String? = idsForDisplayName(name).singleOrNull()

	/** Every item id sharing that NEU display name (formatting stripped, case ignored), sorted. */
	@JvmStatic
	fun idsForDisplayName(name: String?): List<String> {
		if (name.isNullOrBlank()) return emptyList()
		val size = ITEMS.size
		if (size != displayNameIndexSize) {
			val index = HashMap<String, MutableList<String>>()
			for ((id, item) in ITEMS) {
				val display = item.get("displayname")
				if (display == null || !display.isJsonPrimitive) continue
				val key = display.asString.replace(FORMATTING, "").trim().lowercase(Locale.ROOT)
				if (key.isNotEmpty()) index.getOrPut(key) { ArrayList() }.add(id)
			}
			displayNameIndex = index.mapValues { (_, ids) -> ids.sorted() }
			displayNameIndexSize = size
		}
		return displayNameIndex[name.trim().lowercase(Locale.ROOT)] ?: emptyList()
	}

	/** Prefetch many ids (off-thread). Safe to call from API/parse threads. */
	@JvmStatic
	fun prefetch(ids: Collection<String?>?) {
		if (ids.isNullOrEmpty()) return
		for (id in ids) {
			if (id.isNullOrBlank()) continue
			val key = id.uppercase(Locale.ROOT)
			if (ITEMS.containsKey(key) || MISS.containsKey(key)) continue
			EXECUTOR.execute { fetchOne(key) }
		}
	}

	/**
	 * Blocking lookup for a single item (used when painting before the full zip is indexed).
	 * Prefer [prefetch] + [get] on the render thread.
	 */
	@JvmStatic
	fun getOrFetch(internalName: String?): JsonObject? {
		get(internalName)?.let { return it }
		if (internalName.isNullOrBlank()) return null
		val key = internalName.uppercase(Locale.ROOT)
		if (MISS.containsKey(key)) return null
		return fetchOne(key)
	}

	private fun fetchOne(key: String): JsonObject? {
		ITEMS[key]?.let { return it }
		if (MISS.containsKey(key)) return null
		return try {
			val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20")
			val request = HttpRequest.newBuilder(URI.create("$RAW_ITEM$encoded.json"))
				.timeout(ITEM_TIMEOUT)
				.GET()
				.build()
			val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
			if (response.statusCode() == 404) {
				MISS[key] = true
				return null
			}
			val body = response.body()
			if (response.statusCode() !in 200..299 || body.isNullOrBlank()) return null
			JsonParser.parseString(body).asJsonObject.also { ITEMS[key] = it }
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.debug("NEU item fetch failed for {}", key, exception)
			null
		}
	}

	private fun loadFullRepoSafely() {
		try {
			loadFullRepo()
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("Failed to load NEU-REPO zip (on-demand item fetch still works)", exception)
		} finally {
			synchronized(FULL_LOAD_LOCK) {
				fullLoadDone = true
				(FULL_LOAD_LOCK as Object).notifyAll()
			}
		}
	}

	private fun loadFullRepo() {
		val base = ProfileViewer.DIR.resolve("neu-repo")
		Files.createDirectories(base)
		val repoRoot = base.resolve("repo")
		val diskOk = Files.isDirectory(repoRoot) && Files.isDirectory(repoRoot.resolve("items"))
		if (!diskOk) {
			ProfileViewer.LOGGER.info("Downloading NotEnoughUpdates-REPO…")
			downloadAndExtract(base, repoRoot)
			writeUpdatedAt(base)
		}
		if (!Files.isDirectory(repoRoot.resolve("items"))) return
		val before = ITEMS.size
		val indexed = indexRepoItems(repoRoot)
		ProfileViewer.LOGGER.info("NEU-REPO ready ({} items, +{})", indexed, max(0, indexed - before))
		SkyBlockItemFactory.clearCache()
		if (needsRefresh(base)) refreshInBackground(base, repoRoot)
	}

	private fun refreshIfStaleSafely() {
		try {
			val base = ProfileViewer.DIR.resolve("neu-repo")
			val repoRoot = base.resolve("repo")
			if (!Files.isDirectory(repoRoot.resolve("items")) || !needsRefresh(base)) return
			refreshInBackground(base, repoRoot)
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("Failed to check NEU-REPO for updates", exception)
		}
	}

	private fun refreshInBackground(base: Path, repoRoot: Path) {
		if (!REFRESH_IN_FLIGHT.compareAndSet(false, true)) return
		EXECUTOR.execute {
			try {
				ProfileViewer.LOGGER.info("Updating NotEnoughUpdates-REPO…")
				downloadAndExtract(base, repoRoot)
				writeUpdatedAt(base)
				val indexed = indexRepoItems(repoRoot)
				reloadDependentCaches()
				ProfileViewer.LOGGER.info("NEU-REPO updated ({} items)", indexed)
			} catch (exception: Exception) {
				ProfileViewer.LOGGER.warn("Failed to update NEU-REPO (using cached copy)", exception)
			} finally {
				REFRESH_IN_FLIGHT.set(false)
			}
		}
	}

	private fun needsRefresh(base: Path): Boolean {
		val repoRoot = base.resolve("repo")
		if (!Files.isDirectory(repoRoot.resolve("items"))) return false
		val marker = base.resolve(UPDATED_AT)
		// Existing installs from before auto-update never wrote a marker.
		if (!Files.isRegularFile(marker)) return true
		return try {
			val updated = Files.readString(marker, StandardCharsets.UTF_8).trim().toLong()
			System.currentTimeMillis() - updated > Duration.ofHours(REFRESH_HOURS).toMillis()
		} catch (_: Exception) {
			true
		}
	}

	private fun writeUpdatedAt(base: Path) {
		Files.writeString(
			base.resolve(UPDATED_AT),
			System.currentTimeMillis().toString(),
			StandardCharsets.UTF_8,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING
		)
	}

	private fun indexRepoItems(repoRoot: Path): Int {
		val next = ConcurrentHashMap<String, JsonObject>()
		Files.walk(repoRoot.resolve("items")).use { paths ->
			paths.filter { it.toString().endsWith(".json") }.forEach { path ->
				try {
					Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
						val obj = JsonParser.parseReader(reader).asJsonObject
						val internal = if (obj.has("internalname")) {
							obj.get("internalname").asString
						} else {
							path.fileName.toString().replace(".json", "")
						}
						next[internal.uppercase(Locale.ROOT)] = obj
					}
				} catch (_: Exception) {
				}
			}
		}
		// Overwrite then prune, so readers never see an empty map mid-reload.
		ITEMS.putAll(next)
		ITEMS.keys.retainAll(next.keys)
		MISS.clear()
		return next.size
	}

	private fun reloadDependentCaches() {
		synchronized(SACKS_LOCK) {
			sacks = emptyMap()
			sackItems = emptyMap()
		}
		synchronized(MINIONS_LOCK) {
			minions = emptyMap()
		}
		BestiaryData.reload()
		AttributeShardsData.reload()
		HoppityRabbitsData.reload()
		TrophyFishData.reload()
		ForagingHotfData.reload()
		MiningHotmData.reload()
		SkyBlockItemFactory.clearCache()
	}

	private fun downloadAndExtract(base: Path, repoRoot: Path) {
		val mirrors = arrayOf(
			URI.create("https://codeload.github.com/$REPO_USER/$REPO_NAME/zip/refs/heads/$REPO_BRANCH"),
			URI.create("https://github.com/$REPO_USER/$REPO_NAME/archive/refs/heads/$REPO_BRANCH.zip")
		)
		val zipPath = base.resolve("repo.zip")
		var last: IOException? = null
		for (zipUri in mirrors) {
			try {
				val request = HttpRequest.newBuilder(zipUri).timeout(TIMEOUT).GET().build()
				val response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(zipPath))
				if (response.statusCode() !in 200..299) {
					last = IOException("zip HTTP " + response.statusCode() + " from " + zipUri)
					continue
				}
				val extractRoot = base.resolve("extract")
				deleteRecursive(extractRoot)
				Files.createDirectories(extractRoot)
				unzip(zipPath, extractRoot)
				val nested = findRepoContentRoot(extractRoot)
				deleteRecursive(repoRoot)
				Files.createDirectories(repoRoot.parent)
				Files.move(nested, repoRoot, StandardCopyOption.REPLACE_EXISTING)
				deleteRecursive(extractRoot)
				Files.deleteIfExists(zipPath)
				return
			} catch (exception: IOException) {
				last = exception
			}
		}
		if (last != null) throw last
	}

	private fun findRepoContentRoot(extractRoot: Path): Path =
		Files.list(extractRoot).use { stream -> stream.filter { Files.isDirectory(it) }.findFirst().orElse(extractRoot) }

	private fun unzip(zipPath: Path, targetDir: Path) {
		ZipInputStream(Files.newInputStream(zipPath)).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				val out = targetDir.resolve(entry.name).normalize()
				if (!out.startsWith(targetDir)) throw IOException("Zip slip: " + entry.name)
				if (entry.isDirectory) {
					Files.createDirectories(out)
				} else {
					Files.createDirectories(out.parent)
					Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}
	}

	private fun deleteRecursive(root: Path?) {
		if (root == null || !Files.exists(root)) return
		Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
			override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
				Files.deleteIfExists(file)
				return FileVisitResult.CONTINUE
			}

			override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
				Files.deleteIfExists(dir)
				return FileVisitResult.CONTINUE
			}
		})
	}
}
