/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.ProfileViewer
import fishmod.pv.data.AuctionSnapshot
import fishmod.pv.data.BestiarySnapshot
import fishmod.pv.data.CollectionSnapshot
import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.EventsSnapshot
import fishmod.pv.data.FishingSnapshot
import fishmod.pv.data.ForagingSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.Leveling
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.data.MiscStatsSnapshot
import fishmod.pv.data.PetSnapshot
import fishmod.pv.data.PlayerStatsCalculator
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.data.RepoData
import fishmod.pv.data.RiftSnapshot
import fishmod.pv.data.SoftDataFailure
import fishmod.pv.gui.ArmorStacks
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NetworthBreakdown
import fishmod.pv.networth.NetworthCalculator
import fishmod.pv.weight.WeightCalculator
import java.util.Locale
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.floor

/** Home / core profile parsing helpers. Constructs [ProfileFetcher.LoadedProfile] for core loads. */
internal object ProfileHomeParser {
    private val HOME_SKILLS = arrayOf(
        "combat", "foraging", "farming", "enchanting", "mining", "alchemy", "fishing", "carpentry", "taming", "hunting"
    )
    private val HOME_SLAYERS = arrayOf(
        "zombie" to "Revenant",
        "enderman" to "Enderman",
        "spider" to "Tarantula",
        "blaze" to "Blaze",
        "wolf" to "Sven",
        "vampire" to "Vampire"
    )
    private val SECTION_CODE = Regex("§.")
    private val AMP_CODE = Regex("&[0-9a-fk-or]")

    /**
     * Minimum valid Home snapshot only. No inventory UI, tab snapshots, museum, or networth.
     * Fatal validation failures return `!ok()` so the loading egg stays up.
     */
    @JvmStatic
    @JvmOverloads
    fun parseHomeCore(name: String, uuid: UUID, root: JsonObject, preferredProfileId: String? = null): ProfileFetcher.LoadedProfile {
        return try {
            // Home gear, stats and armor each decode the same inventory fields.
            InventoryDecoder.withSharedDecode(Supplier { parseHomeCoreUnsafe(name, uuid, root, preferredProfileId) })
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Core profile parse failed for {}", name, exception)
            ProfileFetcher.failed(name, exception.message ?: "Parse failed")
        }
    }

    @JvmStatic
    fun parseHomeCoreUnsafe(name: String, uuid: UUID, root: JsonObject, preferredProfileId: String?): ProfileFetcher.LoadedProfile {
        val profiles = if (root.has("profiles") && root.get("profiles").isJsonArray) root.getAsJsonArray("profiles") else null
        if (profiles == null || profiles.isEmpty) return ProfileFetcher.failed(name, "No SkyBlock profiles")
        val best = ProfileCoopIndex.pickProfile(profiles, preferredProfileId)
        var cuteName = "Unknown"
        val undashed = HypixelApiClient.undashed(uuid)
        var profileId: String? = null
        if (best != null) {
            ProfileParseSupport.str(best.get("cute_name")).let { if (it.isNotEmpty()) cuteName = it }
            ProfileParseSupport.str(best.get("profile_id")).let { if (it.isNotEmpty()) profileId = it }
        }
        val choices = ProfileCoopIndex.listProfileChoices(profiles, profileId, undashed)
        if (best == null) return ProfileFetcher.failed(name, "No usable profile")
        val members = if (best.has("members") && best.get("members").isJsonObject) best.getAsJsonObject("members") else null
        val member = ProfileMuseumLookup.findMember(members, undashed) ?: return ProfileFetcher.failed(name, "Member data missing")

        ProfileCoopIndex.warmCoopMemberNames(choices, undashed, name, null)

        ProfileViewer.LOGGER.info("Parsing home core for {} ({})", name, cuteName)

        val weightLevels = WeightCalculator.buildLevels(member)
        val senither = WeightCalculator.senither(member, weightLevels)
        val lily = WeightCalculator.lily(member, weightLevels)

        val skills = buildHomeSkills(member)
        val social = buildSocial(member)
        val runecrafting = buildRunecrafting(member)
        val slayers = buildHomeSlayers(member)
        val activeSlayer = parseActiveSlayerQuest(member)

        var sbLevel = 0
        var sbXp = 0
        val leveling = Leveling.obj(member.get("leveling"))
        if (leveling != null) {
            Leveling.num(leveling.get("experience"))?.let { experience ->
                sbLevel = floor(experience / 100f).toInt()
                sbXp = Math.round(experience % 100f)
            }
        }
        val emblems = parseEmblems(leveling)

        val purseCoins = NetworthCalculator.purse(member)
        val bankCoins = NetworthCalculator.bank(best, member)
        val bankTransactions = parseBankTransactions(best)

        val snapshot = ProfileSnapshot(
            name,
            uuid,
            cuteName,
            sbLevel,
            sbXp,
            FormatUtil.weight(senither.total()),
            "...",
            purseCoins,
            bankCoins,
            bankTransactions,
            skills,
            slayers,
            social,
            runecrafting,
            activeSlayer,
            emblems
        )

        val homeGear = InventoryDecoder.parseHomeGear(member)
        val playerStats = PlayerStatsCalculator.fromMember(member, homeGear)
        val armor = ArmorStacks.fromMember(member)

        val misc = try {
            MiscStatsSnapshot.from(best, member)
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Misc stats parse failed for {}", name, exception)
            MiscStatsSnapshot.empty()
        }

        return ProfileFetcher.LoadedProfile(
            snapshot,
            DungeonSnapshot.empty(),
            InventorySnapshot.empty(),
            PetSnapshot.empty(),
            AuctionSnapshot.empty(),
            CollectionSnapshot.empty(),
            GardenSnapshot.empty(),
            MiningSnapshot.empty(),
            ForagingSnapshot.empty(),
            FishingSnapshot.empty(),
            CrimsonSnapshot.empty().withPlayerStats(playerStats),
            RiftSnapshot.empty(),
            BestiarySnapshot.empty(),
            EventsSnapshot.empty(),
            misc,
            null,
            profileId,
            root,
            choices,
            senither,
            lily,
            NetworthBreakdown.empty("Loading networth"),
            NetworthBreakdown.empty("Loading networth"),
            NetworthBreakdown.empty("Loading networth"),
            NetworthBreakdown.empty("Loading networth"),
            armor,
            playerStats,
            null
        )
    }

