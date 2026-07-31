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

    private val TAB_NAME_LINE: Pattern = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s+(.+)")
    private val TAB_XP_LINE: Pattern = Pattern.compile("([\\d.,]+[KMB]?)\\s*/\\s*([\\d.,]+[KMB]?)\\s*XP")

    // Chat is the authoritative source for the active pet, matched against these Hypixel message formats.
    private val AUTOPET_PAT: Pattern = Pattern.compile("Autopet equipped your \\[Lvl\\s*(\\d+)\\]\\s*(.+?)!")
    private val SUMMON_PAT: Pattern = Pattern.compile("You summoned your\\s+(.+?)!")
    private val DESPAWN_PAT: Pattern = Pattern.compile("(?:You|Autopet) despawned your\\s+(.+?)!")
    // Loadout switch can silently swap pets with no Autopet/summon line, so it just triggers an API re-sync.
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
    private var forceScanTicks = 0
    private var pendingXp = 0.0
    private var lastXpAt = 0L
    private var tickCount = 0

    private var xpCurrent = -1.0
    private var xpNext = -1.0
    private var xpPct = -1f

    private var lastTabUpdate = 0L // guards against /pets menu overwriting fresher tab-list data

    // Tab format broke and dungeons have no pet tab entry, so the API is authoritative for level/XP.
    private const val API_REFRESH_MS = 60_000L
    private var lastApiFetchAt = 0L
    private var apiFetchInFlight = false

    // The SkyBlock profile API is a periodic snapshot; right after a pet swap it can still lag and
    // report the previous pet, so a stale API response disagreeing with a recent chat msg is discarded.
    private const val CHAT_TRUST_WINDOW_MS = 15_000L
    private var lastChatPetName: String? = null
    private var lastChatPetChangeAt = 0L

    // A pet earns 1 Pet XP per 1 skill XP in its matching skill, 0 otherwise. Source: wiki.hypixel.net/Pets#Pet_XP
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

        // Chat is authoritative for the active pet (tab/menu scraping is a fallback).
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
            // Force an API re-check on loadout switch; don't force a tab scan since the tab list
            // lags this chat line and would briefly read the stale previous-pet entry.
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

            val nowMs = System.currentTimeMillis()
            if (!apiFetchInFlight && nowMs - lastApiFetchAt >= API_REFRESH_MS) {
                lastApiFetchAt = nowMs
                apiFetchInFlight = true
                HypixelApi.getActivePet(client, ::applyApiPet)
            }

            // Tab/menu don't reliably reflect the active pet in dungeons; rely on chat + API there.
            if (Location.inDungeon()) return@register

            scanPetsMenuIfOpen(client.screen)

            // Scan every tick for a short burst after an equip/summon, since tab can lag the chat msg.
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
        // "[Lvl N] Name" under the tab's "Pet:" header; "[Lvl " is unique vs player tags like "[519]".
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
                val ov = TAB_OVERFLOW_XP.matcher(text)
                if (ov.find()) overflowXp = parseAbbrev(ov.group(1))
            }
        }

        if (tempName != null) {
            petName = tempName
            petLevel = tempLevel
            petMaxed = maxed
            if (maxed) {
                xpNext = -1.0 // forces the HUD's MAXED display
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
            lastTabUpdate = System.currentTimeMillis()
        }
    }

    private fun applyApiPet(info: HypixelApi.PetInfo?) {
        apiFetchInFlight = false
        if (info == null || !info.ok) return

        // Discard a stale API result that disagrees with a recent chat-confirmed pet change,
        // and retry sooner (~3s) rather than waiting the full refresh interval.
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
        pendingXp = 0.0
        lastTabUpdate = System.currentTimeMillis()
    }

    @JvmStatic
    fun getOverflowLevel(): Int = petOverflowLevel

    private fun scanPetsMenuIfOpen(current: Screen?) {
        if (System.currentTimeMillis() - lastTabUpdate < 2000) return // don't fight a recent tab update

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
