/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.SkyBlockStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hover tips for Malik / island essence-shop perks. Effect text matches Hypixel shop lore;
 * numbers are the bonus at the player's current tier (0 = not unlocked).
 */
public final class EssencePerkTips {
	private static final int COLOR_UNLOCKED = 0xFF6DFF8A;
	private static final int COLOR_LOCKED = 0xFFFF5555;
	private static final int COLOR_COLD = 0xFF55FFFF;
	private static final int COLOR_WISDOM = 0xFF55FFFF;

	private EssencePerkTips() {
	}

	public static List<PvTooltip.Line> tip(DungeonSnapshot.EssencePerk perk) {
		if (perk == null) {
			return List.of();
		}
		String id = perk.id() == null ? "" : perk.id().toLowerCase(Locale.ROOT);
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title(displayName(id, perk.name()), PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.row(
			"Level",
			PvDraw.COLOR_MUTED,
			perk.level() + "/" + perk.maxLevel(),
			perk.maxed() ? COLOR_UNLOCKED : (perk.level() > 0 ? PvDraw.COLOR_ACCENT : COLOR_LOCKED)
		));
		lines.add(PvTooltip.Line.divider());
		lines.addAll(effectLines(id, perk.level()));
		lines.addAll(costLines(EssenceShopCosts.costs(id), perk.level()));
		return lines;
	}

	private static List<PvTooltip.Line> costLines(int[] costs, int level) {
		if (costs == null || level >= costs.length) {
			return List.of();
		}
		List<PvTooltip.Line> out = new ArrayList<>();
		out.add(PvTooltip.Line.blank());
		out.add(PvTooltip.Line.row("Next tier", PvDraw.COLOR_MUTED,
			FormatUtil.commas(EssenceShopCosts.nextTier(costs, level)) + " essence", PvDraw.COLOR_ACCENT));
		out.add(PvTooltip.Line.row("To max", PvDraw.COLOR_MUTED,
			FormatUtil.commas(EssenceShopCosts.toMax(costs, level)) + " essence", PvDraw.COLOR_TEXT));
		return out;
	}

	private static String displayName(String id, String fallback) {
		return switch (id) {
			case "permanent_health" -> "Forbidden Health";
			case "permanent_defense" -> "Forbidden Defense";
			case "permanent_speed" -> "Forbidden Speed";
			case "permanent_intelligence" -> "Forbidden Intelligence";
			case "permanent_strength" -> "Forbidden Strength";
			case "forbidden_blessing" -> "Forbidden Blessing";
			case "dungeonbreaker_secrets" -> "Echoes of the Lost";
			case "catacombs_boss_luck" -> "Boss Luck";
			case "catacombs_looting" -> "Looting";
			case "revive_stone", "help_of_the_fairies" -> "Help of the Fairies";
			case "catacombs_health" -> "Health Essence";
			case "catacombs_defense" -> "Defense Essence";
			case "catacombs_strength" -> "Strength Essence";
			case "catacombs_intelligence" -> "Intelligence Essence";
			case "catacombs_crit_damage" -> "Critical Essence";
			case "cold_efficiency" -> "Cold Efficiency";
			case "cooled_forges" -> "Cooled Forges";
			case "frozen_skin" -> "Frozen Skin";
			case "season_of_joy" -> "Season of Joy";
			case "drake_piper" -> "Drake Piper";
			case "empowered_agility" -> "Empowered Agility";
			case "vermin_control" -> "Vermin Control";
			case "bane" -> "Bane";
			case "spider_training" -> "Spider Training";
			case "toxophilite" -> "Toxophilite";
			case "flat_damage_vs_ender" -> "One Punch";
			case "mana_after_ender_kill" -> "Recharge";
			case "fero_vs_dragons" -> "Rageborn";
			case "inc_zealots_odds" -> "Zealuck";
			case "combat_wisdom_in_end" -> "Ender Training";
			case "edrag_cd" -> "Infused Dragon";
			case "dragon_reforges_buff" -> "Two-Headed Strike";
			case "increased_sup_chances" -> "Dragon Piper";
			case "unbridled_rage" -> "Unbridled Rage";
			default -> fallback == null || fallback.isBlank() ? id : fallback;
		};
	}