    @JvmStatic
    fun buildHomeSkills(member: JsonObject): List<ProfileSnapshot.SkillEntry> = HOME_SKILLS.map { skill ->
        val cap = Leveling.skillCap(skill, member)
        val progress = Leveling.getLevel(Leveling.skillTable(skill), Leveling.readSkillXpDouble(member, skill), cap, false)
        val title = ProfileParseSupport.title(skill)
        ProfileSnapshot.SkillEntry(
            skill,
            title,
            progress.cappedLevel(),
            progress.fill(),
            progress.maxed,
            progress.skillHover(title),
            progress.skillHoverLines(title),
            progress.overflowLevel
        )
    }.toMutableList()

    @JvmStatic
    fun buildSocial(member: JsonObject): ProfileSnapshot.SkillEntry = simpleSkill(member, "social", "Social")

    @JvmStatic
    fun buildRunecrafting(member: JsonObject): ProfileSnapshot.SkillEntry = simpleSkill(member, "runecrafting", "Runecrafting")

    private fun simpleSkill(member: JsonObject, id: String, title: String): ProfileSnapshot.SkillEntry {
        val xp = Leveling.readSkillXpDouble(member, id).toFloat()
        val cap = Leveling.skillCap(id, member)
        val progress = Leveling.getLevel(Leveling.skillTable(id), xp, cap, false)
        return ProfileSnapshot.SkillEntry(
            id,
            title,
            progress.cappedLevel(),
            progress.fill(),
            progress.maxed,
            progress.skillHover(title),
            progress.skillHoverLines(title)
        )
    }

    @JvmStatic
    fun parseActiveSlayerQuest(member: JsonObject?): ProfileSnapshot.ActiveSlayerQuest? {
        val slayer = Leveling.obj(member?.get("slayer"))
        val quest = slayer?.let { Leveling.obj(it.get("slayer_quest")) } ?: return null
        val type = ProfileParseSupport.str(quest.get("type"))
        if (type.isBlank()) return null
        val tierRaw = Leveling.num(quest.get("tier")) ?: return null
        val tier = maxOf(0, Math.round(tierRaw)) + 1
        val spawned = Leveling.num(quest.get("spawn_timestamp")) != null
        val solo = ProfileParseSupport.bool(quest, "solo")
        val combat = Leveling.num(quest.get("combat_xp"))
        val island = InventoryDecoder.prettyWords(ProfileParseSupport.str(quest.get("last_killed_mob_island")))
        return ProfileSnapshot.ActiveSlayerQuest(
            type.lowercase(Locale.ROOT),
            slayerDisplayName(type),
            tier,
            spawned,
            solo,
            if (combat == null) 0f else maxOf(0f, combat),
            island
        )
    }

    @JvmStatic
    fun slayerDisplayName(type: String?): String {
        if (type.isNullOrBlank()) return ""
        val key = type.trim().lowercase(Locale.ROOT)
        return HOME_SLAYERS.firstOrNull { it.first == key }?.second ?: ProfileParseSupport.title(key)
    }

