/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import fishmod.pv.networth.InventoryDecoder
import java.util.Collections
import java.util.Locale

class DungeonSnapshot(
	@get:JvmName("cataLevel") val cataLevel: Int,
	@get:JvmName("cataXp") val cataXp: Float,
	@get:JvmName("cataProgress") val cataProgress: Float,
	@get:JvmName("cataMaxed") val cataMaxed: Boolean,
	@get:JvmName("cataHover") val cataHover: String?,
	@get:JvmName("secrets") val secrets: Long,
	@get:JvmName("secretsPerRun") val secretsPerRun: Double,
	@get:JvmName("classAverage") val classAverage: Float,
	@get:JvmName("classAverageProgress") val classAverageProgress: Float,
	@get:JvmName("classAverageMaxed") val classAverageMaxed: Boolean,
	@get:JvmName("classAverageHover") val classAverageHover: String?,
	classes: List<ClassEntry>?,
	normal: ModeStats?,
	master: ModeStats?,
	@get:JvmName("expertRing") val expertRing: Boolean,
	@get:JvmName("hecatombLevel") val hecatombLevel: Int,
	@get:JvmName("scarfBonus") val scarfBonus: Double,
	catacombsGraduateBonus: Double,
	classEssenceBonuses: Map<String, Double>?,
	@get:JvmName("mayorFactor") val mayorFactor: Double,
	mayorName: String?,
	witherShop: EssenceShop?,
	undeadShop: EssenceShop?,
	iceShop: EssenceShop?,
	spiderShop: EssenceShop?,
	dragonShop: EssenceShop?,
	dailyRuns: Int,
	journalsUnlocked: Int,
	hubRace: HubRace?,
	chestHistory: DungeonChestHistory?
) {
	@JvmRecord
	data class ClassEntry(
		val id: String,
		val name: String,
		val level: Int,
		val xp: Float,
		val progress: Float,
		val maxed: Boolean,
		val selected: Boolean,
		val xpHover: String?
	)

	@JvmRecord
	data class FloorEntry(
		val id: String,
		val label: String,
		val completions: Long,
		val milestoneCompletions: Long,
		val mobsKilled: Long,
		val bestScore: Long,
		val mostMobsKilled: Long,
		val fastestMs: Long,
		val fastestSMs: Long,
		val fastestSPlusMs: Long,
		val mostHealing: Double,
		val mostDamageClass: String?,
		val mostDamage: Double
	) {
		companion object {
			@JvmStatic
			fun empty(id: String, label: String): FloorEntry = FloorEntry(id, label, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0, null, 0.0)
		}
	}

	@JvmRecord
	data class ModeStats(val totalRuns: Long, val floors: List<FloorEntry>) {
		companion object {
			@JvmStatic
			fun empty(): ModeStats = ModeStats(0L, listOf())
		}
	}

	@JvmRecord
	data class EssencePerk(val id: String, val name: String, val level: Int, val maxLevel: Int) {
		fun maxed(): Boolean = level >= maxLevel && maxLevel > 0
	}

	@JvmRecord
	data class EssenceShop(val id: String, val name: String, val balance: Long, val iconId: String, val perks: List<EssencePerk>) {
		companion object {
			@JvmStatic
			fun empty(id: String, name: String): EssenceShop {
				val icon = when (id) {
					"wither" -> "ESSENCE_WITHER"
					"undead" -> "ESSENCE_UNDEAD"
					"gold" -> "ESSENCE_GOLD"
					"diamond" -> "ESSENCE_DIAMOND"
					"crimson" -> "ESSENCE_CRIMSON"
					"forest" -> "ESSENCE_FOREST"
					"spider" -> "ESSENCE_SPIDER"
					"dragon" -> "ESSENCE_DRAGON"
					"ice" -> "ESSENCE_ICE"
					"fossil" -> "ESSENCE_FOSSIL"
					"safari" -> "ESSENCE_SAFARI"
					else -> "ESSENCE_WITHER"
				}
				return EssenceShop(id, name, 0L, icon, listOf())
			}
		}
	}

	class HubRace(selectedRace: String?, selectedSetting: String?, @get:JvmName("runback") val runback: Boolean?) {
		@get:JvmName("selectedRace") val selectedRace: String = selectedRace ?: ""
		@get:JvmName("selectedSetting") val selectedSetting: String = selectedSetting ?: ""

		fun present(): Boolean = selectedRace.isNotBlank() || selectedSetting.isNotBlank() || runback != null

		companion object {
			@JvmStatic
			fun empty(): HubRace = HubRace("", "", null)
		}
	}

	@get:JvmName("classes") val classes: List<ClassEntry> = if (classes == null) listOf() else java.util.List.copyOf(classes)
	@get:JvmName("normal") val normal: ModeStats = normal ?: ModeStats.empty()
	@get:JvmName("master") val master: ModeStats = master ?: ModeStats.empty()
	@get:JvmName("catacombsGraduateBonus") val catacombsGraduateBonus: Double = maxOf(0.0, catacombsGraduateBonus)
	private val classEssenceBonuses: Map<String, Double> =
		if (classEssenceBonuses == null) mapOf() else Collections.unmodifiableMap(java.util.Map.copyOf(classEssenceBonuses))
	@get:JvmName("mayorName") val mayorName: String = mayorName ?: ""
	@get:JvmName("witherShop") val witherShop: EssenceShop = witherShop ?: EssenceShop.empty("wither", "Wither")
	@get:JvmName("undeadShop") val undeadShop: EssenceShop = undeadShop ?: EssenceShop.empty("undead", "Undead")
	@get:JvmName("iceShop") val iceShop: EssenceShop = iceShop ?: EssenceShop.empty("ice", "Ice")
	@get:JvmName("spiderShop") val spiderShop: EssenceShop = spiderShop ?: EssenceShop.empty("spider", "Spider")
	@get:JvmName("dragonShop") val dragonShop: EssenceShop = dragonShop ?: EssenceShop.empty("dragon", "Dragon")
	@get:JvmName("dailyRuns") val dailyRuns: Int = maxOf(0, dailyRuns)
	@get:JvmName("journalsUnlocked") val journalsUnlocked: Int = maxOf(0, journalsUnlocked)
	@get:JvmName("hubRace") val hubRace: HubRace = hubRace ?: HubRace.empty()
	@get:JvmName("chestHistory") val chestHistory: DungeonChestHistory = chestHistory ?: DungeonChestHistory.empty()

	fun mode(masterMode: Boolean): ModeStats = if (masterMode) master else normal

	fun classEssenceBonus(classId: String?): Double {
		if (classId == null || classEssenceBonuses.isEmpty()) {
			return 0.0
		}
		return classEssenceBonuses[classId.lowercase(Locale.ROOT)] ?: 0.0
	}

	companion object {
		@JvmStatic
		fun parseHubRace(raw: JsonElement?): HubRace {
			val root = if (raw != null && raw.isJsonObject) raw.asJsonObject else null
			if (root == null || root.entrySet().isEmpty()) {
				return HubRace.empty()
			}
			var race = ""
			if (root.has("selected_race") && root.get("selected_race").isJsonPrimitive) {
				race = try {
					root.get("selected_race").asString
				} catch (ignored: RuntimeException) {
					""
				}
			}
			var setting = ""
			if (root.has("selected_setting") && root.get("selected_setting").isJsonPrimitive) {
				setting = try {
					root.get("selected_setting").asString
				} catch (ignored: RuntimeException) {
					""
				}
			}
			var runback: Boolean? = null
			if (root.has("runback") && root.get("runback").isJsonPrimitive) {
				runback = try {
					root.get("runback").asBoolean
				} catch (ignored: RuntimeException) {
					null
				}
			}
			if (race.isBlank() && setting.isBlank() && runback == null) {
				return HubRace.empty()
			}
			return HubRace(InventoryDecoder.prettyWords(race), InventoryDecoder.prettyWords(setting), runback)
		}

		@JvmStatic
		fun empty(): DungeonSnapshot = DungeonSnapshot(
			0, 0f, 0f, false, "Loading...",
			0L, 0.0,
			0f, 0f, false, "Loading...",
			listOf(),
			ModeStats.empty(),
			ModeStats.empty(),
			false, 0, 0.0, 0.0, mapOf(), 1.0, "",
			EssenceShop.empty("wither", "Wither"),
			EssenceShop.empty("undead", "Undead"),
			EssenceShop.empty("ice", "Ice"),
			EssenceShop.empty("spider", "Spider"),
			EssenceShop.empty("dragon", "Dragon"),
			0, 0,
			HubRace.empty(),
			DungeonChestHistory.empty()
		)
	}
}
