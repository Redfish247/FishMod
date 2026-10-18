/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.FarmingFortuneEstimate.Garden;
import fishmod.pv.data.FarmingFortuneEstimate.Kind;
import fishmod.pv.data.FarmingFortuneEstimate.Line;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NbtAttrs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Temporary and situational Farming Fortune. None of it is part of a total: a source is only {@link Status#ACTIVE}
 * when saved profile data proves it, and sources the API can't show stay {@link Status#UNKNOWN}.
 * Values follow the Hypixel wiki (Farming Fortune, Garden Chips and each item's page) as of SkyBlock 0.27.
 */
public final class FarmingFortuneTemporary {
	public enum Status { ACTIVE, UNKNOWN, INACTIVE }

	/**
	 * {@code crop} marks crop fortune for the selected crop only. {@code state} is the short status detail,
	 * {@code condition} when the bonus applies, {@code iconId} the SkyBlock id behind it and {@code parts} the
	 * value breakdown.
	 */
	public record Source(
		String label, double value, Status status, boolean crop, String state, String condition, String iconId,
		List<Line> parts
	) {
		public Source {
			parts = parts == null ? List.of() : List.copyOf(parts);
		}
	}

	/** Buff state saved in {@code player_data}; {@code known} is false when the profile has none. */
	public record Buffs(boolean known, long harvestHarbingerTicks, long cakeExpiresAt, int hypercharge, int overdrive) {
		public static final Buffs NONE = new Buffs(false, 0L, 0L, 0, 0);
	}

	static final double HARVEST_HARBINGER = 50D;
	static final double CENTURY_CAKE = 5D;
	static final double PER_PEST = 5D;
	static final int MAX_PESTS = 40;
	static final double CROP_FEVER = 100D;
	static final double TRUFFLE = 30D;
	static final double MAGIC_8_BALL = 25D;
	static final double ATMOSPHERIC_FILTER = 25D;
	static final double EARTHWORM_PER_LEVEL = 3D;
	static final double CRICKET_PER_LEVEL = 5D;
	static final List<String> ANITA_FAMILY = List.of("ANITA_ARTIFACT", "ANITA_RING", "ANITA_TALISMAN");
	private static final Map<String, Double> ANITA_FORTUNE = Map.of(
		"ANITA_ARTIFACT", 25D, "ANITA_RING", 15D, "ANITA_TALISMAN", 5D
	);
	private static final Map<String, String> ANITA_NAMES = Map.of(
		"ANITA_ARTIFACT", "Anita's Artifact", "ANITA_RING", "Anita's Ring", "ANITA_TALISMAN", "Anita's Talisman"
	);
	private static final int PHILLIP_GARDEN_LEVEL = 5;
	private static final long TICK_MS = 50L;
	// SkyBlock year 1 began 2019-06-11 17:55 UTC; a year is 124 real hours and starts with three Spring months.
	private static final long SKYBLOCK_EPOCH_MS = 1_560_275_700_000L;
	private static final long YEAR_MS = 124L * 60L * 60L * 1000L;
	private static final long SPRING_MS = YEAR_MS / 4L;

	private FarmingFortuneTemporary() {
	}

	static Buffs buffs(JsonObject playerData) {
		if (playerData == null) {
			return Buffs.NONE;
		}
		long potion = 0L;
		JsonElement effects = playerData.get("active_effects");
		if (effects != null && effects.isJsonArray()) {
			for (JsonElement el : effects.getAsJsonArray()) {
				JsonObject effect = Leveling.obj(el);
				JsonElement id = effect == null ? null : effect.get("effect");
				if (id == null || !id.isJsonPrimitive()
					|| !id.getAsString().toLowerCase(Locale.ROOT).contains("harvest_harbinger")) {
					continue;
				}
				JsonElement infinite = effect.get("infinite");
				long ticks = infinite != null && infinite.isJsonPrimitive() && infinite.getAsBoolean()
					? Long.MAX_VALUE
					: Leveling.longAmount(effect.get("ticks_remaining"));
				potion = Math.max(potion, ticks);
			}
		}
		long cake = 0L;
		JsonElement temp = playerData.get("temp_stat_buffs");
		if (temp != null && temp.isJsonArray()) {
			for (JsonElement el : temp.getAsJsonArray()) {
				JsonObject buff = Leveling.obj(el);
				JsonElement key = buff == null ? null : buff.get("key");
				if (key == null || !key.isJsonPrimitive()
					|| !key.getAsString().toLowerCase(Locale.ROOT).contains("farming_fortune")) {
					continue;
				}
				long expires = Leveling.longAmount(buff.get("expire_at"));
				// Documented as epoch millis; accept seconds too rather than read them as 1970.
				cake = Math.max(cake, expires < 100_000_000_000L ? expires * 1000L : expires);
			}
		}
		JsonObject chips = Leveling.obj(playerData.get("garden_chips"));
		return new Buffs(true, potion, cake, FarmingFortuneEstimate.chipLevel(chips, "hypercharge"),
			FarmingFortuneEstimate.chipLevel(chips, "overdrive"));
	}

	/** Extra strength for Hypercharge-affected buffs; chip rarity isn't saved, so it follows the level cap like Cropshot. */
	static double hyperchargeBonus(int level) {
		if (level <= 0) {
			return 0D;
		}
		int per = level > 15 ? 5 : level > 10 ? 4 : 3;
		return per * level / 100D;
	}

	static double overdriveFortune(int level) {
		int per = level > 15 ? 7 : level > 10 ? 6 : 5;
		return per * Math.max(0, level);
	}

	static long intoYear(long nowMs) {
		return Math.floorMod(nowMs - SKYBLOCK_EPOCH_MS, YEAR_MS);
	}

	static boolean spring(long nowMs) {
		return intoYear(nowMs) < SPRING_MS;
	}

	/** Every source for {@code crop}, active ones first. */
	static List<Source> sources(FarmingFortuneEstimate est, String crop, Garden garden, long nowMs) {
		FarmingFortuneEstimate.Inputs in = est.inputs();
		Buffs buffs = in.buffs();
		double hyper = hyperchargeBonus(buffs.hypercharge());
		List<Source> out = new ArrayList<>();

		Status potion = !buffs.known() ? Status.UNKNOWN : buffs.harvestHarbingerTicks() > 0L ? Status.ACTIVE : Status.INACTIVE;
		String potionState = switch (potion) {
			case ACTIVE -> buffs.harvestHarbingerTicks() == Long.MAX_VALUE
				? "Active"
				: FormatUtil.prettySpan(buffs.harvestHarbingerTicks() * TICK_MS) + " left";
			case INACTIVE -> "When last saved";
			case UNKNOWN -> "Not in the API";
		};
		out.add(new Source("Harvest Harbinger Potion", HARVEST_HARBINGER, potion, false, potionState,
			"Lasts 25 minutes of play time.", "POTION_HARVEST_HARBINGER;5", List.of()));

		Status cake = !buffs.known() ? Status.UNKNOWN : buffs.cakeExpiresAt() > nowMs ? Status.ACTIVE : Status.INACTIVE;
		String cakeState = switch (cake) {
			case ACTIVE -> FormatUtil.prettySpan(buffs.cakeExpiresAt() - nowMs) + " left";
			case INACTIVE -> "Expired or not eaten";
			case UNKNOWN -> "Not in the API";
		};
		out.add(hypercharged(new Source("Chocolate Century Cake", CENTURY_CAKE, cake, false, cakeState,
			"Lasts 48 hours after eating.", "EPOCH_CAKE_BROWN", List.of()), hyper, buffs.hypercharge()));

		if (!garden.loaded() || garden.level() >= PHILLIP_GARDEN_LEVEL) {
			out.add(hypercharged(new Source("Pest turn-in", PER_PEST * MAX_PESTS, Status.UNKNOWN, false, "Not in the API",
				"Lasts 30m after giving Phillip pests.", "PESTHUNTER_PHILLIP_NPC",
				List.of(new Line("Pests", PER_PEST * MAX_PESTS, Kind.COUNTED, MAX_PESTS + " x " + (int) PER_PEST))),
				hyper, buffs.hypercharge()));
		}

		if (est.atmosphericFilter()) {
			int mite = in.shards().getOrDefault("filter_upgrade", 0);
			List<Line> parts = new ArrayList<>();
			parts.add(new Line("Spring", ATMOSPHERIC_FILTER, Kind.COUNTED, ""));
			if (mite > 0) {
				parts.add(new Line("Mite shard", ATMOSPHERIC_FILTER * 0.02D * mite, Kind.COUNTED, "+" + 2 * mite + "%"));
			}
			boolean spring = spring(nowMs);
			long into = intoYear(nowMs);
			String state = spring
				? "Spring, " + FormatUtil.prettySpan(SPRING_MS - into) + " left"
				: "Spring in " + FormatUtil.prettySpan(YEAR_MS - into);
			out.add(hypercharged(new Source("Atmospheric Filter", ATMOSPHERIC_FILTER * (1D + 0.02D * mite),
				spring ? Status.ACTIVE : Status.INACTIVE, false, state, "Active during Spring on the Garden.",
				"ATMOSPHERIC_FILTER", parts), hyper, buffs.hypercharge()));
		}

		InventoryDecoder.Stack ball = est.magic8Ball();
		if (ball != null) {
			double rolled = PlayerStatsCalculator.loreStat(ball.lore(), "farming_fortune");
			if (rolled > 0D) {
				out.add(new Source("Magic 8 Ball", rolled, Status.ACTIVE, false, "Rolled this season",
					"Rolls a new bonus every season.", "MAGIC_8_BALL", List.of()));
			} else {
				Status status = rolledOtherStat(ball.lore()) ? Status.INACTIVE : Status.UNKNOWN;
				out.add(hypercharged(new Source("Magic 8 Ball", MAGIC_8_BALL, status, false,
					status == Status.INACTIVE ? "Rolled another bonus" : "Roll not in the API",
					"Rolls a new bonus every season.", "MAGIC_8_BALL", List.of()), hyper, buffs.hypercharge()));
			}
		}

		FarmingFortuneEstimate.Tool tool = crop == null ? null : est.tool(crop, garden);
		if (tool != null && NbtAttrs.intMap(tool.stack().extraAttributes(), "enchantments").getOrDefault("ultimate_crop_fever", 0) > 0) {
			out.add(hypercharged(new Source("Crop Fever", CROP_FEVER, Status.UNKNOWN, false, "Random",
				"Random 60s bonus while farming.", "ULTIMATE_CROP_FEVER;1", List.of()), hyper, buffs.hypercharge()));
		}

		int earthworm = in.shards().getOrDefault("infiltration", 0);
		if (earthworm > 0) {
			out.add(new Source("Earthworm shard", EARTHWORM_PER_LEVEL * earthworm, Status.UNKNOWN, false, "Level " + earthworm,
				"Active on plots with a pest.", "ATTRIBUTE_SHARD_INFILTRATION;1", List.of()));
		}
		int cricket = in.shards().getOrDefault("pest_fortune", 0);
		if (cricket > 0) {
			out.add(new Source("Cricket shard", CRICKET_PER_LEVEL * cricket, Status.UNKNOWN, false, "Level " + cricket,
				"Only on pests.", "ATTRIBUTE_SHARD_PEST_FORTUNE;1", List.of()));
		}
		int solar = in.shards().getOrDefault("solar_power", 0);
		int lunar = in.shards().getOrDefault("lunar_power", 0);
		if (lunar > solar) {
			out.add(new Source("Lunar Moth shard", 5D * (lunar - solar), Status.UNKNOWN, false, "Level " + lunar,
				"Extra at night, above the Firefly level.", "ATTRIBUTE_SHARD_LUNAR_POWER;1", List.of()));
		} else if (solar > lunar) {
			out.add(new Source("Firefly shard", 5D * (solar - lunar), Status.UNKNOWN, false, "Level " + solar,
				"Extra by day, above the Lunar Moth level.", "ATTRIBUTE_SHARD_SOLAR_POWER;1", List.of()));
		}

		FarmingFortuneEstimate.PetPick pet = est.bestPet(crop, garden);
		if (pet != null) {
			for (Line line : pet.lines()) {
				if (line.kind() == Kind.CONDITIONAL) {
					out.add(new Source(line.label(), line.value(), Status.UNKNOWN, false, pet.pet().displayName(),
						sentence(line.note()), pet.pet().neuId(), List.of()));
				}
			}
		}

		if (crop != null) {
			InventoryDecoder.Stack anita = est.anita();
			if (anita != null) {
				String id = anita.id().toUpperCase(Locale.ROOT);
				out.add(new Source(ANITA_NAMES.get(id), ANITA_FORTUNE.get(id), Status.UNKNOWN, true, "Random crop",
					"One random crop each Jacob's Contest.", id, List.of()));
			}
			if (buffs.overdrive() > 0) {
				out.add(new Source("Overdrive Chip", overdriveFortune(buffs.overdrive()), Status.UNKNOWN, true,
					"Level " + buffs.overdrive(), "Contest crops during Jacob's Contests.", "OVERDRIVE_GARDEN_CHIP", List.of()));
			}
			if ("Cocoa Beans".equals(crop) && est.truffle()) {
				out.add(new Source("Refined Dark Cacao Truffle", TRUFFLE, Status.UNKNOWN, true, "Not in the API",
					"Lasts 60 minutes after eating.", "REFINED_DARK_CACAO_TRUFFLE", List.of()));
			}
		}
		out.sort(Comparator.comparingInt(s -> s.status().ordinal()));
		return out;
	}

	private static Source hypercharged(Source s, double bonus, int level) {
		if (bonus <= 0D) {
			return s;
		}
		List<Line> parts = new ArrayList<>(s.parts().isEmpty() ? List.of(new Line("Base", s.value(), Kind.COUNTED, "")) : s.parts());
		parts.add(new Line("Hypercharge Chip", s.value() * bonus, Kind.COUNTED, "Level " + level + ", +" + Math.round(bonus * 100D) + "%"));
		return new Source(s.label(), s.value() * (1D + bonus), s.status(), s.crop(), s.state(), s.condition(), s.iconId(), parts);
	}

	// Each season shows the rolled bonus as stat lines; any other rolled stat means Farming Fortune isn't active.
	private static boolean rolledOtherStat(List<String> lore) {
		for (String stat : List.of("foraging_fortune", "mining_fortune", "fishing_speed", "strength")) {
			if (PlayerStatsCalculator.loreStat(lore, stat) > 0D) {
				return true;
			}
		}
		return false;
	}

	private static String sentence(String note) {
		if (note == null || note.isBlank()) {
			return "Only active some of the time.";
		}
		String s = Character.toUpperCase(note.charAt(0)) + note.substring(1);
		return s.endsWith(".") ? s : s + ".";
	}

	public static double activeTotal(List<Source> sources) {
		double total = 0D;
		for (Source s : sources) {
			if (s.status() == Status.ACTIVE) {
				total += s.value();
			}
		}
		return total;
	}

	/** Most each unconfirmed source could add; inactive ones are left out. */
	public static double possibleTotal(List<Source> sources) {
		double total = 0D;
		for (Source s : sources) {
			if (s.status() == Status.UNKNOWN) {
				total += s.value();
			}
		}
		return total;
	}
}
