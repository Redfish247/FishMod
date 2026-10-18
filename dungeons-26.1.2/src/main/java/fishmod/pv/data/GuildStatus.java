/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.util.LegacyChatFormatting;
import java.util.Locale;
import net.minecraft.ChatFormatting;

/** Click-to-load Hypixel guild card for Home → Misc. */
public final class GuildStatus {
	public enum State {
		IDLE,
		LOADING,
		READY,
		NONE,
		ERROR
	}

	private final State state;
	private final String name;
	private final String tag;
	private final String tagColor;
	private final String rank;
	private final long joinedMs;
	private final int members;
	private final String description;
	private final long exp;
	private final long createdMs;
	private final String error;

	private GuildStatus(
		State state,
		String name,
		String tag,
		String tagColor,
		String rank,
		long joinedMs,
		int members,
		String description,
		long exp,
		long createdMs,
		String error
	) {
		this.state = state == null ? State.IDLE : state;
		this.name = name == null ? "" : name;
		this.tag = tag == null ? "" : tag;
		this.tagColor = tagColor == null ? "" : tagColor;
		this.rank = rank == null ? "" : rank;
		this.joinedMs = Math.max(0L, joinedMs);
		this.members = Math.max(0, members);
		this.description = description == null ? "" : description;
		this.exp = Math.max(0L, exp);
		this.createdMs = Math.max(0L, createdMs);
		this.error = error == null ? "" : error;
	}

	public static GuildStatus idle() {
		return new GuildStatus(State.IDLE, "", "", "", "", 0L, 0, "", 0L, 0L, "");
	}

	public static GuildStatus loading() {
		return new GuildStatus(State.LOADING, "", "", "", "", 0L, 0, "", 0L, 0L, "");
	}

	public static GuildStatus none() {
		return new GuildStatus(State.NONE, "", "", "", "", 0L, 0, "", 0L, 0L, "");
	}

	public static GuildStatus error(String message) {
		return new GuildStatus(State.ERROR, "", "", "", "", 0L, 0, "", 0L, 0L, message);
	}

	public static GuildStatus fromHypixel(JsonObject root, String undashedUuid) {
		if (root == null) {
			return error("Guild unavailable");
		}
		JsonObject guild = Leveling.obj(root.get("guild"));
		if (guild == null) {
			return none();
		}
		String name = str(guild, "name");
		if (name.isBlank()) {
			return none();
		}
		String rank = "";
		long joined = 0L;
		JsonArray members = guild.has("members") && guild.get("members").isJsonArray()
			? guild.getAsJsonArray("members")
			: null;
		int count = members == null ? 0 : members.size();
		String want = undashedUuid == null ? "" : undashedUuid.replace("-", "").toLowerCase(Locale.ROOT);
		if (members != null && !want.isBlank()) {
			for (JsonElement el : members) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject m = el.getAsJsonObject();
				String uuid = str(m, "uuid").replace("-", "").toLowerCase(Locale.ROOT);
				if (!want.equals(uuid)) {
					continue;
				}
				rank = str(m, "rank");
				joined = longOf(m, "joined");
				break;
			}
		}
		return new GuildStatus(
			State.READY,
			name,
			str(guild, "tag"),
			str(guild, "tagColor"),
			rank,
			joined,
			count,
			str(guild, "description"),
			longOf(guild, "exp"),
			longOf(guild, "created"),
			""
		);
	}

	public String buttonLabel() {
		return switch (this.state) {
			case IDLE -> "Load Guild";
			case LOADING -> "...";
			case READY -> {
				String label = this.name;
				if (!this.tag.isBlank()) {
					label = label + " [" + this.tag + "]";
				}
				yield label;
			}
			case NONE -> "No Guild";
			case ERROR -> "Guild";
		};
	}

	public int tagRgb() {
		ChatFormatting fmt = null;
		if (!this.tagColor.isBlank()) {
			try {
				fmt = ChatFormatting.valueOf(this.tagColor.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
			}
		}
		Integer rgb = LegacyChatFormatting.rgb(fmt);
		if (rgb != null) {
			return 0xFF000000 | rgb;
		}
		return PvDrawCompat.ACCENT;
	}

	/** Local color helper so this data class need not import PvDraw. */
	private static final class PvDrawCompat {
		static final int ACCENT = 0xFF5B8CFF;
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			String s = obj.get(key).getAsString();
			return s == null ? "" : s;
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0L;
		}
		try {
			return Math.max(0L, (long) obj.get(key).getAsDouble());
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0L;
		}
	}

	public State state() { return state; }
	public String name() { return name; }
	public String tag() { return tag; }
	public String tagColor() { return tagColor; }
	public String rank() { return rank; }
	public long joinedMs() { return joinedMs; }
	public int members() { return members; }
	public String description() { return description; }
	public long exp() { return exp; }
	public long createdMs() { return createdMs; }
	public String error() { return error; }
}
