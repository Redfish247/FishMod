/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import fishmod.utils.TextStrip

import fishmod.pv.data.FarmingFortuneEstimate.Garden
import fishmod.pv.data.FarmingFortuneEstimate.Kind
import fishmod.pv.data.FarmingFortuneEstimate.Line
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NbtAttrs
import java.util.EnumMap
import java.util.Locale

/** Groups the estimate into short page labels; the full sources stay available for tooltips. Never changes totals. */
object FarmingFortuneSummary {
	enum class Category(@get:JvmName("label") val label: String) {
		SKILLS("Skills"), GARDEN("Garden"), SHARDS("Shards"), ACCESSORIES("Accessories")
	}

	/** {@code lines} holds counted and unavailable sources; temporary ones are listed separately. */
	@JvmRecord
	data class Group(val category: Category, val total: Double, val lines: List<Line>)

	private val CATEGORIES: Map<String, Category> = mapOf(
		"Farming level" to Category.SKILLS,
		"Anita bonus" to Category.SKILLS,
		"Unlocked plots" to Category.GARDEN,
		"Garden bestiary" to Category.GARDEN,
		"DNA Analysis milestone" to Category.GARDEN,
		"Cropshot chip" to Category.GARDEN,
		"Fly shard" to Category.SHARDS,
		"Galaxy Fish shard" to Category.SHARDS,
		"Firefly and Lunar Moth shards" to Category.SHARDS,
		"Accessories" to Category.ACCESSORIES,
		"Helianthus Relic" to Category.ACCESSORIES
	)
	private val SHORT: Map<String, String> = mapOf(
		"Farming level" to "Farming",
		"Anita bonus" to "Anita",
		"Unlocked plots" to "Plots",
		"Garden bestiary" to "Bestiary",
		"DNA Analysis milestone" to "DNA",
		"Cropshot chip" to "Cropshot",
		"Fly shard" to "Fly",
		"Galaxy Fish shard" to "Galaxy Fish",
		"Firefly and Lunar Moth shards" to "Firefly, Lunar Moth",
		"Helianthus Relic" to "Relic"
	)
	private val SHARD_KEYS: Map<String, String> = mapOf(
		"Fly shard" to "fortunate_farmer",
		"Galaxy Fish shard" to "ultimate_dna",
		"Firefly and Lunar Moth shards" to "solar_power"
	)

	/** Permanent bonuses in fixed category order, skipping empty categories. Totals add up to {@code permanentTotal}. */
	@JvmStatic
	fun permanentGroups(est: FarmingFortuneEstimate, garden: Garden): List<Group> {
		val byCategory = EnumMap<Category, MutableList<Line>>(Category::class.java)
		for (line in est.permanentLines(garden)) {
			if (line.kind != Kind.CONDITIONAL) {
				byCategory.getOrPut(category(line.label)) { ArrayList() }.add(line)
			}
		}
		val out = ArrayList<Group>()
		for ((category, lines) in byCategory) {
			val total = lines.filter { it.kind == Kind.COUNTED }.sumOf { it.value }
			out.add(Group(category, total, java.util.List.copyOf(lines)))
		}
		return out
	}

	/** Unrecognised sources land in Garden so a new bonus is never dropped from the page. */
	@JvmStatic
	fun category(label: String?): Category = CATEGORIES[label] ?: Category.GARDEN

	@JvmStatic
	fun shortLabel(line: Line): String? {
		if (line.label == "Accessories") {
			return line.note
		}
		return SHORT[line.label] ?: line.label
	}

	/** Comma-joined short source names, for the second line of a category tile. */
	@JvmStatic
	fun detail(group: Group): String {
		val out = StringBuilder()
		for (line in group.lines) {
			if (line.kind == Kind.COUNTED && line.value <= 0.0) {
				continue
			}
			if (out.isNotEmpty()) {
				out.append(", ")
			}
			out.append(shortLabel(line))
		}
		return out.toString()
	}

	@JvmStatic
	fun shardKey(label: String?): String? = SHARD_KEYS[label]

	/** Tile detail for the tool line when there is no tool to show. */
	@JvmStatic
	fun missingToolDetail(toolLine: Line): String = when (toolLine.note) {
		"Inventory API off" -> "Inventory API off"
		"Toolkit slot unreadable", "Toolkit not in API" -> "Toolkit not readable"
		else -> "No tool"
	}

	@JvmStatic
	fun missingToolExplanation(crop: String?, toolLine: Line): String = when (toolLine.note) {
		"Inventory API off" -> "The inventory API is off, so tools can't be checked."
		"Toolkit slot unreadable" -> "The $crop toolkit slot couldn't be read."
		"Toolkit not in API" -> "The Farming Toolkit isn't in the API for this profile."
		"None in inventories, toolkit locked" -> "No $crop tool in inventories, and the toolkit is locked."
		else -> "No $crop tool in inventories or the toolkit."
	}

	/** Display name without the reforge or "Mk." so the page can show it in one short line. */
	@JvmStatic
	fun shortToolName(stack: InventoryDecoder.Stack): String {
		val raw = stack.displayName?.let { TextStrip.plain(it).trim() } ?: ""
		var name = raw.ifEmpty { stack.id }
		val reforge = NbtAttrs.string(stack.extraAttributes, "modifier")
		if (!reforge.isNullOrBlank()) {
			val prefix = FarmingToolFortune.pretty(reforge) + " "
			if (name.lowercase(Locale.ROOT).startsWith(prefix.lowercase(Locale.ROOT))) {
				name = name.substring(prefix.length)
			}
		}
		return name.replace(" Mk. ", " ").trim()
	}
}
