/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Last few players opened from the search box, newest first. */
public final class SearchHistory {
	public static final int MAX = 3;
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final SearchHistory EMPTY = new SearchHistory(List.of());

	public record Entry(UUID uuid, String name) {
		boolean samePlayer(UUID otherUuid, String otherName) {
			if (this.uuid != null && otherUuid != null) {
				return this.uuid.equals(otherUuid);
			}
			return this.name.equalsIgnoreCase(otherName);
		}
	}

	private final List<Entry> entries;

	private SearchHistory(List<Entry> entries) {
		this.entries = List.copyOf(entries);
	}

	public static SearchHistory empty() {
		return EMPTY;
	}

	public List<Entry> entries() {
		return this.entries;
	}

	/** Moves the player to the top (renames replace the old name) and drops anything past {@link #MAX}. */
	public SearchHistory record(UUID uuid, String name) {
		String cleaned = name == null ? "" : name.trim();
		if (!NAME.matcher(cleaned).matches()) {
			return this;
		}
		List<Entry> next = new ArrayList<>(MAX);
		next.add(new Entry(uuid, cleaned));
		for (Entry entry : this.entries) {
			if (next.size() >= MAX) {
				break;
			}
			if (!entry.samePlayer(uuid, cleaned)) {
				next.add(entry);
			}
		}
		return new SearchHistory(next);
	}

	public List<Entry> filter(String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return this.entries;
		}
		List<Entry> matches = new ArrayList<>(this.entries.size());
		for (Entry entry : this.entries) {
			if (entry.name().toLowerCase(Locale.ROOT).contains(q)) {
				matches.add(entry);
			}
		}
		return matches;
	}

	public JsonArray toJson() {
		JsonArray array = new JsonArray();
		for (Entry entry : this.entries) {
			JsonObject object = new JsonObject();
			if (entry.uuid() != null) {
				object.addProperty("uuid", entry.uuid().toString());
			}
			object.addProperty("name", entry.name());
			array.add(object);
		}
		return array;
	}

	public static SearchHistory fromJson(JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return EMPTY;
		}
		JsonArray array = element.getAsJsonArray();
		SearchHistory history = EMPTY;
		for (int i = array.size() - 1; i >= 0; i--) {
			JsonElement item = array.get(i);
			if (!item.isJsonObject()) {
				continue;
			}
			JsonObject object = item.getAsJsonObject();
			String name = string(object, "name");
			history = history.record(parseUuid(string(object, "uuid")), name);
		}
		return history;
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw.trim());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
