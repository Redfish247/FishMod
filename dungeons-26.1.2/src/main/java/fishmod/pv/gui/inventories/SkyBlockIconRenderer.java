/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Single draw path for SkyBlock icons.
 * Probes known {@code hypixel_skyblock:} models through {@code ItemModelResolver.appendItemLayers}
 * so remapper mixins can participate, then falls back to official Hypixel PNGs when missing.
 */
public final class SkyBlockIconRenderer {
	private static final Map<String, Boolean> RESOLVES_BY_MODEL = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> PROBING = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Boolean> LAST_MISSING = new ThreadLocal<>();

	private SkyBlockIconRenderer() {
	}

	public static void invalidateProbeCache() {
		RESOLVES_BY_MODEL.clear();
		DRAWN_BY_STACK.clear();
		DRAWN_BY_ID.clear();
	}

	/** Used by {@link fishmod.pv.mixin.ItemModelResolverIconProbeMixin}. */
	public static boolean isProbing() {
		return Boolean.TRUE.equals(PROBING.get());
	}

	/** Used by {@link fishmod.pv.mixin.ItemModelResolverIconProbeMixin}. */
	public static void recordProbeMissing(boolean missing) {
		if (isProbing()) {
			LAST_MISSING.set(missing);
		}
	}

	public static void draw(GuiGraphicsExtractor g, String skyblockId, int x, int y) {
		draw(g, skyblockId, null, x, y, 16);
	}

	public static void draw(GuiGraphicsExtractor g, String skyblockId, int x, int y, int size) {
		draw(g, skyblockId, null, x, y, size);
	}

	public static void draw(
		GuiGraphicsExtractor g,
		String skyblockId,
		String itemModelOverride,
		int x,
		int y,
		int size
	) {
		ItemStack stack = skyblockId == null || skyblockId.isBlank()
			? ItemStack.EMPTY
			: SkyBlockItemFactory.iconStack(skyblockId);
		draw(g, stack, skyblockId, itemModelOverride, x, y, size);
	}

	public static void draw(GuiGraphicsExtractor g, ItemStack stack, String skyblockId, int x, int y) {
		draw(g, stack, skyblockId, null, x, y, 16);
	}

	public static void draw(
		GuiGraphicsExtractor g,
		ItemStack stack,
		String skyblockId,
		int x,
		int y,
		int size
	) {
		draw(g, stack, skyblockId, null, x, y, size);
	}

	public static void draw(
		GuiGraphicsExtractor g,
		ItemStack stack,
		String skyblockId,
		String itemModelOverride,
		int x,
		int y,
		int size
	) {
		if (g == null) {
			return;
		}
		String model = resolveModelString(skyblockId, itemModelOverride);
		boolean usePack = model != null && resolvesWithoutMissing(model, skyblockId, stack);
		ItemStack drawn = cachedStack(stack, skyblockId, usePack ? model : null);
		boolean paper = drawn.isEmpty() || drawn.is(Items.PAPER);

		if (!usePack && paper) {
			Identifier png = officialTexture(skyblockId, model);
			if (png != null) {
				blitOfficial(g, png, model, skyblockId, x, y, size);
				return;
			}
		}

		drawStack(g, drawn, x, y, size);
	}

