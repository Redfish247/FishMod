/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonObject
import fishmod.pv.ProfileViewer
import fishmod.pv.data.DungeonChestHistory
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.Leveling
import fishmod.pv.data.SoftDataFailure
import fishmod.pv.dungeons.CataXpMath
import fishmod.pv.dungeons.DungeonModifierScanner
import fishmod.pv.dungeons.EssenceShopData
import fishmod.pv.networth.InventoryDecoder

internal object ProfileDungeonParser {
    private val DUNGEON_CLASSES = arrayOf("healer", "mage", "berserk", "archer", "tank")
    private val DAMAGE_CLASSES = arrayOf("mage", "berserk", "archer", "healer", "tank")

    @JvmStatic
    fun parseDungeons(
        member: JsonObject,
        museumMember: JsonObject?,
        electionRoot: JsonObject?,
        inventoryCategories: Map<String, List<InventoryDecoder.Stack>>?
    ): DungeonSnapshot {
        val cataXp = Leveling.readCatacombsXp(member)
        val cata = CataXpMath.progress(cataXp)

        val dungeons = Leveling.obj(member.get("dungeons"))
        var selected = ""
        var secrets = 0L
        if (dungeons != null) {
            if (dungeons.has("selected_dungeon_class") && dungeons.get("selected_dungeon_class").isJsonPrimitive) {
                selected = dungeons.get("selected_dungeon_class").asString
            }
            Leveling.num(dungeons.get("secrets"))?.let { secrets = Math.round(it).toLong() }
        }

        val classes = ArrayList<DungeonSnapshot.ClassEntry>()
        var classSum = 0f
        var softMaxedCount = 0
        for (classId in DUNGEON_CLASSES) {
            val classXp = Leveling.readClassXp(member, classId)
            val progress = CataXpMath.progress(classXp)
            val level = progress.displayLevel()
            if (level >= CataXpMath.SOFT_CAP) softMaxedCount++
            classSum += progress.level
            classes.add(
                DungeonSnapshot.ClassEntry(
                    classId,
                    ProfileParseSupport.title(classId),
                    level,
                    classXp,
                    progress.fill(),
                    progress.maxed,
                    classId.equals(selected, ignoreCase = true),
                    progress.skillHover(ProfileParseSupport.title(classId))
                )
            )
        }

        val classAverage = classSum / DUNGEON_CLASSES.size
        val classAvgMaxed = softMaxedCount == DUNGEON_CLASSES.size
        val classAvgProgress = if (classAvgMaxed) 1f else (classAverage / CataXpMath.SOFT_CAP).coerceIn(0f, 1f)
        val classAvgHover = if (classAvgMaxed) {
            "Class Average " + FormatUtil.oneDecimal(classAverage.toDouble()) + " - MAX"
        } else {
            "Class Average " + FormatUtil.oneDecimal(classAverage.toDouble()) + " / " + CataXpMath.SOFT_CAP
        }

        val normal = parseMode(member, "catacombs", false)
        val master = parseMode(member, "master_catacombs", true)
        val allRuns = maxOf(1L, normal.totalRuns + master.totalRuns)
        val secretsPerRun = secrets / allRuns.toDouble()

        var mods = DungeonModifierScanner.Mods.none()
        try {
            val categories = inventoryCategories ?: InventoryDecoder.parseCategories(member, museumMember)
            mods = DungeonModifierScanner.scan(categories)
        } catch (ignored: RuntimeException) {
            if (!SoftDataFailure.isSoft(ignored)) throw ignored
            ProfileViewer.LOGGER.debug("Dungeon modifier scan failed", ignored)
        }

        val mayorFactor = CataXpMath.mayorXpFactor(electionRoot)
        val mayorName = CataXpMath.mayorName(electionRoot)

        val essenceBonuses = DungeonModifierScanner.readEssenceClassBonuses(member)
        val graduateBonus = DungeonModifierScanner.readCatacombsGraduateBonus(member)

        var dailyCount = 0
        Leveling.obj(dungeons?.get("daily_runs"))?.let { dailyRuns ->
            Leveling.num(dailyRuns.get("completed_runs_count"))?.let { dailyCount = maxOf(0, Math.round(it)) }
        }
        val journal = Leveling.obj(dungeons?.get("dungeon_journal"))
        var journals = 0
        if (journal != null && journal.has("unlocked_journals") && journal.get("unlocked_journals").isJsonArray) {
            journals = journal.getAsJsonArray("unlocked_journals").size()
        }
        val race = DungeonSnapshot.parseHubRace(dungeons?.get("dungeon_hub_race_settings"))

        return DungeonSnapshot(
            cata.displayLevel(),
            cataXp,
            cata.fill(),
            cata.maxed,
            cata.skillHover("Catacombs"),
            secrets,
            secretsPerRun,
            classAverage,
            classAvgProgress,
            classAvgMaxed,
            classAvgHover,
            classes,
            normal,
            master,
            mods.expertRing,
            mods.hecatombLevel,
            mods.scarfBonus,
            graduateBonus,
            essenceBonuses,
            mayorFactor,
            mayorName,
            EssenceShopData.wither(member),
            EssenceShopData.undead(member),
            EssenceShopData.ice(member),
            EssenceShopData.spider(member),
            EssenceShopData.dragon(member),
            dailyCount,
            journals,
            race,
            DungeonChestHistory.from(member)
        )
    }

