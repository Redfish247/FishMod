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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sole owner of ThreadLocal NBT decode caches for {@link InventoryDecoder}.
 * Nested {@link #withSharedDecode} calls reuse the outer cache.
 */
final class InventoryDecodeCache {
	private static final ThreadLocal<Map<String, List<InventoryDecoder.Stack>>> DECODE_KEEPING_EMPTY_CACHE = new ThreadLocal<>();
	private static final ThreadLocal<Map<String, List<InventoryDecoder.Stack>>> DECODE_COMPACT_CACHE = new ThreadLocal<>();

	private InventoryDecodeCache() {
	}

	static <T> T withSharedDecode(Supplier<T> work) {
		boolean outer = DECODE_KEEPING_EMPTY_CACHE.get() == null;
		if (outer) {
			DECODE_KEEPING_EMPTY_CACHE.set(new HashMap<>());
			DECODE_COMPACT_CACHE.set(new HashMap<>());
		}
		try {
			return work.get();
		} finally {
			if (outer) {
				DECODE_KEEPING_EMPTY_CACHE.remove();
				DECODE_COMPACT_CACHE.remove();
			}
		}
	}

	static void withSharedDecode(Runnable work) {
		withSharedDecode(() -> {
			work.run();
			return null;
		});
	}

	static List<InventoryDecoder.Stack> decodeKeepingEmpty(String encoded, int minSlots) throws IOException {
		Map<String, List<InventoryDecoder.Stack>> cache = DECODE_KEEPING_EMPTY_CACHE.get();
		if (cache != null) {
			List<InventoryDecoder.Stack> natural = cache.get(encoded);
			if (natural == null) {
				natural = NbtInventoryCodec.decodeKeepingEmptyUncached(encoded, 0);
				cache.put(encoded, natural);
			}
			return padSlots(natural, minSlots);
		}
		return NbtInventoryCodec.decodeKeepingEmptyUncached(encoded, minSlots);
	}

	static List<InventoryDecoder.Stack> decode(String encoded) throws IOException {
		Map<String, List<InventoryDecoder.Stack>> cache = DECODE_COMPACT_CACHE.get();
		if (cache != null) {
			List<InventoryDecoder.Stack> cached = cache.get(encoded);
			if (cached != null) {
				return cached;
			}
		}
		if (DECODE_KEEPING_EMPTY_CACHE.get() != null) {
			List<InventoryDecoder.Stack> compact = compactNonNull(decodeKeepingEmpty(encoded, 0));
			if (cache != null) {
				cache.put(encoded, compact);
			}
			return compact;
		}
		List<InventoryDecoder.Stack> decoded = NbtInventoryCodec.decodeUncached(encoded);
		if (cache != null) {
			cache.put(encoded, decoded);
		}
		return decoded;
	}

	static List<InventoryDecoder.Stack> decodeField(JsonObject container, String field) {
		if (container == null || field == null) {
			return List.of();
		}
		return decodeDataElement(container.get(field));
	}

	static List<InventoryDecoder.Stack> decodeDataElement(JsonElement element) {
		String data = extractData(element);
		if (data.isBlank()) {
			return List.of();
		}
		try {
			return decode(data);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	static List<InventoryDecoder.Stack> decodeFieldKeepingEmpty(JsonObject container, String field, int minSlots) {
		if (container == null || field == null) {
			return emptySlots(minSlots);
		}
		return decodeDataElementKeepingEmpty(container.get(field), minSlots);
	}

	static List<InventoryDecoder.Stack> decodeDataElementKeepingEmpty(JsonElement element, int minSlots) {
		String data = extractData(element);
		if (data.isBlank()) {
			return emptySlots(minSlots);
		}
		try {
			return decodeKeepingEmpty(data, minSlots);
		} catch (Exception ignored) {
			return emptySlots(minSlots);
		}
	}

	static String extractData(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("data") && object.get("data").isJsonPrimitive()) {
				return object.get("data").getAsString();
			}
		}
		return "";
	}

	static List<InventoryDecoder.Stack> padSlots(List<InventoryDecoder.Stack> slots, int minSlots) {
		if (slots == null) {
			return emptySlots(minSlots);
		}
		if (minSlots <= slots.size()) {
			return slots;
		}
		List<InventoryDecoder.Stack> out = new ArrayList<>(minSlots);
		out.addAll(slots);
		while (out.size() < minSlots) {
			out.add(null);
		}
		return out;
	}

	static List<InventoryDecoder.Stack> emptySlots(int count) {
		List<InventoryDecoder.Stack> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			out.add(null);
		}
		return out;
	}

	static List<InventoryDecoder.Stack> compactNonNull(List<InventoryDecoder.Stack> slots) {
		if (slots == null || slots.isEmpty()) {
			return List.of();
		}
		List<InventoryDecoder.Stack> out = new ArrayList<>(slots.size());
		for (InventoryDecoder.Stack stack : slots) {
			if (stack != null) {
				out.add(stack);
			}
		}
		return out;
	}

	static void put(Map<String, List<InventoryDecoder.Stack>> map, String key, List<InventoryDecoder.Stack> stacks) {
		map.put(key, stacks);
	}
}
