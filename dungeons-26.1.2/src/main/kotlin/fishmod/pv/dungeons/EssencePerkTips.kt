/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.SkyBlockStats
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Hover tips for Malik / island essence-shop perks. Effect text matches Hypixel shop lore;
 * numbers are the bonus at the player's current tier (0 = not unlocked).
 */
object EssencePerkTips {
	private const val COLOR_UNLOCKED = 0xFF6DFF8A.toInt()
	private const val COLOR_LOCKED = 0xFFFF5555.toInt()
	private const val COLOR_COLD = 0xFF55FFFF.toInt()
	private const val COLOR_WISDOM = 0xFF55FFFF.toInt()

	@JvmStatic
	fun tip(perk: DungeonSnapshot.EssencePerk?): List<PvTooltip.Line> {
		if (perk == null) return emptyList()
		val id = perk.id()?.lowercase(Locale.ROOT) ?: ""
		val levelColor = when {
			perk.maxed() -> COLOR_UNLOCKED
			perk.level() > 0 -> PvDraw.COLOR_ACCENT
			else -> COLOR_LOCKED
		}
		val lines = ArrayList<PvTooltip.Line>()
		lines.add(PvTooltip.Line.title(displayName(id, perk.name()), PvDraw.COLOR_TEXT))
		lines.add(PvTooltip.Line.row("Level", PvDraw.COLOR_MUTED, perk.level().toString() + "/" + perk.maxLevel(), levelColor))
		lines.add(PvTooltip.Line.divider())
		lines.addAll(effectLines(id, perk.level()))
		lines.addAll(costLines(EssenceShopCosts.costs(id), perk.level()))
		return lines
	}

	private fun costLines(costs: IntArray?, level: Int): List<PvTooltip.Line> {
		if (costs == null || level >= costs.size) return emptyList()
		return listOf(
			PvTooltip.Line.blank(),
			PvTooltip.Line.row("Next tier", PvDraw.COLOR_MUTED,
				FormatUtil.commas(EssenceShopCosts.nextTier(costs, level)) + " essence", PvDraw.COLOR_ACCENT),
			PvTooltip.Line.row("To max", PvDraw.COLOR_MUTED,
				FormatUtil.commas(EssenceShopCosts.toMax(costs, level)) + " essence", PvDraw.COLOR_TEXT)
		)
	}

	private fun displayName(id: String, fallback: String?): String = when (id) {
		"permanent_health" -> "Forbidden Health"
		"permanent_defense" -> "Forbidden Defense"
		"permanent_speed" -> "Forbidden Speed"
		"permanent_intelligence" -> "Forbidden Intelligence"
		"permanent_strength" -> "Forbidden Strength"
		"forbidden_blessing" -> "Forbidden Blessing"
		"dungeonbreaker_secrets" -> "Echoes of the Lost"
		"catacombs_boss_luck" -> "Boss Luck"
		"catacombs_looting" -> "Looting"
		"revive_stone", "help_of_the_fairies" -> "Help of the Fairies"
		"catacombs_health" -> "Health Essence"
		"catacombs_defense" -> "Defense Essence"
		"catacombs_strength" -> "Strength Essence"
		"catacombs_intelligence" -> "Intelligence Essence"
		"catacombs_crit_damage" -> "Critical Essence"
		"cold_efficiency" -> "Cold Efficiency"
		"cooled_forges" -> "Cooled Forges"
		"frozen_skin" -> "Frozen Skin"
		"season_of_joy" -> "Season of Joy"
		"drake_piper" -> "Drake Piper"
		"empowered_agility" -> "Empowered Agility"
		"vermin_control" -> "Vermin Control"
		"bane" -> "Bane"
		"spider_training" -> "Spider Training"
		"toxophilite" -> "Toxophilite"
		"flat_damage_vs_ender" -> "One Punch"
		"mana_after_ender_kill" -> "Recharge"
		"fero_vs_dragons" -> "Rageborn"
		"inc_zealots_odds" -> "Zealuck"
		"combat_wisdom_in_end" -> "Ender Training"
		"edrag_cd" -> "Infused Dragon"
		"dragon_reforges_buff" -> "Two-Headed Strike"
		"increased_sup_chances" -> "Dragon Piper"
		"unbridled_rage" -> "Unbridled Rage"
		else -> if (fallback.isNullOrBlank()) id else fallback
	}

