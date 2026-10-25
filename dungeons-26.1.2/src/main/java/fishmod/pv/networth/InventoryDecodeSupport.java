/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.InventorySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared JSON and UI helpers for inventory decoding. */
final class InventoryDecodeSupport {
	private InventoryDecodeSupport() {
	}

	static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	static Integer jsonInt(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
			return null;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (Exception ignored) {
			return null;
		}
	}

	static Integer tryParseInt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ignored) {
			String digits = value.replaceAll("\\D+", "");
			if (digits.isEmpty()) {
				return null;
			}
			try {
				return Integer.parseInt(digits);
			} catch (NumberFormatException ignored2) {
				return null;
			}
		}
	}

	static int compareKeys(String a, String b) {
		Integer ai = tryParseInt(a);
		Integer bi = tryParseInt(b);
		if (ai != null && bi != null) {
			return Integer.compare(ai, bi);
		}
		return a.compareToIgnoreCase(b);
	}

	static String normalizeUuid(String uuid) {
		return uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
	}

	static List<InventorySnapshot.Slot> toUiSlots(List<InventoryDecoder.Stack> stacks) {
		List<InventorySnapshot.Slot> out = new ArrayList<>(stacks.size());
		for (InventoryDecoder.Stack stack : stacks) {
			out.add(toUiSlot(stack));
		}
		return out;
	}

	static InventorySnapshot.Slot toUiSlot(InventoryDecoder.Stack stack) {
		if (stack == null) {
			return null;
		}
		return new InventorySnapshot.Slot(
			stack.id(),
			stack.count(),
			stack.lore(),
			stack.displayName(),
			stack.dyeColor(),
			stack.skullValue(),
			stack.skullSignature(),
			stack.extraAttributes(),
			stack.soulbound()
		);
	}

	static List<InventorySnapshot.Page> chunkPages(
		List<InventorySnapshot.Slot> slots,
		int pageSize,
		int columns,
		String titlePrefix
	) {
		if (slots.isEmpty()) {
			return List.of(InventorySnapshot.emptyPage(titlePrefix, columns));
		}
		int totalPages = (slots.size() + pageSize - 1) / pageSize;
		List<InventorySnapshot.Page> pages = new ArrayList<>();
		for (int start = 0; start < slots.size(); start += pageSize) {
			int end = Math.min(slots.size(), start + pageSize);
			int pageIndex = start / pageSize + 1;
			String title = totalPages <= 1 ? titlePrefix : titlePrefix + " " + pageIndex;
			pages.add(new InventorySnapshot.Page(title, slots.subList(start, end), columns));
		}
		return pages;
	}

	static String prettyWords(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}
}
