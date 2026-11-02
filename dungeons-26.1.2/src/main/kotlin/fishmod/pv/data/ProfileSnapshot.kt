/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.slayer.SlayerMayorMods
import java.util.UUID

class ProfileSnapshot(
    @get:JvmName("playerName") val playerName: String,
    @get:JvmName("playerUuid") val playerUuid: UUID?,
    @get:JvmName("profileName") val profileName: String,
    @get:JvmName("skyBlockLevel") val skyBlockLevel: Int,
    skyBlockXpIntoLevel: Int,
    @get:JvmName("weightText") val weightText: String,
    @get:JvmName("networthText") val networthText: String,
    purseCoins: Double,
    bankCoins: Double,
    bankTransactions: List<BankTransaction>?,
    skills: List<SkillEntry>,
    slayers: List<SlayerEntry>,
    social: SkillEntry?,
    runecrafting: SkillEntry?,
    activeSlayer: ActiveSlayerQuest?,
    emblems: EmblemInfo?,
    slayerMods: SlayerMayorMods?,
) {
    class SkillEntry(
        @get:JvmName("id") val id: String,
        @get:JvmName("name") val name: String,
        @get:JvmName("level") val level: Int,
        @get:JvmName("progress") val progress: Float,
        @get:JvmName("maxed") val maxed: Boolean,
        @get:JvmName("xpHover") val xpHover: String?,
        hoverLines: List<PvTooltip.Line>?,
        @get:JvmName("overflowLevel") val overflowLevel: Float,
    ) {
        @get:JvmName("hoverLines")
        val hoverLines: List<PvTooltip.Line> = defaultHover(hoverLines, xpHover)

        constructor(
            id: String, name: String, level: Int, progress: Float, maxed: Boolean, xpHover: String?, hoverLines: List<PvTooltip.Line>?,
        ) : this(id, name, level, progress, maxed, xpHover, hoverLines, if (maxed) level.toFloat() else level + progress)

        constructor(id: String, name: String, level: Int, progress: Float, maxed: Boolean, xpHover: String?) :
            this(id, name, level, progress, maxed, xpHover, emptyList())
    }

    class SlayerEntry(
        @get:JvmName("id") val id: String,
        @get:JvmName("name") val name: String,
        @get:JvmName("tier") val tier: Int,
        @get:JvmName("progress") val progress: Float,
        @get:JvmName("maxed") val maxed: Boolean,
        xp: Float,
        @get:JvmName("xpHover") val xpHover: String?,
        tierKills: List<Int>?,
        hoverLines: List<PvTooltip.Line>?,
    ) {
        @get:JvmName("xp") val xp: Float = maxOf(0f, xp)
        @get:JvmName("tierKills") val tierKills: List<Int> = tierKills?.toList() ?: emptyList()
        @get:JvmName("hoverLines") val hoverLines: List<PvTooltip.Line> = defaultHover(hoverLines, xpHover)

        constructor(id: String, name: String, tier: Int, progress: Float, maxed: Boolean, xpHover: String?) :
            this(id, name, tier, progress, maxed, 0f, xpHover, emptyList(), emptyList())

        constructor(id: String, name: String, tier: Int, progress: Float, maxed: Boolean, xpHover: String?, tierKills: List<Int>?) :
            this(id, name, tier, progress, maxed, 0f, xpHover, tierKills, emptyList())
    }

    /** Coop bank ledger row from `profiles[].banking.transactions[]`. */
    class BankTransaction(action: String?, amount: Double, initiatorName: String?, timestampMs: Long) {
        @get:JvmName("action") val action: String = action ?: ""
        @get:JvmName("amount") val amount: Double = maxOf(0.0, amount)
        @get:JvmName("initiatorName") val initiatorName: String = initiatorName ?: ""
        @get:JvmName("timestampMs") val timestampMs: Long = maxOf(0L, timestampMs)
    }

    /** Active `slayer.slayer_quest` row. `tier` is player-facing (T1+). */
    class ActiveSlayerQuest(
        typeId: String?,
        name: String?,
        tier: Int,
        @get:JvmName("spawned") val spawned: Boolean,
        @get:JvmName("solo") val solo: Boolean,
        combatXp: Float,
        island: String?,
    ) {
        @get:JvmName("typeId") val typeId: String = typeId ?: ""
        @get:JvmName("name") val name: String = name ?: ""
        @get:JvmName("tier") val tier: Int = maxOf(0, tier)
        @get:JvmName("combatXp") val combatXp: Float = maxOf(0f, combatXp)
        @get:JvmName("island") val island: String = island ?: ""

        fun present(): Boolean = name.isNotBlank() && tier > 0
    }

    /** `leveling.emblem_unlocks` + optional `leveling.selected_symbol`. */
    class EmblemInfo(selected: String?, unlocked: List<String>?) {
        @get:JvmName("selected") val selected: String = selected ?: ""
        @get:JvmName("unlocked") val unlocked: List<String> = unlocked?.toList() ?: emptyList()

        fun present(): Boolean = unlocked.isNotEmpty() || selected.isNotBlank()

        companion object {
            @JvmStatic
            fun empty(): EmblemInfo = EmblemInfo("", emptyList())
        }
    }

    @get:JvmName("skyBlockXpIntoLevel") val skyBlockXpIntoLevel: Int = skyBlockXpIntoLevel.coerceIn(0, 100)
    @get:JvmName("purseCoins") val purseCoins: Double = maxOf(0.0, purseCoins)
    @get:JvmName("bankCoins") val bankCoins: Double = maxOf(0.0, bankCoins)
    @get:JvmName("bankTransactions") val bankTransactions: List<BankTransaction> = bankTransactions?.toList() ?: emptyList()
    @get:JvmName("skills") val skills: List<SkillEntry> = skills.toList()
    @get:JvmName("slayers") val slayers: List<SlayerEntry> = slayers.toList()
    @get:JvmName("social") val social: SkillEntry = social ?: entry("social", "Social")
    @get:JvmName("runecrafting") val runecrafting: SkillEntry = runecrafting ?: entry("runecrafting", "Runecrafting")
    @get:JvmName("activeSlayer") val activeSlayer: ActiveSlayerQuest? = activeSlayer?.takeIf { it.present() }
    @get:JvmName("emblems") val emblems: EmblemInfo = emblems ?: EmblemInfo.empty()
    @get:JvmName("slayerMods") val slayerMods: SlayerMayorMods = slayerMods ?: SlayerMayorMods.none()

    constructor(
        playerName: String, playerUuid: UUID?, profileName: String, skyBlockLevel: Int, skyBlockXpIntoLevel: Int,
        weightText: String, networthText: String, skills: List<SkillEntry>, slayers: List<SlayerEntry>, social: SkillEntry?,
    ) : this(
        playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
        weightText, networthText, 0.0, 0.0, emptyList(), skills, slayers, social,
        null, null, EmblemInfo.empty(),
    )

    constructor(
        playerName: String, playerUuid: UUID?, profileName: String, skyBlockLevel: Int, skyBlockXpIntoLevel: Int,
        weightText: String, networthText: String, purseCoins: Double, bankCoins: Double,
        skills: List<SkillEntry>, slayers: List<SlayerEntry>, social: SkillEntry?,
    ) : this(
        playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
        weightText, networthText, purseCoins, bankCoins, emptyList(), skills, slayers, social,
        null, null, EmblemInfo.empty(),
    )

    constructor(
        playerName: String, playerUuid: UUID?, profileName: String, skyBlockLevel: Int, skyBlockXpIntoLevel: Int,
        weightText: String, networthText: String, purseCoins: Double, bankCoins: Double,
        bankTransactions: List<BankTransaction>?, skills: List<SkillEntry>, slayers: List<SlayerEntry>, social: SkillEntry?,
    ) : this(
        playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
        weightText, networthText, purseCoins, bankCoins, bankTransactions, skills, slayers, social,
        null, null, EmblemInfo.empty(),
    )

    constructor(
        playerName: String, playerUuid: UUID?, profileName: String, skyBlockLevel: Int, skyBlockXpIntoLevel: Int,
        weightText: String, networthText: String, purseCoins: Double, bankCoins: Double,
        bankTransactions: List<BankTransaction>?, skills: List<SkillEntry>, slayers: List<SlayerEntry>, social: SkillEntry?,
        runecrafting: SkillEntry?, activeSlayer: ActiveSlayerQuest?, emblems: EmblemInfo?,
    ) : this(
        playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
        weightText, networthText, purseCoins, bankCoins, bankTransactions,
        skills, slayers, social, runecrafting, activeSlayer, emblems, SlayerMayorMods.none(),
    )

    fun skyBlockProgress(): Float = skyBlockXpIntoLevel / 100.0f

    /** False while [skills] still holds the loading placeholders. */
    fun skillsLoaded(): Boolean = skills.any { it.xpHover != LOADING_HOVER }

    /**
     * Average over every entry in [skills]. Runecrafting and Social are cosmetic and live outside
     * that list, so they never count; unlevelled skills count as 0 like the in-game Skill Avg.
     */
    fun skillAverage(withProgress: Boolean): Double {
        if (skills.isEmpty()) return 0.0
        var sum = 0.0
        for (skill in skills) {
            sum += skill.level
            if (withProgress && !skill.maxed) sum += skill.progress.coerceIn(0f, 1f)
        }
        return sum / skills.size
    }

    /** Average of uncapped levels with progress, using SkyHanni's overflow curve past the skill cap. */
    fun overflowSkillAverage(): Double {
        if (skills.isEmpty()) return 0.0
        return skills.sumOf { maxOf(it.level.toFloat(), it.overflowLevel).toDouble() } / skills.size
    }

    fun withWeightText(weightText: String): ProfileSnapshot = ProfileSnapshot(
        playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
        weightText, networthText, purseCoins, bankCoins, bankTransactions,
        skills, slayers, social, runecrafting, activeSlayer, emblems, slayerMods,
    )

    fun withNetworthText(networthText: String): ProfileSnapshot = ProfileSnapshot(
        playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
        weightText, networthText, purseCoins, bankCoins, bankTransactions,
        skills, slayers, social, runecrafting, activeSlayer, emblems, slayerMods,
    )

    companion object {
        private const val LOADING_HOVER = "Loading..."

        private fun defaultHover(hoverLines: List<PvTooltip.Line>?, xpHover: String?): List<PvTooltip.Line> =
            if (hoverLines.isNullOrEmpty()) listOf(PvTooltip.Line.of(xpHover ?: "", PvDraw.COLOR_TEXT)) else hoverLines.toList()

        @JvmStatic
        fun loading(name: String?): ProfileSnapshot {
            val player = if (name.isNullOrBlank()) "Player" else name.trim()
            val skills = listOf(
                entry("combat", "Combat"), entry("foraging", "Foraging"),
                entry("farming", "Farming"), entry("enchanting", "Enchanting"),
                entry("mining", "Mining"), entry("alchemy", "Alchemy"),
                entry("fishing", "Fishing"), entry("carpentry", "Carpentry"),
                entry("taming", "Taming"), entry("hunting", "Hunting"),
            )
            val slayers = listOf(
                slayer("zombie", "Revenant"), slayer("enderman", "Enderman"),
                slayer("spider", "Tarantula"), slayer("blaze", "Blaze"),
                slayer("wolf", "Sven"), slayer("vampire", "Vampire"),
            )
            return ProfileSnapshot(
                player, null, "...", 0, 0, "...", "-", 0.0, 0.0, emptyList(), skills, slayers, entry("social", "Social"),
                entry("runecrafting", "Runecrafting"), null, EmblemInfo.empty(),
            )
        }

        private fun entry(id: String, name: String): SkillEntry = SkillEntry(id, name, 0, 0f, false, LOADING_HOVER)

        private fun slayer(id: String, name: String): SlayerEntry = SlayerEntry(id, name, 0, 0f, false, "Loading...", emptyList())
    }
}
