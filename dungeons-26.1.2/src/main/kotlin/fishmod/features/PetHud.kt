package fishmod.features

import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern

object PetHud {

    private val COLOR_STRIP: Pattern = Pattern.compile("§.")

    // Tab patterns for 2-line layout
    private val TAB_NAME_LINE: Pattern = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s+(.+)")
    private val TAB_XP_LINE: Pattern = Pattern.compile("([\\d.,]+[KMB]?)\\s*/\\s*([\\d.,]+[KMB]?)\\s*XP")

    // Chat detection — the authoritative source for the active pet.
    // "Autopet equipped your [Lvl 100] Griffin! VIEW RULE"
    private val AUTOPET_PAT: Pattern = Pattern.compile("Autopet equipped your \\[Lvl\\s*(\\d+)\\]\\s*(.+?)!")
    // "You summoned your Golden Dragon ✦!"
    private val SUMMON_PAT: Pattern = Pattern.compile("You summoned your\\s+(.+?)!")
    // "You despawned your Golden Dragon ✦!" / "Autopet despawned your ..."
    private val DESPAWN_PAT: Pattern = Pattern.compile("(?:You|Autopet) despawned your\\s+(.+?)!")
    // "You equipped <Loadout Name>!" (item-customizer loadout switch) — can silently swap the
    // active pet without an Autopet/summon line, so treat it as a signal to re-sync from the API.
    private val LOADOUT_EQUIP_PAT: Pattern = Pattern.compile("^You equipped (.+)!$")

