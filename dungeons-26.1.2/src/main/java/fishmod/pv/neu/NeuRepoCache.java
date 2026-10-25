/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.neu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.data.AttributeShardsData;
import fishmod.pv.data.BestiaryData;
import fishmod.pv.data.ForagingHotfData;
import fishmod.pv.data.HoppityRabbitsData;
import fishmod.pv.data.MiningHotmData;
import fishmod.pv.data.TrophyFishData;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * NotEnoughUpdates-REPO item definitions (skull textures, leather colors, itemids).
 * Full zip is cached under {@code ~/.betterpv/neu-repo}; individual items can be fetched on demand.
 */
public final class NeuRepoCache {
	private static final String REPO_USER = "NotEnoughUpdates";
	private static final String REPO_NAME = "NotEnoughUpdates-REPO";
	private static final String REPO_BRANCH = "master";
	private static final String RAW_ITEM =
		"https://raw.githubusercontent.com/" + REPO_USER + "/" + REPO_NAME + "/" + REPO_BRANCH + "/items/";
	private static final Duration TIMEOUT = Duration.ofSeconds(90);
	private static final Duration ITEM_TIMEOUT = Duration.ofSeconds(8);
	private static final long REFRESH_HOURS = 24L;
	private static final String UPDATED_AT = "updated-at.txt";

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.ALWAYS)
		.build();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-NeuRepo");
		t.setDaemon(true);
		return t;
	});
	private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-NeuRepoRefresh");
		t.setDaemon(true);
		return t;
	});

	private static final Map<String, JsonObject> ITEMS = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> MISS = new ConcurrentHashMap<>();
	private static final AtomicBoolean FULL_LOAD_STARTED = new AtomicBoolean(false);
	private static final AtomicBoolean REFRESH_IN_FLIGHT = new AtomicBoolean(false);
	private static final Object FULL_LOAD_LOCK = new Object();
	private static volatile boolean fullLoadDone;

	private NeuRepoCache() {
	}

	public static void start() {
		if (!FULL_LOAD_STARTED.compareAndSet(false, true)) {
			return;
		}
		EXECUTOR.execute(NeuRepoCache::loadFullRepoSafely);
		SCHEDULER.scheduleAtFixedRate(
			NeuRepoCache::refreshIfStaleSafely, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS
		);
	}

	/** Wait for the zip index (or finish a sync load) before warming item stacks. */
	public static void ensureLoadedBlocking() {
		start();
		synchronized (FULL_LOAD_LOCK) {
			long deadline = System.currentTimeMillis() + 120_000L;
			while (!fullLoadDone && System.currentTimeMillis() < deadline) {
				try {
					FULL_LOAD_LOCK.wait(500L);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	public static boolean isReady() {
		return !ITEMS.isEmpty();
	}

	public static int size() {
		return ITEMS.size();
	}

	/** Sack name → item ids (from NEU {@code constants/sacks.json}). */
	public static Map<String, List<String>> sackDefinitions() {
		ensureSacksLoaded();
		return SACKS;
	}

	/**
	 * NEU skull / item id for a sack display name (e.g. {@code Agronomy} → {@code LARGE_AGRONOMY_SACK}).
	 * Also accepts titles like {@code Rune Sack}.
	 */
	public static String sackItemId(String sackName) {
		ensureSacksLoaded();
		if (sackName == null || sackName.isBlank()) {
			return null;
		}
		String direct = SACK_ITEMS.get(sackName);
		if (direct != null) {
			return direct;
		}
		if (sackName.regionMatches(true, sackName.length() - 5, " Sack", 0, 5)) {
			return SACK_ITEMS.get(sackName.substring(0, sackName.length() - 5));
		}
		for (var entry : SACK_ITEMS.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(sackName)) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** All NEU sack icon item ids (for prefetch). */
	public static Set<String> sackItemIds() {
		ensureSacksLoaded();
		return Set.copyOf(SACK_ITEMS.values());
	}

	/** Minion internal name (e.g. {@code WHEAT_GENERATOR}) → max tier from NEU {@code constants/misc.json}. */
	public static Map<String, Integer> minionMaxTiers() {
		ensureMinionsLoaded();
		return MINIONS;
	}

	/** Accessory id → every higher tier it upgrades into (NEU {@code misc.json} {@code talisman_upgrades}). */
	public static Map<String, List<String>> talismanUpgrades() {
		ensureTalismansLoaded();
		return TALISMAN_UPGRADES;
	}

	/** Accessories NEU excludes from missing-accessory lists ({@code misc.json} {@code ignored_talisman}). */
	public static Set<String> ignoredTalismans() {
		ensureTalismansLoaded();
		return IGNORED_TALISMANS;
	}

	private static volatile JsonObject abiphoneContacts;

	/** Contact display name → {@code requirement} / {@code callNames} (NEU {@code constants/abiphone.json}). */
	public static JsonObject abiphoneContacts() {
		JsonObject cached = abiphoneContacts;
		if (cached != null) {
			return cached;
		}
		Path path = constantsPath("abiphone.json");
		if (path == null) {
			return new JsonObject();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			cached = JsonParser.parseReader(reader).getAsJsonObject();
			abiphoneContacts = cached;
			return cached;
		} catch (Exception exception) {
			BetterPV.LOGGER.debug("Failed loading NEU abiphone.json", exception);
			return new JsonObject();
		}
	}

	private static final Map<String, List<String>> TALISMAN_UPGRADES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Set<String> IGNORED_TALISMANS = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static volatile boolean talismansLoaded;

	private static void ensureTalismansLoaded() {
		if (talismansLoaded) {
			return;
		}
		synchronized (TALISMAN_UPGRADES) {
			if (talismansLoaded) {
				return;
			}
			Path path = constantsPath("misc.json");
			if (path == null) {
				return;
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("talisman_upgrades") && root.get("talisman_upgrades").isJsonObject()) {
					for (var entry : root.getAsJsonObject("talisman_upgrades").entrySet()) {
						if (!entry.getValue().isJsonArray()) {
							continue;
						}
						List<String> upgrades = new java.util.ArrayList<>();
						for (var el : entry.getValue().getAsJsonArray()) {
							if (el.isJsonPrimitive()) {
								upgrades.add(el.getAsString().toUpperCase(Locale.ROOT));
							}
						}
						TALISMAN_UPGRADES.put(entry.getKey().toUpperCase(Locale.ROOT), List.copyOf(upgrades));
					}
				}
				if (root.has("ignored_talisman") && root.get("ignored_talisman").isJsonArray()) {
					for (var el : root.getAsJsonArray("ignored_talisman")) {
						if (el.isJsonPrimitive()) {
							IGNORED_TALISMANS.add(el.getAsString().toUpperCase(Locale.ROOT));
						}
					}
				}
				talismansLoaded = true;
			} catch (Exception exception) {
				BetterPV.LOGGER.debug("Failed loading NEU misc.json talismans", exception);
			}
		}
	}

	// Immutable snapshots swapped whole: parse threads iterate them while a repo update reloads.
	private static volatile Map<String, List<String>> SACKS = Map.of();
	/** Sack display name → NEU item id (player-head sack skins). */
	private static volatile Map<String, String> SACK_ITEMS = Map.of();
	private static volatile Map<String, Integer> MINIONS = Map.of();
	private static final Object SACKS_LOCK = new Object();
	private static final Object MINIONS_LOCK = new Object();

	private static void ensureSacksLoaded() {
		if (!SACKS.isEmpty() && !SACK_ITEMS.isEmpty()) {
			return;
		}
		synchronized (SACKS_LOCK) {
			if (!SACKS.isEmpty() && !SACK_ITEMS.isEmpty()) {
				return;
			}
			loadSacksFromDisk();
		}
	}

	private static void ensureMinionsLoaded() {
		if (!MINIONS.isEmpty()) {
			return;
		}
		synchronized (MINIONS_LOCK) {
			if (!MINIONS.isEmpty()) {
				return;
			}
			loadMinionsFromDisk();
		}
	}

	private static void loadSacksFromDisk() {
		Path path = constantsPath("sacks.json");
		if (path == null) {
			return;
		}
		Map<String, List<String>> sacksOut = new java.util.LinkedHashMap<>();
		Map<String, String> sackItemsOut = new java.util.LinkedHashMap<>();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject sacks = root.has("sacks") && root.get("sacks").isJsonObject()
				? root.getAsJsonObject("sacks")
				: root;
			for (var entry : sacks.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject def = entry.getValue().getAsJsonObject();
				if (def.has("contents") && def.get("contents").isJsonArray()) {
					List<String> contents = new java.util.ArrayList<>();
					for (var el : def.getAsJsonArray("contents")) {
						if (el.isJsonPrimitive()) {
							contents.add(el.getAsString());
						}
					}
					if (!contents.isEmpty()) {
						sacksOut.put(entry.getKey(), List.copyOf(contents));
					}
				}
				if (def.has("item") && def.get("item").isJsonPrimitive()) {
					String item = def.get("item").getAsString();
					if (item != null && !item.isBlank()) {
						sackItemsOut.put(entry.getKey(), item);
					}
				}
			}
			SACKS = java.util.Collections.unmodifiableMap(sacksOut);
			SACK_ITEMS = java.util.Collections.unmodifiableMap(sackItemsOut);
		} catch (Exception exception) {
			BetterPV.LOGGER.debug("Failed loading NEU sacks.json", exception);
		}
	}

	private static void loadMinionsFromDisk() {
		Path path = constantsPath("misc.json");
		if (path == null) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (!root.has("minions") || !root.get("minions").isJsonObject()) {
				return;
			}
			Map<String, Integer> minionsOut = new java.util.LinkedHashMap<>();
			for (var entry : root.getAsJsonObject("minions").entrySet()) {
				if (!entry.getValue().isJsonPrimitive()) {
					continue;
				}
				try {
					int max = entry.getValue().getAsInt();
					if (max > 0) {
						minionsOut.put(entry.getKey().toUpperCase(Locale.ROOT), max);
					}
				} catch (Exception ignored) {
				}
			}
			MINIONS = java.util.Collections.unmodifiableMap(minionsOut);
		} catch (Exception exception) {
			BetterPV.LOGGER.debug("Failed loading NEU misc.json minions", exception);
		}
	}

	private static Path constantsPath(String fileName) {
		Path path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "repo", "constants", fileName);
		if (Files.isRegularFile(path)) {
			return path;
		}
		start();
		path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "repo", "constants", fileName);
		return Files.isRegularFile(path) ? path : null;
	}

	public static JsonObject get(String internalName) {
		if (internalName == null || internalName.isBlank()) {
			return null;
		}
		return ITEMS.get(internalName.toUpperCase(Locale.ROOT));
	}

	private static volatile Map<String, List<String>> displayNameIndex = Map.of();
	private static volatile int displayNameIndexSize = -1;

	/**
	 * Item id whose NEU display name (formatting stripped) matches exactly, ignoring case.
	 * Null when unknown or when several items share the name.
	 */
	public static String idForDisplayName(String name) {
		List<String> ids = idsForDisplayName(name);
		return ids.size() == 1 ? ids.get(0) : null;
	}

	/** Every item id sharing that NEU display name (formatting stripped, case ignored), sorted. */
	public static List<String> idsForDisplayName(String name) {
		if (name == null || name.isBlank()) {
			return List.of();
		}
		int size = ITEMS.size();
		if (size != displayNameIndexSize) {
			Map<String, List<String>> index = new java.util.HashMap<>();
			for (var entry : ITEMS.entrySet()) {
				JsonObject item = entry.getValue();
				if (item == null || !item.has("displayname") || !item.get("displayname").isJsonPrimitive()) {
					continue;
				}
				String key = item.get("displayname").getAsString()
					.replaceAll("\u00a7.", "").trim().toLowerCase(Locale.ROOT);
				if (!key.isEmpty()) {
					index.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(entry.getKey());
				}
			}
			index.replaceAll((k, v) -> v.stream().sorted().toList());
			displayNameIndex = index;
			displayNameIndexSize = size;
		}
		return displayNameIndex.getOrDefault(name.trim().toLowerCase(Locale.ROOT), List.of());
	}

	/** Prefetch many ids (off-thread). Safe to call from API/parse threads. */
	public static void prefetch(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		for (String id : ids) {
			if (id == null || id.isBlank()) {
				continue;
			}
			String key = id.toUpperCase(Locale.ROOT);
			if (ITEMS.containsKey(key) || MISS.containsKey(key)) {
				continue;
			}
			EXECUTOR.execute(() -> fetchOne(key));
		}
	}

	/**
	 * Blocking lookup for a single item (used when painting before the full zip is indexed).
	 * Prefer {@link #prefetch} + {@link #get} on the render thread.
	 */
	public static JsonObject getOrFetch(String internalName) {
		JsonObject cached = get(internalName);
		if (cached != null) {
			return cached;
		}
		if (internalName == null || internalName.isBlank()) {
			return null;
		}
		String key = internalName.toUpperCase(Locale.ROOT);
		if (MISS.containsKey(key)) {
			return null;
		}
		return fetchOne(key);
	}

	private static JsonObject fetchOne(String key) {
		JsonObject existing = ITEMS.get(key);
		if (existing != null) {
			return existing;
		}
		if (MISS.containsKey(key)) {
			return null;
		}
		try {
			String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
			HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_ITEM + encoded + ".json"))
				.timeout(ITEM_TIMEOUT)
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 404) {
				MISS.put(key, true);
				return null;
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
				return null;
			}
			JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
			ITEMS.put(key, object);
			return object;
		} catch (Exception exception) {
			BetterPV.LOGGER.debug("NEU item fetch failed for {}", key, exception);
			return null;
		}
	}

	private static void loadFullRepoSafely() {
		try {
			loadFullRepo();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to load NEU-REPO zip (on-demand item fetch still works)", exception);
		} finally {
			synchronized (FULL_LOAD_LOCK) {
				fullLoadDone = true;
				FULL_LOAD_LOCK.notifyAll();
			}
		}
	}

	private static void loadFullRepo() throws IOException, InterruptedException {
		Path base = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo");
		Files.createDirectories(base);
		Path repoRoot = base.resolve("repo");
		boolean diskOk = Files.isDirectory(repoRoot) && Files.isDirectory(repoRoot.resolve("items"));
		if (!diskOk) {
			BetterPV.LOGGER.info("Downloading NotEnoughUpdates-REPO…");
			downloadAndExtract(base, repoRoot);
			writeUpdatedAt(base);
		}
		if (!Files.isDirectory(repoRoot.resolve("items"))) {
			return;
		}
		int before = ITEMS.size();
		int indexed = indexRepoItems(repoRoot);
		BetterPV.LOGGER.info("NEU-REPO ready ({} items, +{})", indexed, Math.max(0, indexed - before));
		fishmod.pv.gui.inventories.SkyBlockItemFactory.clearCache();
		if (needsRefresh(base)) {
			refreshInBackground(base, repoRoot);
		}
	}

	private static void refreshIfStaleSafely() {
		try {
			Path base = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo");
			Path repoRoot = base.resolve("repo");
			if (!Files.isDirectory(repoRoot.resolve("items")) || !needsRefresh(base)) {
				return;
			}
			refreshInBackground(base, repoRoot);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to check NEU-REPO for updates", exception);
		}
	}

	private static void refreshInBackground(Path base, Path repoRoot) {
		if (!REFRESH_IN_FLIGHT.compareAndSet(false, true)) {
			return;
		}
		EXECUTOR.execute(() -> {
			try {
				BetterPV.LOGGER.info("Updating NotEnoughUpdates-REPO…");
				downloadAndExtract(base, repoRoot);
				writeUpdatedAt(base);
				int indexed = indexRepoItems(repoRoot);
				reloadDependentCaches();
				BetterPV.LOGGER.info("NEU-REPO updated ({} items)", indexed);
			} catch (Exception exception) {
				BetterPV.LOGGER.warn("Failed to update NEU-REPO (using cached copy)", exception);
			} finally {
				REFRESH_IN_FLIGHT.set(false);
			}
		});
	}

	private static boolean needsRefresh(Path base) {
		Path repoRoot = base.resolve("repo");
		if (!Files.isDirectory(repoRoot.resolve("items"))) {
			return false;
		}
		Path marker = base.resolve(UPDATED_AT);
		if (!Files.isRegularFile(marker)) {
			// Existing installs from before auto-update never wrote a marker.
			return true;
		}
		try {
			long updated = Long.parseLong(Files.readString(marker, StandardCharsets.UTF_8).trim());
			return System.currentTimeMillis() - updated > Duration.ofHours(REFRESH_HOURS).toMillis();
		} catch (Exception ignored) {
			return true;
		}
	}

	private static void writeUpdatedAt(Path base) throws IOException {
		Files.writeString(
			base.resolve(UPDATED_AT),
			Long.toString(System.currentTimeMillis()),
			StandardCharsets.UTF_8,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING
		);
	}

	private static int indexRepoItems(Path repoRoot) throws IOException {
		Map<String, JsonObject> next = new ConcurrentHashMap<>();
		try (var paths = Files.walk(repoRoot.resolve("items"))) {
			paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
					String internal = object.has("internalname")
						? object.get("internalname").getAsString()
						: path.getFileName().toString().replace(".json", "");
					next.put(internal.toUpperCase(Locale.ROOT), object);
				} catch (Exception ignored) {
				}
			});
		}
		// Overwrite then prune, so readers never see an empty map mid-reload.
		ITEMS.putAll(next);
		ITEMS.keySet().retainAll(next.keySet());
		MISS.clear();
		return next.size();
	}

	private static void reloadDependentCaches() {
		synchronized (SACKS_LOCK) {
			SACKS = Map.of();
			SACK_ITEMS = Map.of();
		}
		synchronized (MINIONS_LOCK) {
			MINIONS = Map.of();
		}
		BestiaryData.reload();
		AttributeShardsData.reload();
		HoppityRabbitsData.reload();
		TrophyFishData.reload();
		ForagingHotfData.reload();
		MiningHotmData.reload();
		fishmod.pv.gui.inventories.SkyBlockItemFactory.clearCache();
	}

	private static void downloadAndExtract(Path base, Path repoRoot) throws IOException, InterruptedException {
		URI[] mirrors = {
			URI.create("https://codeload.github.com/" + REPO_USER + "/" + REPO_NAME + "/zip/refs/heads/" + REPO_BRANCH),
			URI.create("https://github.com/" + REPO_USER + "/" + REPO_NAME + "/archive/refs/heads/" + REPO_BRANCH + ".zip")
		};
		Path zipPath = base.resolve("repo.zip");
		IOException last = null;
		for (URI zipUri : mirrors) {
			try {
				HttpRequest request = HttpRequest.newBuilder(zipUri).timeout(TIMEOUT).GET().build();
				HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(zipPath));
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					last = new IOException("zip HTTP " + response.statusCode() + " from " + zipUri);
					continue;
				}
				Path extractRoot = base.resolve("extract");
				deleteRecursive(extractRoot);
				Files.createDirectories(extractRoot);
				unzip(zipPath, extractRoot);
				Path nested = findRepoContentRoot(extractRoot);
				deleteRecursive(repoRoot);
				Files.createDirectories(repoRoot.getParent());
				Files.move(nested, repoRoot, StandardCopyOption.REPLACE_EXISTING);
				deleteRecursive(extractRoot);
				Files.deleteIfExists(zipPath);
				return;
			} catch (IOException exception) {
				last = exception;
			}
		}
		if (last != null) {
			throw last;
		}
	}

	private static Path findRepoContentRoot(Path extractRoot) throws IOException {
		try (var stream = Files.list(extractRoot)) {
			return stream.filter(Files::isDirectory).findFirst().orElse(extractRoot);
		}
	}

	private static void unzip(Path zipPath, Path targetDir) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				Path out = targetDir.resolve(entry.getName()).normalize();
				if (!out.startsWith(targetDir)) {
					throw new IOException("Zip slip: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(out);
				} else {
					Files.createDirectories(out.getParent());
					Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteRecursive(Path root) throws IOException {
		if (root == null || !Files.exists(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.deleteIfExists(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