    private fun parseMode(member: JsonObject, typeKey: String, master: Boolean): DungeonSnapshot.ModeStats {
        val dungeons = Leveling.obj(member.get("dungeons"))
        val types = dungeons?.let { Leveling.obj(it.get("dungeon_types")) }
        val type = types?.let { Leveling.obj(it.get(typeKey)) }
        fun sub(key: String): JsonObject? = type?.let { Leveling.obj(it.get(key)) }
        val completions = sub("tier_completions")
        val milestones = sub("milestone_completions")
        val mobsKilled = sub("mobs_killed")
        val bestScore = sub("best_score")
        val mostMobs = sub("most_mobs_killed")
        val fastest = sub("fastest_time")
        val fastestS = sub("fastest_time_s")
        val sPlus = sub("fastest_time_s_plus")
        val mostHealing = sub("most_healing")

        val floors = ArrayList<DungeonSnapshot.FloorEntry>()
        var total = 0L
        for (floor in (if (master) 1 else 0)..7) {
            val key = floor.toString()
            val runs = ProfileParseSupport.readLong(completions, key)
            total += runs
            val label = if (master) "M$floor" else if (floor == 0) "E" else "F$floor"
            val damage = bestDamage(type, key)
            floors.add(
                DungeonSnapshot.FloorEntry(
                    key,
                    label,
                    runs,
                    ProfileParseSupport.readLong(milestones, key),
                    ProfileParseSupport.readLong(mobsKilled, key),
                    ProfileParseSupport.readLong(bestScore, key),
                    ProfileParseSupport.readLong(mostMobs, key),
                    ProfileParseSupport.readLong(fastest, key),
                    ProfileParseSupport.readLong(fastestS, key),
                    ProfileParseSupport.readLong(sPlus, key),
                    ProfileParseSupport.readDouble(mostHealing, key),
                    damage.classId,
                    damage.value
                )
            )
        }
        return DungeonSnapshot.ModeStats(total, floors)
    }

    private class DamagePeak(val classId: String?, val value: Double) {
        companion object {
            val NONE = DamagePeak(null, 0.0)
        }
    }

    private fun bestDamage(type: JsonObject?, floorKey: String): DamagePeak {
        if (type == null) return DamagePeak.NONE
        var bestClass: String? = null
        var best = 0.0
        for (classId in DAMAGE_CLASSES) {
            val value = ProfileParseSupport.readDouble(Leveling.obj(type.get("most_damage_$classId")), floorKey)
            if (value > best) {
                best = value
                bestClass = classId
            }
        }
        return if (bestClass == null) DamagePeak.NONE else DamagePeak(bestClass, best)
    }
}
