/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.networth.InventoryDecoder
import kotlin.math.floor

/** Member `rift.*` + motes + Vampire slayer for the Rift tab. */
class RiftSnapshot private constructor(
    motesPurse: Long,
    lifetimeMotes: Long,
    visits: Long,
    lastFreeAccessMs: Long,
    enigmaFound: Int,
    @get:JvmName("enigmaCloakBought") val enigmaCloakBought: Boolean,
    enigmaBonusIndex: Int,
    enigmaSouls: List<String>?,
    timecharmsSecured: Int,
    eliseStep: Int,
    timecharms: List<Timecharm>?,
    burgers: Int,
    catsFound: Int,
    @get:JvmName("montezumaUnlocked") val montezumaUnlocked: Boolean,
    montezumaTier: String?,
    foundCats: List<String>?,
    eyesKilled: Int,
    killedEyes: List<String>?,
    purchasedBoundaries: List<String>?,
    vampire: VampireProgress?,
    inventory: InventorySnapshot.Page?,
    enderPages: List<InventorySnapshot.Page>?,
    questRegions: List<QuestRegion>?,
) {
    /**
     * Canonical gallery timecharms. API `type` matches [apiType];
     * `visits` on secured trophies is the gallery visit requirement (“Visits to get”).
     */
    enum class TimecharmDef(
        private val apiType: String,
        private val itemId: String,
        private val displayName: String,
        private val color: Int,
    ) {
        WYLDLY_SUPREME("wyldly_supreme", "RIFT_TROPHY_WYLDLY_SUPREME", "Supreme Timecharm", 0xFF55FF55.toInt()),
        CHICKEN_N_EGG("chicken_n_egg", "RIFT_TROPHY_CHICKEN_N_EGG", "Chicken N Egg Timecharm", 0xFFD2A679.toInt()),
        MIRRORED("mirrored", "RIFT_TROPHY_MIRRORED", "mrahcemiT esrevrorriM", 0xFF55FFFF.toInt()),
        CITIZEN("citizen", "RIFT_TROPHY_CITIZEN", "SkyBlock Citizen Timecharm", 0xFFAA55FF.toInt()),
        LAZY_LIVING("lazy_living", "RIFT_TROPHY_LAZY_LIVING", "Living Timecharm", 0xFF5555FF.toInt()),
        SLIME("slime", "RIFT_TROPHY_SLIME", "Globulate Timecharm", 0xFF55FF55.toInt()),
        VAMPIRIC("vampiric", "RIFT_TROPHY_VAMPIRIC", "Vampiric Timecharm", 0xFFFF5555.toInt()),
        MOUNTAIN("mountain", "RIFT_TROPHY_MOUNTAIN", "Celestial Timecharm", 0xFF5555FF.toInt());

        fun apiType(): String = apiType

        fun itemId(): String = itemId

        fun displayName(): String = displayName

        fun color(): Int = color

        companion object {
            @JvmStatic
            fun fromApiType(raw: String?): TimecharmDef? {
                if (raw.isNullOrBlank()) return null
                val key = raw.trim().lowercase().replace("rift_trophy_", "").replace('-', '_')
                if (key == "celestial") return MOUNTAIN
                return entries.firstOrNull { it.apiType == key || it.itemId.equals(raw.trim(), ignoreCase = true) }
            }
        }
    }

    class Timecharm(
        id: String?,
        itemId: String?,
        name: String?,
        color: Int,
        @get:JvmName("secured") val secured: Boolean,
        @get:JvmName("securedAtMs") val securedAtMs: Long,
        @get:JvmName("visitsToGet") val visitsToGet: Int,
    ) {
        @get:JvmName("id") val id: String = id ?: ""
        @get:JvmName("itemId") val itemId: String = if (itemId.isNullOrBlank()) "RIFT_TROPHY_" + this.id.uppercase() else itemId
        @get:JvmName("name") val name: String = if (name.isNullOrBlank()) InventoryDecoder.prettyWords(this.id) else name
        @get:JvmName("color") val color: Int = if (color == 0) DEFAULT_CHARM_COLOR else color
    }

    @JvmRecord
    data class VampireProgress(val level: Int, val fill: Float, val maxed: Boolean, val hover: String, val xp: Float) {
        companion object {
            @JvmStatic
            fun empty(): VampireProgress = VampireProgress(0, 0f, false, "", 0f)
        }
    }

    class QuestLine(label: String?, value: String?) {
        @get:JvmName("label") val label: String = label ?: ""
        @get:JvmName("value") val value: String = value ?: ""
    }

    class QuestRegion(title: String?, lines: List<QuestLine>?) {
        @get:JvmName("title") val title: String = title ?: ""
        @get:JvmName("lines") val lines: List<QuestLine> = lines?.toList() ?: emptyList()

        fun present(): Boolean = title.isNotBlank() && lines.isNotEmpty()
    }

    @get:JvmName("motesPurse") val motesPurse: Long = maxOf(0L, motesPurse)
    @get:JvmName("lifetimeMotes") val lifetimeMotes: Long = maxOf(0L, lifetimeMotes)
    @get:JvmName("visits") val visits: Long = maxOf(0L, visits)
    @get:JvmName("lastFreeAccessMs") val lastFreeAccessMs: Long = maxOf(0L, lastFreeAccessMs)
    @get:JvmName("enigmaFound") val enigmaFound: Int = maxOf(0, enigmaFound)
    @get:JvmName("enigmaBonusIndex") val enigmaBonusIndex: Int = maxOf(0, enigmaBonusIndex)
    @get:JvmName("enigmaSouls") val enigmaSouls: List<String> = enigmaSouls?.toList() ?: emptyList()
    @get:JvmName("timecharmsSecured") val timecharmsSecured: Int = maxOf(0, timecharmsSecured)
    @get:JvmName("eliseStep") val eliseStep: Int = maxOf(0, eliseStep)
    @get:JvmName("timecharms") val timecharms: List<Timecharm> = timecharms?.toList() ?: emptyList()
    @get:JvmName("burgers") val burgers: Int = burgers.coerceIn(0, BURGER_MAX)
    @get:JvmName("catsFound") val catsFound: Int = maxOf(0, catsFound)
    @get:JvmName("montezumaTier") val montezumaTier: String = montezumaTier ?: ""
    @get:JvmName("foundCats") val foundCats: List<String> = foundCats?.toList() ?: emptyList()
    @get:JvmName("eyesKilled") val eyesKilled: Int = maxOf(0, eyesKilled)
    @get:JvmName("killedEyes") val killedEyes: List<String> = killedEyes?.toList() ?: emptyList()
    @get:JvmName("purchasedBoundaries") val purchasedBoundaries: List<String> = purchasedBoundaries?.toList() ?: emptyList()
    @get:JvmName("vampire") val vampire: VampireProgress = vampire ?: VampireProgress.empty()
    @get:JvmName("inventory") val inventory: InventorySnapshot.Page = inventory ?: InventorySnapshot.emptyPage("Inventory", 9)
    @get:JvmName("enderPages") val enderPages: List<InventorySnapshot.Page> =
        if (enderPages.isNullOrEmpty()) listOf(InventorySnapshot.emptyPage("Ender Chest", 9)) else enderPages.toList()
    @get:JvmName("questRegions") val questRegions: List<QuestRegion> = questRegions?.toList() ?: emptyList()

    fun enigmaFill(): Float = minOf(1f, enigmaFound / ENIGMA_MAX.toFloat())

    fun timecharmFill(): Float = minOf(1f, timecharmsSecured / TIMECHARM_MAX.toFloat())

    fun burgerFill(): Float = minOf(1f, burgers / BURGER_MAX.toFloat())

    fun catsFill(): Float = minOf(1f, catsFound / MONTEZUMA_CATS_MAX.toFloat())

    fun eyesFill(): Float = minOf(1f, eyesKilled / EYES_MAX.toFloat())

    companion object {
        const val ENIGMA_MAX = 52
        const val TIMECHARM_MAX = 8
        const val BURGER_MAX = 5
        const val MONTEZUMA_CATS_MAX = 9
        const val EYES_MAX = 7
        private const val DEFAULT_CHARM_COLOR = 0xFFFFAA00.toInt()

        @JvmStatic
        fun empty(): RiftSnapshot = RiftSnapshot(
            0, 0, 0, 0,
            0, false, 0, emptyList(),
            0, 0, parseTimecharms(null),
            0, 0, false, "", emptyList(),
            0, emptyList(),
            emptyList(),
            VampireProgress.empty(),
            InventorySnapshot.emptyPage("Inventory", 9),
            listOf(InventorySnapshot.emptyPage("Ender Chest", 9)),
            emptyList(),
        )

        @JvmStatic
        fun fromMember(member: JsonObject?): RiftSnapshot {
            if (member == null) return empty()

            val currencies = Leveling.obj(member.get("currencies"))
            val motes = longVal(currencies, "motes_purse")

            val stats = Leveling.obj(member.get("player_stats"))
            val riftStats = Leveling.obj(stats?.get("rift"))
            val lifetime = longVal(riftStats, "lifetime_motes_earned")
            val visits = longVal(riftStats, "visits")

            val rift = Leveling.obj(member.get("rift"))
            val access = Leveling.obj(rift?.get("access"))
            val lastFree = longVal(access, "last_free")

            val enigma = Leveling.obj(rift?.get("enigma"))
            val souls = stringList(enigma?.get("found_souls"))
            val cloak = bool(enigma, "bought_cloak")
            val bonus = intVal(enigma, "claimed_bonus_index")

            val gallery = Leveling.obj(rift?.get("gallery"))
            val elise = intVal(gallery, "elise_step")
            val charms = parseTimecharms(gallery?.get("secured_trophies"))
            val securedCount = charms.count { it.secured }

            val castle = Leveling.obj(rift?.get("castle"))
            val burgers = intVal(castle, "grubber_stacks")

            val deadCats = Leveling.obj(rift?.get("dead_cats"))
            val cats = stringList(deadCats?.get("found_cats"))
            val petUnlocked = bool(deadCats, "unlocked_pet")
            val montezuma = Leveling.obj(deadCats?.get("montezuma"))
            val tier = if (montezuma == null) "" else str(montezuma.get("tier"))

            val witherCage = Leveling.obj(rift?.get("wither_cage"))
            val eyes = stringList(witherCage?.get("killed_eyes"))

            val boundaries = stringList(rift?.get("lifetime_purchased_boundaries")).sortedWith(String.CASE_INSENSITIVE_ORDER)

            val vampireXp = Leveling.readSlayerXp(member, "vampire")
            val table = RepoData.slayerXp("vampire")
            val cap = if (table == null || table.isEmpty) 5 else table.size()
            val vp = Leveling.getLevel(table, vampireXp, cap, true)
            val vampire = VampireProgress(
                floor(vp.level).toInt(),
                vp.fill(),
                vp.maxed,
                vp.slayerHover("Vampire"),
                vampireXp,
            )

            val inv = InventoryDecoder.parseRiftUi(member)
            val quests = parseQuestRegions(rift)

            return RiftSnapshot(
                motes,
                lifetime,
                visits,
                lastFree,
                souls.size,
                cloak,
                bonus,
                souls,
                securedCount,
                elise,
                charms,
                burgers,
                cats.size,
                petUnlocked,
                tier,
                cats,
                eyes.size,
                eyes,
                boundaries,
                vampire,
                inv.inventory,
                inv.enderPages,
                quests,
            )
        }

        private fun parseQuestRegions(rift: JsonObject?): List<QuestRegion> {
            if (rift == null) return emptyList()
            val out = ArrayList<QuestRegion>()
            addRegion(out, "Wyld Woods", wyldWoodsLines(Leveling.obj(rift.get("wyld_woods"))))
            addRegion(out, "Dreadfarm", dreadfarmLines(Leveling.obj(rift.get("dreadfarm"))))
            addRegion(out, "West Village", westVillageLines(Leveling.obj(rift.get("west_village"))))
            addRegion(out, "Castle", castleLines(Leveling.obj(rift.get("castle"))))
            addRegion(out, "Black Lagoon", blackLagoonLines(Leveling.obj(rift.get("black_lagoon"))))
            addRegion(out, "Village Plaza", villagePlazaLines(Leveling.obj(rift.get("village_plaza"))))
            addRegion(out, "Wizard Tower", wizardTowerLines(Leveling.obj(rift.get("wizard_tower"))))
            return out.toList()
        }

        private fun addRegion(out: MutableList<QuestRegion>, title: String, lines: List<QuestLine>) {
            if (lines.isNotEmpty()) out.add(QuestRegion(title, lines))
        }

        private fun wyldWoodsLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            if (intVal(obj, "bughunter_step") > 0) {
                // No published mapping for bughunter_step integers.
                lines.add(QuestLine("Argofay Bughunter", "In Progress"))
            }
            if (bool(obj, "sirius_completed_q_a") || bool(obj, "sirius_q_a_chain_done")) {
                lines.add(QuestLine("Inverted Sirius", if (bool(obj, "sirius_claimed_doubloon")) "Claimed" else "Complete"))
            } else if (bool(obj, "sirius_started_q_a")) {
                lines.add(QuestLine("Inverted Sirius", "In Progress"))
            }
            val brothers = stringList(obj.get("talked_threebrothers")).size
            if (brothers > 0) {
                // Size alone is not an explicit completion flag; keep the count.
                lines.add(QuestLine("Argofay Threebrothers", "$brothers/3 talked"))
            }
            return lines
        }

        private fun dreadfarmLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            if (intVal(obj, "shania_stage") > 0) {
                // No published mapping for shania_stage integers (wand / harvest / reward stages).
                lines.add(QuestLine("Harvest for Shania", "In Progress"))
            }
            val feeder = countEntries(obj.get("caducous_feeder_uses"))
            if (feeder > 0) lines.add(QuestLine("Caducous Feeder", if (feeder == 1) "1 use" else "$feeder uses"))
            return lines
        }

        private fun westVillageLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            val kloon = Leveling.obj(obj.get("crazy_kloon"))
            if (kloon != null) {
                if (bool(kloon, "quest_complete")) {
                    lines.add(QuestLine("Unhinged Kloon", "Complete"))
                } else {
                    val terminals = stringList(kloon.get("hacked_terminals")).size
                    if (terminals > 0) {
                        lines.add(QuestLine("Unhinged Kloon", "$terminals/8 terminals"))
                    } else if (bool(kloon, "talked")) {
                        lines.add(QuestLine("Unhinged Kloon", "In Progress"))
                    }
                }
            }
            val glyphs = Leveling.obj(obj.get("glyphs"))
            if (glyphs != null) {
                if (bool(glyphs, "completed")) {
                    lines.add(QuestLine("Sorcerer Okron", "Complete"))
                } else {
                    val current = intVal(glyphs, "current_glyph")
                    if (current > 0) {
                        lines.add(QuestLine("Sorcerer Okron", "Glyph $current"))
                    } else if (bool(glyphs, "claimed_wand")) {
                        lines.add(QuestLine("Sorcerer Okron", "Wand claimed"))
                    }
                }
            }
            val kat = Leveling.obj(obj.get("kat_house"))
            if (kat != null) {
                val bins = intVal(kat, "bin_collected_mosquito") +
                    intVal(kat, "bin_collected_silverfish") +
                    intVal(kat, "bin_collected_spider")
                if (bins > 0) {
                    // Bin count alone is not an explicit completion boolean.
                    lines.add(QuestLine("Kat's Vermin Bins", "$bins/3 filled"))
                }
            }
            val mirror = Leveling.obj(obj.get("mirrorverse"))
            if (mirror != null) {
                if (bool(mirror, "claimed_reward")) {
                    lines.add(QuestLine("Mirrorverse", "Reward claimed"))
                } else {
                    val rooms = stringList(mirror.get("visited_rooms")).size
                    if (rooms > 0) lines.add(QuestLine("Mirrorverse", "$rooms/7 rooms"))
                }
            }
            return lines
        }

        private fun castleLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            if (intVal(obj, "fairy_step") > 0) {
                // No published mapping for fairy_step integers.
                lines.add(QuestLine("Castle Fairy", "In Progress"))
            }
            if (bool(obj, "unlocked_pathway_skip")) lines.add(QuestLine("Pathway Skip", "Unlocked"))
            return lines
        }

        private fun blackLagoonLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            when {
                bool(obj, "delivered_science_paper") -> lines.add(QuestLine("Dr. Hibble", "Complete"))
                bool(obj, "received_science_paper") -> lines.add(QuestLine("Dr. Hibble", "Paper received"))
                bool(obj, "talked_to_edwin") -> lines.add(QuestLine("Dr. Hibble", "Talked to Edwin"))
                // completed_step integers are undocumented; avoid raw Step N.
                intVal(obj, "completed_step") > 0 -> lines.add(QuestLine("Dr. Hibble", "In Progress"))
            }
            return lines
        }

        private fun villagePlazaLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            val murder = Leveling.obj(obj.get("murder"))
            if (murder != null && intVal(murder, "step_index") > 0) {
                // murder.step_index integers are undocumented.
                lines.add(QuestLine("Reverse-Murder", "In Progress"))
            }
            val cowboy = Leveling.obj(obj.get("cowboy"))
            if (cowboy != null) {
                val stage = intVal(cowboy, "stage")
                val hay = intVal(cowboy, "hay_eaten")
                val rabbit = str(cowboy.get("rabbit_name"))
                if (stage > 0 || hay > 0 || rabbit.isNotBlank()) {
                    when {
                        rabbit.isNotBlank() -> lines.add(QuestLine("Cowboy Nick", "Rabbit named"))
                        hay > 0 -> lines.add(QuestLine("Cowboy Nick", FormatUtil.commas(hay.toLong()) + " hay eaten"))
                        // cowboy.stage integers are undocumented.
                        else -> lines.add(QuestLine("Cowboy Nick", "In Progress"))
                    }
                }
            }
            val seraphine = Leveling.obj(obj.get("seraphine"))
            if (seraphine != null && intVal(seraphine, "step_index") > 0) {
                // seraphine.step_index integers are undocumented.
                lines.add(QuestLine("Seraphine's Face", "In Progress"))
            }
            return lines
        }

        private fun wizardTowerLines(obj: JsonObject?): List<QuestLine> {
            if (obj == null) return emptyList()
            val lines = ArrayList<QuestLine>()
            val step = intVal(obj, "wizard_quest_step")
            val crumbs = intVal(obj, "crumbs_laid_out")
            if (step > 0 || crumbs > 0) {
                if (crumbs > 0) {
                    lines.add(QuestLine("Wizard's Quest", "$crumbs crumbs laid"))
                } else {
                    // wizard_quest_step integers are undocumented.
                    lines.add(QuestLine("Wizard's Quest", "In Progress"))
                }
            }
            return lines
        }

        private fun countEntries(el: JsonElement?): Int = when {
            el == null || el.isJsonNull -> 0
            el.isJsonArray -> el.asJsonArray.size()
            el.isJsonObject -> el.asJsonObject.size()
            else -> 0
        }

        private fun parseTimecharms(element: JsonElement?): List<Timecharm> {
            val secured = HashMap<String, JsonObject>()
            if (element != null && element.isJsonArray) {
                for (child in element.asJsonArray) {
                    val obj = Leveling.obj(child) ?: continue
                    val type = str(obj.get("type"))
                    if (type.isBlank()) continue
                    val key = TimecharmDef.fromApiType(type)?.apiType() ?: type.trim().lowercase()
                    secured[key] = obj
                }
            }

            val out = ArrayList<Timecharm>(TIMECHARM_MAX)
            val seen = HashSet<String>()
            for (def in TimecharmDef.entries) {
                val obj = secured[def.apiType()]
                seen.add(def.apiType())
                out.add(
                    if (obj != null) {
                        Timecharm(def.apiType(), def.itemId(), def.displayName(), def.color(), true, longVal(obj, "timestamp"), intVal(obj, "visits"))
                    } else {
                        Timecharm(def.apiType(), def.itemId(), def.displayName(), def.color(), false, 0L, 0)
                    },
                )
            }
            // Unknown future trophies still show up after the known set.
            for ((key, obj) in secured) {
                if (key in seen) continue
                val type = str(obj.get("type"))
                out.add(
                    Timecharm(
                        type,
                        "RIFT_TROPHY_" + type.uppercase(),
                        InventoryDecoder.prettyWords(type),
                        DEFAULT_CHARM_COLOR,
                        true,
                        longVal(obj, "timestamp"),
                        intVal(obj, "visits"),
                    ),
                )
            }
            return out
        }

        private fun stringList(element: JsonElement?): List<String> {
            if (element == null || !element.isJsonArray) return emptyList()
            val out = ArrayList<String>()
            for (child in element.asJsonArray) {
                if (child == null || child.isJsonNull || !child.isJsonPrimitive) continue
                val value: String? = child.asString
                if (!value.isNullOrBlank()) out.add(value)
            }
            out.sortWith(String.CASE_INSENSITIVE_ORDER)
            return out
        }

        private fun bool(obj: JsonObject?, key: String): Boolean {
            if (obj == null || !obj.has(key) || obj.get(key).isJsonNull || !obj.get(key).isJsonPrimitive) return false
            return try {
                obj.get(key).asBoolean
            } catch (_: IllegalStateException) {
                false
            } catch (_: ClassCastException) {
                false
            } catch (_: NumberFormatException) {
                false
            } catch (_: UnsupportedOperationException) {
                false
            }
        }

        private fun intVal(obj: JsonObject?, key: String): Int {
            val n = Leveling.num(obj?.get(key)) ?: return 0
            return maxOf(0, Math.round(n))
        }

        private fun longVal(obj: JsonObject?, key: String): Long {
            // Double, not Float: epoch-ms timestamps lose minutes in float precision.
            val n = Leveling.numDouble(obj?.get(key))
            return if (n == null || n.isNaN()) 0L else maxOf(0L, Math.round(n))
        }

        private fun str(element: JsonElement?): String {
            if (element == null || element.isJsonNull || !element.isJsonPrimitive) return ""
            return try {
                element.asString
            } catch (_: IllegalStateException) {
                ""
            } catch (_: ClassCastException) {
                ""
            } catch (_: NumberFormatException) {
                ""
            } catch (_: UnsupportedOperationException) {
                ""
            }
        }
    }
}
