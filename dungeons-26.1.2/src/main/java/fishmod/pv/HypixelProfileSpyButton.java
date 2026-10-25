/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Injects a command-block "Open BetterPV" control into Hypixel's right-click
 * player menu (empty slot under Co-op Request / next to Ender Chest).
 */
public final class HypixelProfileSpyButton {
	private static final Pattern TITLE_PROFILE = Pattern.compile("^([A-Za-z0-9_]{3,16})'s Profile$");
	private static final Pattern TITLE_NAME = Pattern.compile("^([A-Za-z0-9_]{3,16})$");
	private static final int SLOT = 16;
	// Built once on first draw instead of every frame.
	private static ItemStack icon;

	private HypixelProfileSpyButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) {
				return;
			}
			if (resolveTarget(screen) == null) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((scr, click) -> {
				Target target = resolveTarget(scr);
				if (target == null || click == null) {
					return true;
				}
				double mx = click.x();
				double my = click.y();
				if (mx >= target.x && mx < target.x + SLOT && my >= target.y && my < target.y + SLOT) {
					ProfileViewerOpener.open(target.player);
					return false;
				}
				return true;
			});
			ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, delta) -> {
				Target target = resolveTarget(scr);
				if (target == null) {
					return;
				}
				drawSlot(graphics, scr.getFont(), target, mouseX, mouseY);
			});
		});
	}

	private static Target resolveTarget(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return null;
		}
		String player = playerName(screen);
		Integer left = intField(container, "leftPos", "field_2776");
		Integer top = intField(container, "topPos", "field_2800");
		if (left == null || top == null) {
			return null;
		}
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null) {
			return null;
		}
		List<Slot> slots = menu.slots;
		if (slots == null || slots.isEmpty()) {
			return null;
		}

		Slot ender = null;
		Slot coop = null;
		String headName = null;
		for (Slot slot : slots) {
			if (slot == null || !slot.isActive()) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (stack.is(Items.ENDER_CHEST)) {
				ender = slot;
			}
			String name = stack.getHoverName().getString();
			if (name != null && name.equalsIgnoreCase("Co-op Request")) {
				coop = slot;
			}
			if (headName == null && stack.is(Items.PLAYER_HEAD) && name != null) {
				Matcher hm = TITLE_NAME.matcher(name.trim());
				if (hm.matches()) {
					headName = hm.group(1);
				}
			}
		}
		if (ender == null && coop == null) {
			return null;
		}
		if (player == null) {
			player = headName;
		}
		if (player == null) {
			return null;
		}

		Slot empty = null;
		if (ender != null) {
			empty = findEmptyBeside(slots, ender, 1, 0);
		}
		if (empty == null && coop != null) {
			empty = findEmptyBeside(slots, coop, 0, 1);
		}
		if (empty == null) {
			return null;
		}
		return new Target(player, left + empty.x, top + empty.y);
	}

	private static Slot findEmptyBeside(List<Slot> slots, Slot origin, int dxSlots, int dySlots) {
		int wantX = origin.x + dxSlots * 18;
		int wantY = origin.y + dySlots * 18;
		for (Slot slot : slots) {
			if (slot == null || slot == origin || !slot.isActive()) {
				continue;
			}
			if (slot.x == wantX && slot.y == wantY && slot.getItem().isEmpty()) {
				return slot;
			}
		}
		// Fallback: nearest empty slot to the right/below within 2 slot steps.
		Slot best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Slot slot : slots) {
			if (slot == null || slot == origin || !slot.isActive()) {
				continue;
			}
			if (!slot.getItem().isEmpty()) {
				continue;
			}
			int dx = slot.x - origin.x;
			int dy = slot.y - origin.y;
			if (dxSlots > 0 && (dx <= 0 || dy != 0)) {
				continue;
			}
			if (dySlots > 0 && (dy <= 0 || dx != 0)) {
				continue;
			}
			int dist = Math.abs(dx) + Math.abs(dy);
			if (dist > 0 && dist < bestDist && dist <= 36) {
				bestDist = dist;
				best = slot;
			}
		}
		return best;
	}

	private static String playerName(Screen screen) {
		Component title = screen.getTitle();
		if (title == null) {
			return null;
		}
		String plain = title.getString();
		if (plain == null || plain.isBlank()) {
			return null;
		}
		String trimmed = plain.trim();
		Matcher m = TITLE_PROFILE.matcher(trimmed);
		if (!m.matches()) {
			m = TITLE_NAME.matcher(trimmed);
			if (!m.matches()) {
				return null;
			}
		}
		String name = m.group(1);
		if ("npc".equalsIgnoreCase(name) || name.toLowerCase(Locale.ROOT).startsWith("cit-")) {
			return null;
		}
		return name;
	}

	private static Integer intField(Object target, String... names) {
		Class<?> c = target.getClass();
		while (c != null && c != Object.class) {
			for (String name : names) {
				try {
					Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					Object v = f.get(target);
					if (v instanceof Integer i) {
						return i;
					}
					if (v instanceof Number n) {
						return n.intValue();
					}
				} catch (ReflectiveOperationException ignored) {
				}
			}
			c = c.getSuperclass();
		}
		// Mojmap accessor fallbacks (getLeftPos / leftPos via method).
		for (String methodName : new String[] {"getGuiLeft", "getLeftPos"}) {
			try {
				Method method = target.getClass().getMethod(methodName);
				Object v = method.invoke(target);
				if (v instanceof Number n) {
					return n.intValue();
				}
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return null;
	}

	private static void drawSlot(GuiGraphicsExtractor g, Font font, Target target, int mouseX, int mouseY) {
		int x = target.x;
		int y = target.y;
		boolean hover = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
		ItemStack stack = icon;
		if (stack == null) {
			stack = new ItemStack(Items.COMMAND_BLOCK);
			stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.literal("Open BetterPV").withColor(0x55FF55));
			icon = stack;
		}
		g.item(stack, x, y);
		if (hover) {
			int tipX = x + SLOT + 8;
			int tipY = y;
			String title = "Open BetterPV";
			String body = "View " + target.player + "'s profile";
			int tw = Math.max(font.width(title), font.width(body));
			int pad = 3;
			int boxW = tw + pad * 2;
			int boxH = font.lineHeight * 2 + pad * 2 + 2;
			g.fill(tipX - 1, tipY - 1, tipX + boxW + 1, tipY + boxH + 1, 0xFF000000);
			g.fill(tipX, tipY, tipX + boxW, tipY + boxH, 0xF0101018);
			g.text(font, Component.literal(title).withColor(0x55FF55), tipX + pad, tipY + pad, 0xFFFFFFFF, false);
			g.text(font, Component.literal(body).withColor(0xFFFF55), tipX + pad, tipY + pad + font.lineHeight + 2, 0xFFFFFFFF, false);
		}
	}

	private record Target(String player, int x, int y) {
	}
}
