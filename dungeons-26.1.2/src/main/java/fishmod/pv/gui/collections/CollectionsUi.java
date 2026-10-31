/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections;

import fishmod.pv.data.BossCollections;
import fishmod.pv.data.CollectionIds;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared Collections / Minions UI helpers. */
public final class CollectionsUi {
	public static final int GAP = 8;
	public static final int PAD = 6;
	public static final int SLOT = 20;
	public static final int SLOT_GAP = 3;
	public static final int ITEM_ICON = 16;
	/**
	 * Collections: pick the largest crisp icon that fits without scrolling.
	 * Discrete sizes only - fractional scales look muddy.
	 */
	public static final int[] COLL_ICON_STEPS = {16, 14, 12, 10};
	public static final int MINION_UNLOCKED = 0xFF98E898;
	public static final int MINION_LOCKED = 0xFFFF9999;
	/** Hypixel SkyBlock heavy marks (not the thin ✓/✗). */
	public static final String MINION_TICK = "\u2714";
	public static final String MINION_CROSS = "\u2716";

	private CollectionsUi() {
	}

	// Only resolved hits are cached so a not-yet-loaded icon can still upgrade later.
	private static final java.util.Map<String, String> RESOLVED = new java.util.concurrent.ConcurrentHashMap<>();

	public static String resolveIconId(String collectionId) {
		if (collectionId == null) {
			return CollectionIds.iconId(collectionId);
		}
		String cached = RESOLVED.get(collectionId);
		if (cached != null) {
			return cached;
		}
		String resolved = resolveIconIdUncached(collectionId);
		if (resolved == null) {
			return CollectionIds.iconId(collectionId);
		}
		RESOLVED.put(collectionId, resolved);
		return resolved;
	}

	private static String resolveIconIdUncached(String collectionId) {
		if (BossCollections.isBossId(collectionId)) {
			for (BossCollections.BossDef boss : BossCollections.bosses()) {
				if (boss.id().equalsIgnoreCase(collectionId)) {
					return boss.iconId();
				}
			}
		}
		List<String> keys = new ArrayList<>();
		keys.add(CollectionIds.iconId(collectionId));
		keys.addAll(CollectionIds.lookupKeys(collectionId));
		for (String key : keys) {
			if (key == null || key.isBlank()) {
				continue;
			}
			if (SkyBlockIconRenderer.hasKnownIcon(key)) {
				return key;
			}
			ItemStack stack = SkyBlockItemFactory.iconStack(key);
			if (!stack.isEmpty() && !stack.is(Items.PAPER)) {
				return key;
			}
		}
		return null;
	}

	public static void drawVanillaIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		int draw = Math.max(1, Math.min(ITEM_ICON, size));
		if (draw == ITEM_ICON) {
			g.item(icon, x, y);
			return;
		}
		float scale = draw / (float) ITEM_ICON;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	public static void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, int size) {
		int draw = Math.min(ITEM_ICON, Math.max(1, size));
		SkyBlockIconRenderer.draw(g, id, x, y, draw);
	}

	public static void drawIcon(GuiGraphicsExtractor g, String id, int x, int y) {
		drawIcon(g, id, x, y, ITEM_ICON);
	}

	public static boolean scrollBy(double scrollY, int scroll, int maxScroll, int step, java.util.function.IntConsumer setter) {
		if (maxScroll <= 0) {
			return false;
		}
		int before = scroll;
		int next = scroll;
		if (scrollY > 0) {
			next = Math.max(0, scroll - step);
		} else if (scrollY < 0) {
			next = Math.min(maxScroll, scroll + step);
		}
		setter.accept(next);
		return next != before;
	}

	public static String trim(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}
}