	private static List<PvTooltip.Line> effectLines(String id, int level) {
		return switch (id) {
			case "permanent_health" -> statBoost(
				"Increases your ", "health", " by ", at(level, 2, 4, 6, 8, 10), ".", level);
			case "permanent_defense" -> statBoost(
				"Increases your ", "defense", " by ", at(level, 1, 2, 3, 4, 5), ".", level);
			case "permanent_speed" -> statBoost(
				"Increases your ", "speed", " by ", at(level, 1, 2), ".", level);
			case "permanent_intelligence" -> statBoost(
				"Increases your ", "intelligence", " by ", at(level, 2, 4, 6, 8, 10), ".", level);
			case "permanent_strength" -> statBoost(
				"Increases your ", "strength", " by ", at(level, 1, 2, 3, 4, 5), ".", level);
			case "forbidden_blessing" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Blessings are ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" more effective on you.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "dungeonbreaker_secrets" -> {
				int charges = at(level, 1, 2, 3, 4, 5);
				yield plainEffect(
					List.of(
						PvTooltip.Span.of("Restore ", PvDraw.COLOR_TEXT),
						PvTooltip.Span.bold(charges + (charges == 1 ? " charge" : " charges"), COLOR_UNLOCKED),
						PvTooltip.Span.of(" on your ", PvDraw.COLOR_TEXT),
						PvTooltip.Span.of("Dungeonbreaker", 0xFFFF5555),
						PvTooltip.Span.of(" after collecting a secret.", PvDraw.COLOR_TEXT)
					),
					level
				);
			}
			case "catacombs_boss_luck" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases the quality of boss rewards in ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
					PvTooltip.Span.of(" by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 1, 3, 5, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "catacombs_looting" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases the quality of mob loot drops in ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
					PvTooltip.Span.of(" by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "revive_stone", "help_of_the_fairies" -> List.of(
				PvTooltip.Line.text(List.of(
					PvTooltip.Span.of("Start every run in ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
					PvTooltip.Span.of(" with an invisible ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("Revive Stone", 0xFFFF55FF),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				))
			);
			case "catacombs_health" -> catacombsStat("health", at(level, 25, 50, 75, 100, 125), level);
			case "catacombs_defense" -> catacombsStat("defense", at(level, 10, 20, 30, 40, 50), level);
			case "catacombs_strength" -> catacombsStat("strength", at(level, 10, 20, 30, 40, 50), level);
			case "catacombs_intelligence" -> catacombsStat("intelligence", at(level, 15, 30, 45, 60, 75), level);
			case "catacombs_crit_damage" -> catacombsStat("critical_damage", at(level, 10, 20, 30, 40, 50), level);
			case "cold_efficiency" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("Mage", 0xFF55FFFF),
					PvTooltip.Span.of(" class experience gain by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "cooled_forges" -> plainEffect(
				List.of(
					PvTooltip.Span.bold(pct(at(level, 4, 8, 12, 16, 20)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" chance to get double ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.of("Essence", PvDraw.COLOR_ACCENT),
					PvTooltip.Span.of(" when salvaging.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "frozen_skin" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Grants ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("❄", COLOR_COLD),
					PvTooltip.Span.of(" ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.of("Cold Resistance", COLOR_COLD),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "season_of_joy" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Gain ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" extra Gifts from the Gift Attack event.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "drake_piper" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases the chance to spawn a ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("Reindrake", 0xFFFF5555),
					PvTooltip.Span.of(" by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" while fishing on Jerry's Workshop.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "empowered_agility" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Reduces the ", PvDraw.COLOR_TEXT),
					manaSpan(),
					PvTooltip.Span.of(" cost of some movement abilities by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "vermin_control" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Receive ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 3, 6, 9, 12, 15)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" less damage from Spiders.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "bane" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases damage dealt to Spiders by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 3, 6, 9, 12, 15)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "spider_training" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases your ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("☯", COLOR_WISDOM),
					PvTooltip.Span.of(" Combat Wisdom by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 3, 5, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" while on the Spider's Den.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "toxophilite" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases your ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("Archer", 0xFFFFAA00),
					PvTooltip.Span.of(" class experience gain by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "flat_damage_vs_ender" -> plainEffect(
				List.of(
					PvTooltip.Span.of("After all other damage, add ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 500, 1000, 1500, 2000, 2500)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" damage to the first strike against Endermen and Endermites.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "mana_after_ender_kill" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Regain ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" ", PvDraw.COLOR_TEXT),
					manaSpan(),
					PvTooltip.Span.of(" after killing an Enderman or Endermite.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "fero_vs_dragons" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Gain +", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" ", PvDraw.COLOR_TEXT),
					statSpan("ferocity"),
					PvTooltip.Span.of(" against Dragons.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "inc_zealots_odds" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases the chance to find a special Zealot by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "combat_wisdom_in_end" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Gain +", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 3, 5, 7)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("☯", COLOR_WISDOM),
					PvTooltip.Span.of(" Combat Wisdom while in The End.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "edrag_cd" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases the ", PvDraw.COLOR_TEXT),
					statSpan("critical_damage"),
					PvTooltip.Span.of(" of your Ender Dragon pet by +", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "dragon_reforges_buff" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Renowned and Spiked reforges apply an extra +", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(String.valueOf(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(" ", PvDraw.COLOR_TEXT),
					statSpan("attack_speed"),
					PvTooltip.Span.of(" on your gear.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "increased_sup_chances" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Summoning Eyes you drop have a ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("+5%", COLOR_UNLOCKED),
					PvTooltip.Span.of(" chance to be recombobulated.", PvDraw.COLOR_TEXT)
				),
				level
			);
			case "unbridled_rage" -> plainEffect(
				List.of(
					PvTooltip.Span.of("Increases ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold("Berserk", 0xFFFF5555),
					PvTooltip.Span.of(" class experience gain by ", PvDraw.COLOR_TEXT),
					PvTooltip.Span.bold(pct(at(level, 2, 4, 6, 8, 10)), COLOR_UNLOCKED),
					PvTooltip.Span.of(".", PvDraw.COLOR_TEXT)
				),
				level
			);
			default -> List.of(PvTooltip.Line.meta("No effect description available."));
		};
	}

	private static List<PvTooltip.Line> catacombsStat(String statId, int amount, int level) {
		List<PvTooltip.Span> spans = new ArrayList<>();
		spans.add(PvTooltip.Span.of("Increases your base ", PvDraw.COLOR_TEXT));
		spans.add(statSpan(statId));
		spans.add(PvTooltip.Span.of(" while in ", PvDraw.COLOR_TEXT));
		spans.add(PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT));
		spans.add(PvTooltip.Span.of(" by ", PvDraw.COLOR_TEXT));
		spans.add(PvTooltip.Span.bold(String.valueOf(amount), COLOR_UNLOCKED));
		spans.add(PvTooltip.Span.of(".", PvDraw.COLOR_TEXT));
		return plainEffect(spans, level);
	}

	private static List<PvTooltip.Line> statBoost(
		String prefix, String statId, String mid, int amount, String suffix, int level
	) {
		List<PvTooltip.Span> spans = new ArrayList<>();
		spans.add(PvTooltip.Span.of(prefix, PvDraw.COLOR_TEXT));
		spans.add(statSpan(statId));
		spans.add(PvTooltip.Span.of(mid, PvDraw.COLOR_TEXT));
		spans.add(PvTooltip.Span.bold(String.valueOf(amount), COLOR_UNLOCKED));
		spans.add(PvTooltip.Span.of(suffix, PvDraw.COLOR_TEXT));
		return plainEffect(spans, level);
	}

	private static List<PvTooltip.Line> plainEffect(List<PvTooltip.Span> spans, int level) {
		List<PvTooltip.Line> out = new ArrayList<>();
		if (level <= 0) {
			out.add(PvTooltip.Line.of("Not unlocked.", COLOR_LOCKED));
		}
		out.add(PvTooltip.Line.text(spans));
		return out;
	}

	private static PvTooltip.Span manaSpan() {
		SkyBlockStats.StatStyle style = SkyBlockStats.stat("intelligence");
		return PvTooltip.Span.bold(style.symbol() + " Mana", style.color());
	}

	private static PvTooltip.Span statSpan(String statId) {
		SkyBlockStats.StatStyle style = SkyBlockStats.stat(statId);
		String label = switch (statId.toLowerCase(Locale.ROOT)) {
			case "health" -> "Health";
			case "defense" -> "Defense";
			case "speed", "walk_speed" -> "Speed";
			case "strength" -> "Strength";
			case "intelligence" -> "Intelligence";
			case "critical_damage" -> "Crit Damage";
			case "ferocity" -> "Ferocity";
			case "attack_speed" -> "Attack Speed";
			default -> style.symbol();
		};
		return PvTooltip.Span.bold(style.symbol() + " " + label, style.color());
	}

	private static int at(int level, int... tiers) {
		if (tiers.length == 0) {
			return 0;
		}
		if (level <= 0) {
			return tiers[0];
		}
		int idx = Math.min(level, tiers.length) - 1;
		return tiers[Math.max(0, idx)];
	}

	private static String pct(int value) {
		return value + "%";
	}
}
