/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import com.mojang.blaze3d.platform.NativeImage;
import fishmod.pv.BetterPV;
import fishmod.pv.neu.SkyBlockPackCache;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Registers Hypixel SkyBlock item PNGs as dynamic GUI textures
 * (paper items, drills, attuned daggers, animated icons, …).
 */
public final class SkyBlockItemIconCache {
	private static final Map<String, IconState> BY_MODEL = new ConcurrentHashMap<>();

	private SkyBlockItemIconCache() {
	}

	public static void clear() {
		List<Identifier> ids = new java.util.ArrayList<>();
		for (IconState state : BY_MODEL.values()) {
			if (state.textureId != null) {
				ids.add(state.textureId);
			}
		}
		BY_MODEL.clear();
		Minecraft client = Minecraft.getInstance();
		if (client != null && !ids.isEmpty()) {
			// Free GPU textures; ids are deterministic so a re-request re-registers them.
			client.execute(() -> ids.forEach(client.getTextureManager()::release));
		}
	}

	/** Kick pack download; icons register as textures become available. */
	public static void ensurePack() {
		SkyBlockPackCache.start();
	}

	/**
	 * @param itemModel e.g. {@code hypixel_skyblock:item/uncategorized/magma_chunk}
	 * @return texture id when loaded, otherwise {@code null} (request may still be in flight)
	 */
	public static Identifier getOrRequest(String itemModel) {
		if (itemModel == null || itemModel.isBlank()) {
			return null;
		}
		String key = itemModel.trim().toLowerCase(Locale.ROOT);
		if (!key.startsWith("hypixel_skyblock:")) {
			return null;
		}
		IconState state = BY_MODEL.computeIfAbsent(key, IconState::new);
		if (state.textureId != null && state.loaded) {
			return state.textureId;
		}
		if (state.failed) {
			return null;
		}
		tryLoad(state);
		return state.loaded ? state.textureId : null;
	}

	public static int textureSize(String itemModel) {
		if (itemModel == null) {
			return 16;
		}
		IconState state = BY_MODEL.get(itemModel.trim().toLowerCase(Locale.ROOT));
		return state != null && state.size > 0 ? state.size : 16;
	}

	private static void tryLoad(IconState state) {
		if (!state.requested.compareAndSet(false, true)) {
			return;
		}
		SkyBlockPackCache.start();
		if (!SkyBlockPackCache.isReady()) {
			// Pack still downloading - allow another attempt later.
			state.requested.set(false);
			return;
		}
		List<Path> layers = SkyBlockPackCache.resolveTextureLayers(state.model);
		if (layers.isEmpty()) {
			state.failed = true;
			return;
		}
		NativeImage image;
		try {
			image = composeLayers(layers);
		} catch (IOException exception) {
			state.failed = true;
			BetterPV.LOGGER.warn("[BetterPV] Failed reading item icon {}: {}", state.model, exception.toString());
			return;
		}
		if (image == null) {
			state.failed = true;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			image.close();
			state.requested.set(false);
			return;
		}
		final NativeImage toRegister = image;
		client.execute(() -> {
			try {
				int size = Math.max(toRegister.getWidth(), toRegister.getHeight());
				Identifier id = Identifier.fromNamespaceAndPath(
					BetterPV.MOD_ID,
					"dynamic_items/" + UUID.nameUUIDFromBytes(state.model.getBytes(StandardCharsets.UTF_8))
				);
				client.getTextureManager().register(id, new DynamicTexture(() -> "BetterPV item " + state.model, toRegister));
				state.size = size;
				state.textureId = id;
				state.loaded = true;
			} catch (Exception exception) {
				toRegister.close();
				state.failed = true;
				BetterPV.LOGGER.warn("[BetterPV] Failed registering item icon {}: {}", state.model, exception.toString());
			}
		});
	}

	private static NativeImage composeLayers(List<Path> layers) throws IOException {
		NativeImage composed = null;
		for (Path layer : layers) {
			NativeImage frame;
			try (InputStream in = Files.newInputStream(layer)) {
				frame = firstFrame(NativeImage.read(in));
			}
			if (composed == null) {
				composed = new NativeImage(frame.getWidth(), frame.getHeight(), true);
				composed.fillRect(0, 0, composed.getWidth(), composed.getHeight(), 0);
			}
			blitLayer(composed, frame);
			frame.close();
		}
		return composed;
	}

	/** Animated item sheets are width×(N·width); keep the first frame for GUI icons. */
	private static NativeImage firstFrame(NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0) {
			return image;
		}
		if (height > width && height % width == 0) {
			NativeImage frame = new NativeImage(width, width, true);
			image.copyRect(frame, 0, 0, 0, 0, width, width, false, false);
			image.close();
			return frame;
		}
		if (width > height && width % height == 0) {
			NativeImage frame = new NativeImage(height, height, true);
			image.copyRect(frame, 0, 0, 0, 0, height, height, false, false);
			image.close();
			return frame;
		}
		return image;
	}

	private static void blitLayer(NativeImage target, NativeImage source) {
		int w = Math.min(target.getWidth(), source.getWidth());
		int h = Math.min(target.getHeight(), source.getHeight());
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int pixel = source.getPixel(x, y);
				int alpha = (pixel >>> 24) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				target.setPixel(x, y, pixel);
			}
		}
	}

	private static final class IconState {
		final String model;
		final AtomicBoolean requested = new AtomicBoolean();
		volatile Identifier textureId;
		volatile boolean loaded;
		volatile boolean failed;
		volatile int size = 16;

		IconState(String model) {
			this.model = model;
		}
	}
}