	/**
	 * Whether a SkyBlock id has a known model or official texture.
	 * Used when picking among alternate collection keys.
	 */
	public static boolean hasKnownIcon(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return false;
		}
		if (SkyBlockItemFactory.itemModel(skyblockId) != null) {
			return true;
		}
		return SkyBlockItemFactory.customIcon(skyblockId) != null;
	}

	static boolean resolvesWithoutMissing(String model, String skyblockId, ItemStack template) {
		if (model == null || model.isBlank()) {
			return false;
		}
		String key = model.trim().toLowerCase(Locale.ROOT);
		Boolean cached = RESOLVES_BY_MODEL.get(key);
		if (cached != null) {
			return cached;
		}
		Identifier modelId = Identifier.tryParse(model.trim());
		if (modelId == null) {
			RESOLVES_BY_MODEL.put(key, false);
			return false;
		}

		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getItemModelResolver() == null) {
			return false;
		}

		ItemStack probe = template != null && !template.isEmpty()
			? template.copy()
			: new ItemStack(Items.PAPER);
		applyHypixelModel(probe, modelId, skyblockId);

		PROBING.set(true);
		LAST_MISSING.remove();
		try {
			ItemStackRenderState state = new ItemStackRenderState();
			client.getItemModelResolver().appendItemLayers(
				state,
				probe,
				ItemDisplayContext.GUI,
				null,
				null,
				0
			);
			Boolean missing = LAST_MISSING.get();
			boolean ok = missing != null && !missing;
			RESOLVES_BY_MODEL.put(key, ok);
			return ok;
		} catch (Exception ignored) {
			RESOLVES_BY_MODEL.put(key, false);
			return false;
		} finally {
			PROBING.set(false);
			LAST_MISSING.remove();
		}
	}

	private static String resolveModelString(String skyblockId, String itemModelOverride) {
		if (itemModelOverride != null && !itemModelOverride.isBlank()) {
			if (skyblockId != null && !skyblockId.isBlank()) {
				SkyBlockItemFactory.customIconModel(skyblockId, itemModelOverride);
			}
			return itemModelOverride.trim();
		}
		return SkyBlockItemFactory.itemModel(skyblockId);
	}

	private static Identifier officialTexture(String skyblockId, String model) {
		if (model != null && !model.isBlank()) {
			Identifier byModel = SkyBlockItemIconCache.getOrRequest(model);
			if (byModel != null) {
				return byModel;
			}
		}
		return SkyBlockItemFactory.customIcon(skyblockId);
	}

	private static void blitOfficial(
		GuiGraphicsExtractor g,
		Identifier png,
		String model,
		String skyblockId,
		int x,
		int y,
		int size
	) {
		int tex = model != null
			? SkyBlockItemIconCache.textureSize(model)
			: SkyBlockItemFactory.customIconSize(skyblockId);
		int draw = Math.max(1, size);
		g.blit(RenderPipelines.GUI_TEXTURED, png, x, y, 0, 0, draw, draw, tex, tex, tex, tex);
	}

	private record Drawn(String id, String model, ItemStack stack) {
	}

	// Built stacks reused across frames; ItemStack has identity equality, so weak keys drop with their source.
	private static final Map<ItemStack, Drawn> DRAWN_BY_STACK = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final Map<String, ItemStack> DRAWN_BY_ID = new ConcurrentHashMap<>();

	private static ItemStack cachedStack(ItemStack stack, String skyblockId, String model) {
		if (stack != null && !stack.isEmpty()) {
			Drawn hit = DRAWN_BY_STACK.get(stack);
			if (hit != null && java.util.Objects.equals(hit.id(), skyblockId) && java.util.Objects.equals(hit.model(), model)) {
				return hit.stack();
			}
			ItemStack built = stackForPack(stack, skyblockId, model);
			DRAWN_BY_STACK.put(stack, new Drawn(skyblockId, model, built));
			return built;
		}
		String key = skyblockId + "|" + model;
		ItemStack hit = DRAWN_BY_ID.get(key);
		if (hit != null) {
			return hit;
		}
		ItemStack built = stackForPack(stack, skyblockId, model);
		// Paper stand-ins may still upgrade once NEU loads, so only freeze real items.
		if (!built.is(Items.PAPER) && DRAWN_BY_ID.size() < 4096) {
			DRAWN_BY_ID.put(key, built);
		}
		return built;
	}

	private static ItemStack stackForPack(ItemStack stack, String skyblockId, String model) {
		ItemStack drawn;
		if (stack != null && !stack.isEmpty()) {
			drawn = stack.copy();
		} else if (skyblockId != null && !skyblockId.isBlank()) {
			drawn = SkyBlockItemFactory.iconStack(skyblockId);
		} else {
			drawn = new ItemStack(Items.PAPER);
		}
		if (drawn.isEmpty()) {
			drawn = new ItemStack(Items.PAPER);
		}
		Identifier modelId = model == null || model.isBlank() ? null : Identifier.tryParse(model.trim());
		if (modelId != null) {
			applyHypixelModel(drawn, modelId, skyblockId);
		} else {
			SkyBlockItemFactory.applySkyBlockIdentity(drawn, skyblockId);
		}
		return drawn;
	}

	private static void applyHypixelModel(ItemStack stack, Identifier modelId, String skyblockId) {
		stack.set(DataComponents.ITEM_MODEL, modelId);
		SkyBlockItemFactory.applySkyBlockIdentity(stack, skyblockId);
	}

	private static void drawStack(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		int draw = Math.max(1, size);
		if (draw == 16) {
			g.item(stack, x, y);
			return;
		}
		float scale = draw / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}
}