    @JvmStatic
    fun parseEmblems(leveling: JsonObject?): ProfileSnapshot.EmblemInfo {
        if (leveling == null) return ProfileSnapshot.EmblemInfo.empty()
        val unlocked = LinkedHashSet<String>()
        addEmblemIds(unlocked, leveling.get("emblem_unlocks"))
        addEmblemIds(unlocked, leveling.get("unlocked_emblems"))
        addEmblemIds(unlocked, leveling.get("emblems"))
        if (leveling.has("completed_tasks") && leveling.get("completed_tasks").isJsonArray) {
            for (el in leveling.getAsJsonArray("completed_tasks")) {
                val id = emblemId(el)
                if (id.isBlank()) continue
                val upper = id.uppercase(Locale.ROOT)
                if (upper.contains("EMBLEM") || upper.contains("SYMBOL")) unlocked.add(id)
            }
        }
        var selected = ProfileParseSupport.str(leveling.get("selected_symbol"))
        if (selected.isBlank()) selected = ProfileParseSupport.str(leveling.get("selected_emblem"))
        if (selected.isNotBlank()) unlocked.add(selected)
        if (unlocked.isEmpty() && selected.isBlank()) return ProfileSnapshot.EmblemInfo.empty()
        return ProfileSnapshot.EmblemInfo(selected, unlocked.toList())
    }

    @JvmStatic
    fun addEmblemIds(out: MutableSet<String>, raw: JsonElement?) {
        if (raw == null || raw.isJsonNull) return
        if (raw.isJsonArray) {
            for (el in raw.asJsonArray) {
                val id = emblemId(el)
                if (id.isNotBlank()) out.add(id)
            }
            return
        }
        if (raw.isJsonObject) {
            for ((key, value) in raw.asJsonObject.entrySet()) {
                val id = emblemId(value).ifBlank { key?.trim() ?: "" }
                if (id.isNotBlank()) out.add(id)
            }
        }
    }

    @JvmStatic
    fun emblemId(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return ""
        if (el.isJsonPrimitive) return ProfileParseSupport.str(el)
        if (!el.isJsonObject) return ""
        val obj = el.asJsonObject
        return ProfileParseSupport.str(obj.get("id"))
            .ifBlank { ProfileParseSupport.str(obj.get("emblem")) }
            .ifBlank { ProfileParseSupport.str(obj.get("symbol")) }
            .ifBlank { ProfileParseSupport.str(obj.get("name")) }
    }

    @JvmStatic
    fun buildHomeSlayers(member: JsonObject): List<ProfileSnapshot.SlayerEntry> = HOME_SLAYERS.map { (id, display) ->
        val xp = Leveling.readSlayerXp(member, id)
        val slayerTable = RepoData.slayerXp(id)
        val slayerCap = if (slayerTable == null || slayerTable.isEmpty) 9 else slayerTable.size()
        val progress = Leveling.getLevel(slayerTable, xp, slayerCap, true)
        val kills = Leveling.readSlayerBossKills(member, id)
        ProfileSnapshot.SlayerEntry(
            id,
            display,
            floor(progress.level).toInt(),
            progress.fill(),
            progress.maxed,
            progress.totalXp.toFloat(),
            progress.slayerHoverWithKills(display, id, kills),
            kills.toMutableList(),
            progress.slayerHoverLinesWithKills(display, id, kills)
        )
    }.toMutableList()

    @JvmStatic
    fun parseBankTransactions(profileRoot: JsonObject?): List<ProfileSnapshot.BankTransaction> {
        if (profileRoot == null) return listOf()
        val arr = Leveling.obj(profileRoot.get("banking"))?.get("transactions") as? JsonArray
        if (arr == null || arr.isEmpty) return listOf()
        val out = ArrayList<ProfileSnapshot.BankTransaction>(arr.size())
        for (el in arr) {
            if (el == null || !el.isJsonObject) continue
            val tx = el.asJsonObject
            val action = if (tx.has("action") && tx.get("action").isJsonPrimitive) tx.get("action").asString else ""
            var amount = 0.0
            if (tx.has("amount") && tx.get("amount").isJsonPrimitive) {
                amount = ProfileParseSupport.lenient(0.0) { tx.get("amount").asDouble }
            }
            var initiator = ""
            if (tx.has("initiator_name") && tx.get("initiator_name").isJsonPrimitive) {
                initiator = tx.get("initiator_name").asString?.replace(SECTION_CODE, "")?.replace(AMP_CODE, "")?.trim() ?: ""
            }
            var ts = 0L
            if (tx.has("timestamp") && tx.get("timestamp").isJsonPrimitive) {
                ts = ProfileParseSupport.lenient(0L) { tx.get("timestamp").asLong }
            }
            if (action.isBlank() && amount <= 0.0 && ts <= 0L) continue
            out.add(ProfileSnapshot.BankTransaction(action, amount, initiator, ts))
        }
        out.sortByDescending { it.timestampMs }
        return out.take(24)
    }
}