	private fun effectLines(id: String, level: Int): List<PvTooltip.Line> = when (id) {
		"permanent_health" -> statBoost("Increases your ", "health", " by ", at(level, 2, 4, 6, 8, 10), ".", level)
		"permanent_defense" -> statBoost("Increases your ", "defense", " by ", at(level, 1, 2, 3, 4, 5), ".", level)
		"permanent_speed" -> statBoost("Increases your ", "speed", " by ", at(level, 1, 2), ".", level)
		"permanent_intelligence" -> statBoost("Increases your ", "intelligence", " by ", at(level, 2, 4, 6, 8, 10), ".", level)
		"permanent_strength" -> statBoost("Increases your ", "strength", " by ", at(level, 1, 2, 3, 4, 5), ".", level)
		"forbidden_blessing" -> plainEffect(level,
			text("Blessings are "),
			value(pct(at(level, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))),
			text(" more effective on you.")
		)
		"dungeonbreaker_secrets" -> {
			val charges = at(level, 1, 2, 3, 4, 5)
			plainEffect(level,
				text("Restore "),
				value(charges.toString() + if (charges == 1) " charge" else " charges"),
				text(" on your "),
				PvTooltip.Span.of("Dungeonbreaker", 0xFFFF5555.toInt()),
				text(" after collecting a secret.")
			)
		}
		"catacombs_boss_luck" -> plainEffect(level,
			text("Increases the quality of boss rewards in "),
			PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
			text(" by "),
			value(pct(at(level, 1, 3, 5, 10))),
			text(".")
		)
		"catacombs_looting" -> plainEffect(level,
			text("Increases the quality of mob loot drops in "),
			PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
			text(" by "),
			value(pct(at(level, 2, 4, 6, 8, 10))),
			text(".")
		)
		"revive_stone", "help_of_the_fairies" -> listOf(
			PvTooltip.Line.text(listOf(
				text("Start every run in "),
				PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
				text(" with an invisible "),
				PvTooltip.Span.bold("Revive Stone", 0xFFFF55FF.toInt()),
				text(".")
			))
		)
		"catacombs_health" -> catacombsStat("health", at(level, 25, 50, 75, 100, 125), level)
		"catacombs_defense" -> catacombsStat("defense", at(level, 10, 20, 30, 40, 50), level)
		"catacombs_strength" -> catacombsStat("strength", at(level, 10, 20, 30, 40, 50), level)
		"catacombs_intelligence" -> catacombsStat("intelligence", at(level, 15, 30, 45, 60, 75), level)
		"catacombs_crit_damage" -> catacombsStat("critical_damage", at(level, 10, 20, 30, 40, 50), level)
		"cold_efficiency" -> classXpEffect("Mage", 0xFF55FFFF.toInt(), level)
		"cooled_forges" -> plainEffect(level,
			value(pct(at(level, 4, 8, 12, 16, 20))),
			text(" chance to get double "),
			PvTooltip.Span.of("Essence", PvDraw.COLOR_ACCENT),
			text(" when salvaging.")
		)
		"frozen_skin" -> plainEffect(level,
			text("Grants "),
			value(at(level, 2, 4, 6, 8, 10).toString()),
			text(" "),
			PvTooltip.Span.bold("❄", COLOR_COLD),
			text(" "),
			PvTooltip.Span.of("Cold Resistance", COLOR_COLD),
			text(".")
		)
		"season_of_joy" -> plainEffect(level,
			text("Gain "),
			value(pct(at(level, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))),
			text(" extra Gifts from the Gift Attack event.")
		)
		"drake_piper" -> plainEffect(level,
			text("Increases the chance to spawn a "),
			PvTooltip.Span.bold("Reindrake", 0xFFFF5555.toInt()),
			text(" by "),
			value(pct(at(level, 10))),
			text(" while fishing on Jerry's Workshop.")
		)
		"empowered_agility" -> plainEffect(level,
			text("Reduces the "),
			manaSpan(),
			text(" cost of some movement abilities by "),
			value(pct(at(level, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))),
			text(".")
		)
		"vermin_control" -> plainEffect(level,
			text("Receive "),
			value(pct(at(level, 3, 6, 9, 12, 15))),
			text(" less damage from Spiders.")
		)
		"bane" -> plainEffect(level,
			text("Increases damage dealt to Spiders by "),
			value(pct(at(level, 3, 6, 9, 12, 15))),
			text(".")
		)
		"spider_training" -> plainEffect(level,
			text("Increases your "),
			PvTooltip.Span.bold("☯", COLOR_WISDOM),
			text(" Combat Wisdom by "),
			value(at(level, 3, 5, 10).toString()),
			text(" while on the Spider's Den.")
		)
		"toxophilite" -> plainEffect(level,
			text("Increases your "),
			PvTooltip.Span.bold("Archer", 0xFFFFAA00.toInt()),
			text(" class experience gain by "),
			value(pct(at(level, 2, 4, 6, 8, 10))),
			text(".")
		)
		"flat_damage_vs_ender" -> plainEffect(level,
			text("After all other damage, add "),
			value(at(level, 500, 1000, 1500, 2000, 2500).toString()),
			text(" damage to the first strike against Endermen and Endermites.")
		)
		"mana_after_ender_kill" -> plainEffect(level,
			text("Regain "),
			value(at(level, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20).toString()),
			text(" "),
			manaSpan(),
			text(" after killing an Enderman or Endermite.")
		)
		"fero_vs_dragons" -> plainEffect(level,
			text("Gain +"),
			value(at(level, 2, 4, 6, 8, 10).toString()),
			text(" "),
			statSpan("ferocity"),
			text(" against Dragons.")
		)
		"inc_zealots_odds" -> plainEffect(level,
			text("Increases the chance to find a special Zealot by "),
			value(pct(at(level, 2, 4, 6, 8, 10))),
			text(".")
		)
		"combat_wisdom_in_end" -> plainEffect(level,
			text("Gain +"),
			value(at(level, 3, 5, 7).toString()),
			text(" "),
			PvTooltip.Span.bold("☯", COLOR_WISDOM),
			text(" Combat Wisdom while in The End.")
		)
		"edrag_cd" -> plainEffect(level,
			text("Increases the "),
			statSpan("critical_damage"),
			text(" of your Ender Dragon pet by +"),
			value(pct(at(level, 2, 4, 6, 8, 10))),
			text(".")
		)
		"dragon_reforges_buff" -> plainEffect(level,
			text("Renowned and Spiked reforges apply an extra +"),
			value(at(level, 2, 4, 6, 8, 10).toString()),
			text(" "),
			statSpan("attack_speed"),
			text(" on your gear.")
		)
		"increased_sup_chances" -> plainEffect(level,
			text("Summoning Eyes you drop have a "),
			value("+5%"),
			text(" chance to be recombobulated.")
		)
		"unbridled_rage" -> classXpEffect("Berserk", 0xFFFF5555.toInt(), level)
		else -> listOf(PvTooltip.Line.meta("No effect description available."))
	}