    private val PET_ITEM_NAME: Pattern = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s*(.+)")
    private val PROGRESS_PAT: Pattern = Pattern.compile("Progress to Level \\d+:\\s*([\\d.]+)%")
    private val PROGRESS_XP_PAT: Pattern = Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+[KMB]?)")

    @JvmField
    var debugDumpPetLines = false

    private val TAB_OVERFLOW_XP: Pattern = Pattern.compile("\\+([\\d.,]+[KMB]?)\\s*XP")

    private var petName: String? = null
    private var petLevel = -1
    private var petOverflowLevel = -1
    private var petMaxed = false
    private var forceScanTicks = 0 // after equip/summon, scan tab every tick briefly
    private var pendingXp = 0.0
    private var lastXpAt = 0L
    private var tickCount = 0

    private var xpCurrent = -1.0
    private var xpNext = -1.0
    private var xpPct = -1f

    // Prevents /pets menu from overwriting Tab list data
    private var lastTabUpdate = 0L

    // API is the authoritative source for the active pet's level/XP (tab format broke and dungeons
    // have no pet tab entry). Refresh periodically and immediately after a pet change.
    private const val API_REFRESH_MS = 60_000L
    private var lastApiFetchAt = 0L
    private var apiFetchInFlight = false

    // Hypixel's SkyBlock profile API is a periodic snapshot, not real-time — right after an
    // Autopet/summon swap it can still lag behind and report the PREVIOUS pet for several
    // seconds. Track the chat-confirmed name/time so a stale API response that disagrees with
    // a recent chat message gets discarded instead of clobbering the correct name back.
    private const val CHAT_TRUST_WINDOW_MS = 15_000L
    private var lastChatPetName: String? = null
    private var lastChatPetChangeAt = 0L

    // Hypixel wiki: a pet earns 1 Pet XP per 1 skill XP gained in its matching skill.
    // Non-matching skills give 0. Source: https://wiki.hypixel.net/Pets#Pet_XP
    private val SKILL_XP_BAR: Pattern = Pattern.compile(
            "\\+\\s*([\\d,.]+)\\s+(Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Runecrafting|Taming)\\b")
    private val PET_SKILL: Map<String, String> = java.util.Map.ofEntries(
        java.util.Map.entry("Elephant", "Farming"),
        java.util.Map.entry("Rabbit", "Farming"),
        java.util.Map.entry("Bee", "Farming"),
        java.util.Map.entry("Mooshroom Cow", "Farming"),
        java.util.Map.entry("Slug", "Farming"),
        java.util.Map.entry("Hedgehog", "Farming"),
        java.util.Map.entry("Chicken", "Farming"),
        java.util.Map.entry("Squid", "Fishing"),
        java.util.Map.entry("Megalodon", "Fishing"),
        java.util.Map.entry("Blue Whale", "Fishing"),
        java.util.Map.entry("Dolphin", "Fishing"),
        java.util.Map.entry("Flying Fish", "Fishing"),
        java.util.Map.entry("Endermite", "Mining"),
        java.util.Map.entry("Silverfish", "Mining"),
        java.util.Map.entry("Rock", "Mining"),
        java.util.Map.entry("Mole", "Mining"),
        java.util.Map.entry("Bal", "Mining"),
        java.util.Map.entry("Tiger", "Combat"),
        java.util.Map.entry("Wolf", "Combat"),
        java.util.Map.entry("Skeleton", "Combat"),
        java.util.Map.entry("Spider", "Combat"),
        java.util.Map.entry("Enderman", "Combat"),
        java.util.Map.entry("Black Cat", "Combat"),
        java.util.Map.entry("Lion", "Foraging"),
        java.util.Map.entry("Monkey", "Foraging"),
        java.util.Map.entry("Giraffe", "Foraging"),
        java.util.Map.entry("Ocelot", "Foraging"),
        java.util.Map.entry("Treasure Hunter", "Foraging")
    )

    @JvmStatic
    fun init() {
        FishHudEditor.register("Pet",
                { FishSettings.petHudX }, { v -> FishSettings.petHudX = v },
                { FishSettings.petHudY }, { v -> FishSettings.petHudY = v },
                120, 10,
                { FishSettings.petHudScale }, { v -> FishSettings.petHudScale = v })

        // Chat listener: the active pet is announced on summon / autopet-rule equip.
        // This is the authoritative source (tab/menu scraping is a fallback).
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (overlay || !FishSettings.petHudEnabled) return@register
            val s = COLOR_STRIP.matcher(msg.string).replaceAll("").trim()

            val a = AUTOPET_PAT.matcher(s)
            if (a.find()) {
                petLevel = safeInt(a.group(1), -1)
                petName = cleanPetName(a.group(2))
                petMaxed = false // tab burst-scan re-confirms
                xpCurrent = -1.0; xpNext = -1.0; pendingXp = 0.0 // reset XP for the newly-equipped pet
                lastTabUpdate = System.currentTimeMillis()
                lastChatPetName = petName; lastChatPetChangeAt = System.currentTimeMillis()
                lastApiFetchAt = 0 // force an immediate API refetch for the new pet
                forceScanTicks = 10 // immediately pull level/xp/overflow from tab
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] autopet → [$petLevel] $petName"))
                return@register
            }
            val su = SUMMON_PAT.matcher(s)
            if (su.find()) {
                val n = cleanPetName(su.group(1))
                // Ignore non-pet "summoned your" lines (e.g. mounts) by keeping it simple — set name.
                petName = n
                petMaxed = false // tab burst-scan re-confirms
                xpCurrent = -1.0; xpNext = -1.0; pendingXp = 0.0
                lastTabUpdate = System.currentTimeMillis()
                lastChatPetName = petName; lastChatPetChangeAt = System.currentTimeMillis()
                lastApiFetchAt = 0 // force an immediate API refetch for the new pet
                forceScanTicks = 10 // immediately pull level/xp/overflow from tab
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] summon → $petName"))
                return@register
            }
            // Switching loadouts can silently change the equipped pet (no Autopet/summon line),
            // so force an immediate API re-check to pick up whatever pet is now active. Do NOT
            // fall back to a forced tab scan here: the tab list update lags this chat line, so a
            // forced scan can read the tab's still-stale previous-pet entry (e.g. a renamed pet)
            // and briefly show the wrong name. The API is the authoritative source; let the
            // periodic tab scan (every 5 ticks) pick things up naturally once the tab catches up.
            val lo = LOADOUT_EQUIP_PAT.matcher(s)
            if (lo.find()) {
                lastApiFetchAt = 0
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] loadout equip → re-sync"))
            }
        }

        // Action-bar listener: Hypixel emits "+X.X <Skill> (current/next)" each gain.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (!FishSettings.petHudEnabled || !overlay || petName == null) return@register
            val s = COLOR_STRIP.matcher(msg.string).replaceAll("")
            val matchSkill = PET_SKILL[petName] ?: return@register
            val m = SKILL_XP_BAR.matcher(s)
            while (m.find()) {
                if (!m.group(2).equals(matchSkill, ignoreCase = true)) continue
                try {
                    val rawSkillXp = m.group(1).replace(",", "").toDouble()
                    // Apply Hypixel pet-XP multipliers (wiki).
                    val mult = 1.0 *
                            (1 + FishSettings.petXpTamingLevel * 0.01) *
                            (1 + FishSettings.petXpBeastmasterBonus / 100.0) *
                            (1 + FishSettings.petXpPetItemBonus / 100.0) *
                            (if (FishSettings.petXpBoosterCookie) 1.20 else 1.0)
                    val gain = rawSkillXp * mult
                    pendingXp += gain
                    lastXpAt = System.currentTimeMillis()
                    if (xpCurrent >= 0 && xpNext > 0) xpCurrent = minOf(xpCurrent + gain, xpNext)
                } catch (ignored: NumberFormatException) {}
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.petHudEnabled || client.connection == null) return@register
            if (!Location.inSkyblock()) {
                reset()
                return@register
            }

            // Authoritative pet level/XP + multipliers from the API.
            val nowMs = System.currentTimeMillis()
            if (!apiFetchInFlight && nowMs - lastApiFetchAt >= API_REFRESH_MS) {
                lastApiFetchAt = nowMs
                apiFetchInFlight = true
                HypixelApi.getActivePet(client, ::applyApiPet)
            }

            // Tab list and the /pets menu don't reliably reflect the active pet in dungeons
            // (no Pet: tab entry) — in dungeons rely solely on chat (Autopet/summon/loadout
            // messages) plus the API refresh above.
            if (Location.inDungeon()) return@register

            scanPetsMenuIfOpen(client.screen)

            // After an equip/summon, scan every tick for a short burst (tab can lag the chat msg).
            if (forceScanTicks > 0) {
                forceScanTicks--
                scanTabList(client.connection!!)
            }

            tickCount++
            if (tickCount >= 5) {
                tickCount = 0
                scanTabList(client.connection!!)
            }
        }
    }

    private fun scanTabList(handler: ClientPacketListener) {
        // The equipped pet shows in the tab list as "[Lvl N] Name" (under the "Pet:" header).
        // The "[Lvl " prefix is unique to the pet line — player names use "[519]"/"[MVP+]" —
        // so we just match it directly. (The old code required a separate "x/y XP" line that
        // the tab never has, so it never committed; the pet maxed shows "MAX LEVEL" instead.)
        var tempName: String? = null
        var tempLevel = -1
        var maxed = false
        var overflowXp = -1.0

        for (entry: PlayerInfo in handler.onlinePlayers) {
            if (entry.tabListDisplayName == null) continue
            val text = COLOR_STRIP.matcher(entry.tabListDisplayName!!.string).replaceAll("").trim()
            val nameMatch = TAB_NAME_LINE.matcher(text)
            if (nameMatch.find()) {
                tempLevel = safeInt(nameMatch.group(1), -1)
                tempName = nameMatch.group(2).replace("✦", "").trim()
            } else if (text.equals("MAX LEVEL", ignoreCase = true)) {
                maxed = true
            } else {
                val ov = TAB_OVERFLOW_XP.matcher(text) // "+1,234 XP" overflow line under Pet:
                if (ov.find()) overflowXp = parseAbbrev(ov.group(1))
            }
        }

        if (tempName != null) {
            petName = tempName
            petLevel = tempLevel
            petMaxed = maxed
            if (maxed) {
                xpNext = -1.0 // forces the HUD's MAXED display
                // Overflow level: total XP = overflow shown + XP to reach the max level.
                if (overflowXp >= 0 && tempLevel > 0) {
                    val rar = OverflowPetLevels.Rarity.LEGENDARY
                    val total = overflowXp + OverflowPetLevels.getCalculativeXpForLevel(tempLevel, rar)
                    petOverflowLevel = OverflowPetLevels.calcLevel(total, rar)
                } else {
                    petOverflowLevel = tempLevel
                }
            } else {
                petOverflowLevel = -1
            }
            lastTabUpdate = System.currentTimeMillis() // authority mark
        }
    }

    /** Applies the API-fetched active pet as the authoritative baseline (runs on the client thread). */
    private fun applyApiPet(info: HypixelApi.PetInfo?) {
        apiFetchInFlight = false
        if (info == null || !info.ok) return

        // The SkyBlock profile API is a periodic snapshot, not live — right after a chat-confirmed
        // pet change it can still report the PREVIOUS pet for a while. Discard a mismatched result
        // during the trust window instead of letting it clobber the correct chat-driven name, and
        // retry sooner (a few seconds) rather than waiting the full periodic refresh interval.
        val withinTrustWindow = System.currentTimeMillis() - lastChatPetChangeAt < CHAT_TRUST_WINDOW_MS
        if (withinTrustWindow && lastChatPetName != null && !lastChatPetName.equals(info.name, ignoreCase = true)) {
            lastApiFetchAt = System.currentTimeMillis() - API_REFRESH_MS + 3_000L // retry in ~3s
            return
        }

        petName = info.name
        petLevel = info.level
        petMaxed = info.maxed
        petOverflowLevel = if (info.maxed) info.overflowLevel else -1
        if (info.maxed) {
            xpNext = -1.0 // HUD shows MAXED
        } else {
            xpCurrent = info.xpIntoLevel
            xpNext = info.xpForNext
            xpPct = info.pct
        }
        pendingXp = 0.0 // baseline re-synced; clear accumulated live estimate
        lastTabUpdate = System.currentTimeMillis() // treat API as authority over the menu scraper
    }

    /** Overflow level for a maxed pet (e.g. 142 for a Lvl 100 pet past max), or -1 if not maxed/unknown. */
    @JvmStatic
    fun getOverflowLevel(): Int = petOverflowLevel

    private fun scanPetsMenuIfOpen(current: Screen?) {
        // Stop flopping: If Tab updated in last 2 seconds, don't use menu data
        if (System.currentTimeMillis() - lastTabUpdate < 2000) return

        if (current !is ContainerScreen) return
        val title = COLOR_STRIP.matcher(current.title.string).replaceAll("").trim()
        if (!title.startsWith("Pets")) return

        val handler: AbstractContainerMenu = current.menu
        for (slot in handler.slots) {
            val stack: ItemStack = slot.item
            if (stack == null || stack.isEmpty || !ItemUtil.containsLore(stack, "Click to despawn")) continue

            val displayName = COLOR_STRIP.matcher(stack.hoverName.string).replaceAll("").trim()
            val m = PET_ITEM_NAME.matcher(displayName)
            if (m.find()) {
                petLevel = safeInt(m.group(1), petLevel)
                petName = m.group(2).trim()
            }
            scanProgressFromLore(stack)
            return
        }
    }

    private fun scanProgressFromLore(stack: ItemStack) {
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return
        val lines: List<net.minecraft.network.chat.Component> = lore.lines()
        for (i in lines.indices) {
            val s = COLOR_STRIP.matcher(lines[i].string).replaceAll("").trim()
            val pm = PROGRESS_PAT.matcher(s)
            if (!pm.find()) continue
            try { xpPct = pm.group(1).toFloat() } catch (ignored: NumberFormatException) {}
            if (i + 1 < lines.size) {
                val s2 = COLOR_STRIP.matcher(lines[i + 1].string).replaceAll("").trim()
                val xm = PROGRESS_XP_PAT.matcher(s2)
                if (xm.find()) {
                    xpCurrent = parseAbbrev(xm.group(1))
                    xpNext = parseAbbrev(xm.group(2))
                }
            }
            return
        }
    }

    private fun parseAbbrev(sIn: String?): Double {
        if (sIn == null) return -1.0
        var s = sIn.replace(",", "").trim().uppercase()
        var mult = 1.0
        if (s.endsWith("K")) { mult = 1_000.0; s = s.substring(0, s.length - 1) }
        else if (s.endsWith("M")) { mult = 1_000_000.0; s = s.substring(0, s.length - 1) }
        else if (s.endsWith("B")) { mult = 1_000_000_000.0; s = s.substring(0, s.length - 1) }
        return try { s.toDouble() * mult } catch (e: NumberFormatException) { -1.0 }
    }

    private fun reset() {
        petName = null
        petLevel = -1
        xpCurrent = -1.0
        lastChatPetName = null
        lastChatPetChangeAt = 0
    }

    /** Strips rarity star, trailing punctuation, and whitespace from a pet name. */
    private fun cleanPetName(s: String?): String? {
        if (s == null) return null
        return s.replace("✦", "").replace(Regex("[!.]+$"), "").trim()
    }

    private fun safeInt(s: String?, fallback: Int): Int {
        return try { s!!.toInt() } catch (e: NumberFormatException) { fallback } catch (e: NullPointerException) { fallback }
    }

    @JvmStatic
    fun debugState(): String {
        return String.format("petHudEnabled=%s | name=%s | level=%d | currentXP=%.0f | nextXP=%.0f",
                FishSettings.petHudEnabled, petName, petLevel, xpCurrent, xpNext)
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!FishSettings.petHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || !Location.inSkyblock() || petName == null) return

        if (FishSettings.petHudFadeIdle && pendingXp > 0 && (System.currentTimeMillis() - lastXpAt) > FishSettings.petHudFadeMs) {
            pendingXp = 0.0
        }

        val text = StringBuilder()
        if (FishSettings.petHudShowLevel && petLevel >= 0) text.append("§7[Lvl ").append(petLevel).append("] ")
        text.append("§6").append(petName)

        // Detect max-level pet (Golden Dragon = 200, all others = 100). No progress line in lore at max.
        val maxLvl = if ("Golden Dragon".equals(petName, ignoreCase = true)) 200 else 100
        val maxed = petLevel >= maxLvl || petMaxed

        if (maxed) {
            text.append(" §a§lMAXED")
        } else {
            if (pendingXp > 0) text.append(" §a+").append(formatXp(pendingXp))
            if (xpCurrent >= 0 && xpNext > 0) {
                text.append(" §7(").append(formatXp(xpCurrent)).append("/").append(formatXp(xpNext))
                        .append(" ").append(String.format("%.1f%%", xpPct)).append(")")
            }
        }

        val sc = FishSettings.petHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.petHudX.toFloat(), FishSettings.petHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, text.toString(), 0, 0, -1, true)
        ctx.pose().popMatrix()
    }

    private fun formatXp(v: Double): String {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000)
        if (v >= 1_000) return String.format("%.1fk", v / 1_000)
        return String.format("%.0f", v)
    }
}
