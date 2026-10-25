/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.neu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Official Hypixel SkyBlock resource pack (custom {@code hypixel_skyblock:} item textures).
 * Cached under {@code ~/.betterpv/skyblock-pack}.
 */
public final class SkyBlockPackCache {
	private static final String PACK_VERSION = "v2";
	/** Known dump of the Hypixel SkyBlock pack; mirrors keep first-run icons working offline of Hypixel later. */
	private static final String[] PACK_URLS = {
		"https://resourcepacks.hypixel.net/SkyBlock/5c59e0a9-9865-4d4e-91d2-915515672cbd/84.zip",
		"https://github.com/EnderNon/skyblockpack-dump/raw/main/resourcepacks.hypixel.net/SkyBlock/5c59e0a9-9865-4d4e-91d2-915515672cbd/84.zip"
	};
	private static final Duration TIMEOUT = Duration.ofSeconds(120);
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.ALWAYS)
		.build();
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-SkyBlockPack");
		t.setDaemon(true);
		return t;
	});

	private static final AtomicBoolean STARTED = new AtomicBoolean(false);
	private static final Object LOCK = new Object();
	private static volatile boolean ready;
	// Lets blocking waiters return instead of sitting out the full timeout after a failed load.
	private static volatile boolean loadFailed;
	/** {@code .../extracted/assets/hypixel_skyblock} */
	private static volatile Path assetsRoot;

	private SkyBlockPackCache() {
	}

	public static void start() {
		if (!STARTED.compareAndSet(false, true)) {
			return;
		}
		EXECUTOR.execute(SkyBlockPackCache::ensureReadySafely);
	}

	public static void ensureReadyBlocking() {
		start();
		synchronized (LOCK) {
			long deadline = System.currentTimeMillis() + 180_000L;
			while (!ready && !loadFailed && System.currentTimeMillis() < deadline) {
				try {
					LOCK.wait(500L);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	public static boolean isReady() {
		return ready && assetsRoot != null && Files.isDirectory(assetsRoot);
	}

	/**
	 * Resolve GUI texture layers for an item model id such as
	 * {@code hypixel_skyblock:item/uncategorized/magma_chunk} or a composite drill / attuned dagger.
	 */
	public static List<Path> resolveTextureLayers(String itemModel) {
		if (!isReady() || itemModel == null || itemModel.isBlank()) {
			return List.of();
		}
		LinkedHashSet<Path> layers = new LinkedHashSet<>();
		resolveRef(itemModel.trim(), layers, new HashSet<>(), 0);
		return List.copyOf(layers);
	}

	private static void resolveRef(String ref, LinkedHashSet<Path> out, Set<String> seen, int depth) {
		if (ref == null || ref.isBlank() || depth > 16) {
			return;
		}
		String key = ref.trim().toLowerCase(Locale.ROOT);
		if (!key.startsWith("hypixel_skyblock:")) {
			return;
		}
		if (!seen.add(key)) {
			return;
		}

		String rest = key.substring("hypixel_skyblock:".length());
		if (!rest.startsWith("item/")) {
			return;
		}

		Path direct = assetsRoot.resolve("textures").resolve(rest + ".png");
		if (Files.isRegularFile(direct)) {
			out.add(direct);
			return;
		}

		Path itemDef = assetsRoot.resolve("items").resolve(rest + ".json");
		if (Files.isRegularFile(itemDef)) {
			JsonObject root = readJson(itemDef);
			if (root != null) {
				JsonElement model = root.get("model");
				if (model != null) {
					walkModelNode(model, out, seen, depth + 1);
					if (!out.isEmpty()) {
						return;
					}
				}
			}
		}

		Path modelDef = assetsRoot.resolve("models").resolve(rest + ".json");
		if (Files.isRegularFile(modelDef)) {
			collectModelTextures(modelDef, out, seen, depth + 1);
		}
	}

	private static void walkModelNode(JsonElement element, LinkedHashSet<Path> out, Set<String> seen, int depth) {
		if (element == null || !element.isJsonObject() || depth > 16) {
			return;
		}
		JsonObject obj = element.getAsJsonObject();
		String type = obj.has("type") && obj.get("type").isJsonPrimitive()
			? obj.get("type").getAsString().toLowerCase(Locale.ROOT).replace("minecraft:", "")
			: "model";

		switch (type) {
			case "model" -> {
				if (obj.has("model") && obj.get("model").isJsonPrimitive()) {
					resolveRef(obj.get("model").getAsString(), out, seen, depth + 1);
				}
			}
			case "condition" -> {
				if (obj.has("on_false")) {
					walkModelNode(obj.get("on_false"), out, seen, depth + 1);
				}
				if (out.isEmpty() && obj.has("on_true")) {
					walkModelNode(obj.get("on_true"), out, seen, depth + 1);
				}
			}
			case "composite" -> {
				if (obj.has("models") && obj.get("models").isJsonArray()) {
					for (JsonElement child : obj.getAsJsonArray("models")) {
						walkModelNode(child, out, seen, depth + 1);
					}
				}
			}
			case "select" -> {
				if (obj.has("fallback")) {
					walkModelNode(obj.get("fallback"), out, seen, depth + 1);
				}
				if (out.isEmpty() && obj.has("cases") && obj.get("cases").isJsonArray()) {
					JsonArray cases = obj.getAsJsonArray("cases");
					if (!cases.isEmpty() && cases.get(0).isJsonObject()) {
						JsonObject first = cases.get(0).getAsJsonObject();
						if (first.has("model")) {
							walkModelNode(first.get("model"), out, seen, depth + 1);
						}
					}
				}
			}
			case "range_dispatch" -> {
				if (obj.has("fallback")) {
					walkModelNode(obj.get("fallback"), out, seen, depth + 1);
				}
				if (out.isEmpty() && obj.has("entries") && obj.get("entries").isJsonArray()) {
					JsonArray entries = obj.getAsJsonArray("entries");
					if (!entries.isEmpty() && entries.get(0).isJsonObject()) {
						JsonObject first = entries.get(0).getAsJsonObject();
						if (first.has("model")) {
							walkModelNode(first.get("model"), out, seen, depth + 1);
						}
					}
				}
			}
			default -> {
				if (obj.has("model") && obj.get("model").isJsonPrimitive()) {
					resolveRef(obj.get("model").getAsString(), out, seen, depth + 1);
				}
			}
		}
	}

	private static void collectModelTextures(Path modelDef, LinkedHashSet<Path> out, Set<String> seen, int depth) {
		JsonObject root = readJson(modelDef);
		if (root == null) {
			return;
		}
		if (root.has("textures") && root.get("textures").isJsonObject()) {
			JsonObject textures = root.getAsJsonObject("textures");
			List<String> keys = new ArrayList<>();
			for (String key : textures.keySet()) {
				keys.add(key);
			}
			keys.sort((a, b) -> {
				int ra = layerRank(a);
				int rb = layerRank(b);
				if (ra != rb) {
					return Integer.compare(ra, rb);
				}
				return a.compareTo(b);
			});
			for (String key : keys) {
				JsonElement value = textures.get(key);
				if (value != null && value.isJsonPrimitive()) {
					resolveTextureId(value.getAsString(), out, seen, depth);
				}
			}
		}
		if (out.isEmpty() && root.has("parent") && root.get("parent").isJsonPrimitive()) {
			String parent = root.get("parent").getAsString();
			if (parent.toLowerCase(Locale.ROOT).startsWith("hypixel_skyblock:")) {
				resolveRef(parent, out, seen, depth);
			}
		}
	}

	private static int layerRank(String key) {
		if (key == null) {
			return 100;
		}
		if (key.equals("layer0") || key.equals("particle")) {
			return 0;
		}
		if (key.startsWith("layer")) {
			try {
				return Integer.parseInt(key.substring(5));
			} catch (NumberFormatException ignored) {
				return 50;
			}
		}
		return 40;
	}

	private static void resolveTextureId(String textureId, LinkedHashSet<Path> out, Set<String> seen, int depth) {
		if (textureId == null || textureId.isBlank()) {
			return;
		}
		String id = textureId.trim().toLowerCase(Locale.ROOT);
		if (id.startsWith("hypixel_skyblock:")) {
			String rest = id.substring("hypixel_skyblock:".length());
			Path file = assetsRoot.resolve("textures").resolve(rest + ".png");
			if (Files.isRegularFile(file)) {
				out.add(file);
				return;
			}
			// Sometimes texture ids omit the item/ prefix or point at another model.
			resolveRef(id.startsWith("hypixel_skyblock:item/") ? id : "hypixel_skyblock:item/" + rest, out, seen, depth + 1);
			return;
		}
		if (!id.contains(":")) {
			Path file = assetsRoot.resolve("textures").resolve("item").resolve(id + ".png");
			if (Files.isRegularFile(file)) {
				out.add(file);
			}
		}
	}

	private static JsonObject readJson(Path path) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement el = JsonParser.parseReader(reader);
			return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Exception exception) {
			return null;
		}
	}

	private static void ensureReadySafely() {
		try {
			ensureReady();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("[BetterPV] SkyBlock pack load failed: {}", exception.toString());
			synchronized (LOCK) {
				ready = false;
				loadFailed = true;
				LOCK.notifyAll();
			}
		}
	}

	private static void ensureReady() throws IOException, InterruptedException {
		Path root = Path.of(System.getProperty("user.home"), ".betterpv", "skyblock-pack");
		Path zipPath = root.resolve("pack.zip");
		Path extracted = root.resolve("extracted");
		Path marker = root.resolve("pack.ok");
		Files.createDirectories(root);

		boolean markerOk = Files.isRegularFile(marker) && PACK_VERSION.equals(Files.readString(marker).trim());
		if (!markerOk || !Files.isRegularFile(zipPath) || !hasAssets(extracted)) {
			if (!Files.isRegularFile(zipPath)) {
				downloadPack(zipPath);
			}
			extractAssets(zipPath, extracted);
			Files.writeString(marker, PACK_VERSION + "\n");
		}

		Path assets = extracted.resolve("assets").resolve("hypixel_skyblock");
		if (!Files.isDirectory(assets)) {
			throw new IOException("SkyBlock pack missing hypixel_skyblock assets");
		}
		synchronized (LOCK) {
			assetsRoot = assets;
			ready = true;
			LOCK.notifyAll();
		}
		BetterPV.LOGGER.info("[BetterPV] SkyBlock pack assets ready at {}", assets);
	}

	private static boolean hasAssets(Path extracted) {
		Path sampleTex = extracted.resolve("assets")
			.resolve("hypixel_skyblock")
			.resolve("textures")
			.resolve("item")
			.resolve("uncategorized")
			.resolve("magma_chunk.png");
		Path sampleModel = extracted.resolve("assets")
			.resolve("hypixel_skyblock")
			.resolve("items")
			.resolve("item")
			.resolve("uncategorized")
			.resolve("divans_drill.json");
		return Files.isRegularFile(sampleTex) && Files.isRegularFile(sampleModel);
	}

	private static void downloadPack(Path zipPath) throws IOException, InterruptedException {
		Path tmp = zipPath.resolveSibling("pack.zip.part");
		Files.deleteIfExists(tmp);
		IOException last = null;
		for (String url : PACK_URLS) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(TIMEOUT)
					.header("User-Agent", "BetterPV/" + BetterPV.MOD_ID)
					.GET()
					.build();
				HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					throw new IOException("HTTP " + response.statusCode() + " for " + url);
				}
				try (InputStream in = response.body()) {
					Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
				}
				try {
					Files.move(tmp, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException atomicFailed) {
					Files.move(tmp, zipPath, StandardCopyOption.REPLACE_EXISTING);
				}
				BetterPV.LOGGER.info("[BetterPV] Downloaded SkyBlock pack from {}", url);
				return;
			} catch (IOException exception) {
				last = exception;
				Files.deleteIfExists(tmp);
			}
		}
		throw last != null ? last : new IOException("No SkyBlock pack URL succeeded");
	}

	private static void extractAssets(Path zipPath, Path extracted) throws IOException {
		Path assets = extracted.resolve("assets").resolve("hypixel_skyblock");
		if (Files.exists(assets)) {
			deleteRecursive(assets);
		}
		Files.createDirectories(extracted);
		try (InputStream fileIn = Files.newInputStream(zipPath);
			 ZipInputStream zip = new ZipInputStream(fileIn)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				String name = entry.getName().replace('\\', '/');
				boolean keep = name.startsWith("assets/hypixel_skyblock/textures/")
					|| name.startsWith("assets/hypixel_skyblock/models/")
					|| name.startsWith("assets/hypixel_skyblock/items/");
				if (!keep) {
					continue;
				}
				Path out = extracted.resolve(name).normalize();
				if (!out.startsWith(extracted.normalize())) {
					throw new IOException("Zip slip: " + name);
				}
				Files.createDirectories(out.getParent());
				Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static void deleteRecursive(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (var walk = Files.walk(root)) {
			walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			});
		}
	}
}