	private fun classXpEffect(className: String, classColor: Int, level: Int): List<PvTooltip.Line> = plainEffect(level,
		text("Increases "),
		PvTooltip.Span.bold(className, classColor),
		text(" class experience gain by "),
		value(pct(at(level, 2, 4, 6, 8, 10))),
		text(".")
	)

	private fun catacombsStat(statId: String, amount: Int, level: Int): List<PvTooltip.Line> = plainEffect(level,
		text("Increases your base "),
		statSpan(statId),
		text(" while in "),
		PvTooltip.Span.of("The Catacombs", PvDraw.COLOR_ACCENT),
		text(" by "),
		value(amount.toString()),
		text(".")
	)

	private fun statBoost(prefix: String, statId: String, mid: String, amount: Int, suffix: String, level: Int): List<PvTooltip.Line> =
		plainEffect(level, text(prefix), statSpan(statId), text(mid), value(amount.toString()), text(suffix))

	private fun plainEffect(level: Int, vararg spans: PvTooltip.Span): List<PvTooltip.Line> {
		val out = ArrayList<PvTooltip.Line>()
		if (level <= 0) out.add(PvTooltip.Line.of("Not unlocked.", COLOR_LOCKED))
		out.add(PvTooltip.Line.text(spans.toList()))
		return out
	}

	private fun text(s: String): PvTooltip.Span = PvTooltip.Span.of(s, PvDraw.COLOR_TEXT)

	// The tier's number, bold green
	private fun value(s: String): PvTooltip.Span = PvTooltip.Span.bold(s, COLOR_UNLOCKED)

	private fun manaSpan(): PvTooltip.Span {
		val style = SkyBlockStats.stat("intelligence")
		return PvTooltip.Span.bold(style.symbol + " Mana", style.color)
	}

	private fun statSpan(statId: String): PvTooltip.Span {
		val style = SkyBlockStats.stat(statId)
		val label = when (statId.lowercase(Locale.ROOT)) {
			"health" -> "Health"
			"defense" -> "Defense"
			"speed", "walk_speed" -> "Speed"
			"strength" -> "Strength"
			"intelligence" -> "Intelligence"
			"critical_damage" -> "Crit Damage"
			"ferocity" -> "Ferocity"
			"attack_speed" -> "Attack Speed"
			else -> style.symbol
		}
		return PvTooltip.Span.bold(style.symbol + " " + label, style.color)
	}

	private fun at(level: Int, vararg tiers: Int): Int {
		if (tiers.isEmpty()) return 0
		if (level <= 0) return tiers[0]
		return tiers[max(0, min(level, tiers.size) - 1)]
	}

	private fun pct(value: Int): String = "$value%"
}
