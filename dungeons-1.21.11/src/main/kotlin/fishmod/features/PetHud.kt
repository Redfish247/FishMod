package fishmod.features

import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern

object PetHud {

    private val COLOR_STRIP: Pattern = Pattern.compile("§.")

    // Tab patterns for 2-line layout
    private val TAB_NAME_LINE: Pattern = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s+(.+)")
    private val TAB_XP_LINE: Pattern = Pattern.compile("([\\d.,]+[KMB]?)\\s*/\\s*([\\d.,]+[KMB]?)\\s*XP")

    // Chat is the authoritative source for the active pet (Autopet/summon/despawn lines).
    private val AUTOPET_PAT: Pattern = Pattern.compile("Autopet equipped your \\[Lvl\\s*(\\d+)\\]\\s*(.+?)!")
    private val SUMMON_PAT: Pattern = Pattern.compile("You summoned your\\s+(.+?)!")
    private val DESPAWN_PAT: Pattern = Pattern.compile("(?:You|Autopet) despawned your\\s+(.+?)!")
    // Loadout switches can silently swap the pet without an Autopet/summon line; treat as a re-sync signal.
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

    private var lastTabUpdate = 0L // prevents /pets menu from overwriting tab-list data

    // API is authoritative for level/XP since tab has no pet entry in dungeons; refreshed periodically.
    private const val API_REFRESH_MS = 60_000L
    private var lastApiFetchAt = 0L
    private var apiFetchInFlight = false

    // A pet earns 1 Pet XP per 1 matching-skill XP gained; https://wiki.hypixel.net/Pets#Pet_XP
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

        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (overlay || !FishSettings.petHudEnabled) return@register
            val s = COLOR_STRIP.matcher(msg.string).replaceAll("").trim()

            val a = AUTOPET_PAT.matcher(s)
            if (a.find()) {
                petLevel = safeInt(a.group(1), -1)
                petName = cleanPetName(a.group(2))
                petMaxed = false
                xpCurrent = -1.0; xpNext = -1.0; pendingXp = 0.0
                lastTabUpdate = System.currentTimeMillis()
                lastApiFetchAt = 0
                forceScanTicks = 10
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.text.Text.literal("§d[pet] autopet → [$petLevel] $petName"))
                return@register
            }
            val su = SUMMON_PAT.matcher(s)
            if (su.find()) {
                val n = cleanPetName(su.group(1))
                petName = n
                petMaxed = false
                xpCurrent = -1.0; xpNext = -1.0; pendingXp = 0.0
                lastTabUpdate = System.currentTimeMillis()
                lastApiFetchAt = 0
                forceScanTicks = 10
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.text.Text.literal("§d[pet] summon → $petName"))
                return@register
            }
            // No forced tab scan here — the tab list lags this event and would show a stale pet name.
            val lo = LOADOUT_EQUIP_PAT.matcher(s)
            if (lo.find()) {
                lastApiFetchAt = 0
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.text.Text.literal("§d[pet] loadout equip → re-sync"))
            }
        }

        // Action-bar listener: Hypixel emits "+X.X <Skill> (current/next)" each gain.
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
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
            if (!FishSettings.petHudEnabled || client.networkHandler == null) return@register
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

            // Dungeons have no Pet: tab entry, so rely on chat + the API refresh there instead.
            if (Location.inDungeon()) return@register

            scanPetsMenuIfOpen(client.currentScreen)

            if (forceScanTicks > 0) {
                forceScanTicks--
                scanTabList(client.networkHandler!!)
            }

            tickCount++
            if (tickCount >= 5) {
                tickCount = 0
                scanTabList(client.networkHandler!!)
            }
        }
    }

    private fun scanTabList(handler: ClientPlayNetworkHandler) {
        // The pet line is "[Lvl N] Name" under the "Pet:" header; "[Lvl " is unique vs player names' "[519]".
        var tempName: String? = null
        var tempLevel = -1
        var maxed = false
        var overflowXp = -1.0

        for (entry in handler.playerList) {
            if (entry.displayName == null) continue
            val text = COLOR_STRIP.matcher(entry.displayName!!.string).replaceAll("").trim()
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
                xpNext = -1.0
                // Overflow level = total XP (overflow shown + XP to reach max level) converted back to a level.
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

    /** Applies the API-fetched active pet as the authoritative baseline (runs on the client thread). */
    private fun applyApiPet(info: HypixelApi.PetInfo?) {
        apiFetchInFlight = false
        if (info == null || !info.ok) return
        petName = info.name
        petLevel = info.level
        petMaxed = info.maxed
        petOverflowLevel = if (info.maxed) info.overflowLevel else -1
        if (info.maxed) {
            xpNext = -1.0
        } else {
            xpCurrent = info.xpIntoLevel
            xpNext = info.xpForNext
            xpPct = info.pct
        }
        pendingXp = 0.0
        lastTabUpdate = System.currentTimeMillis()
    }

    /** Overflow level for a maxed pet (e.g. 142 for a Lvl 100 pet past max), or -1 if not maxed/unknown. */
    @JvmStatic
    fun getOverflowLevel(): Int = petOverflowLevel

    private fun scanPetsMenuIfOpen(current: Screen?) {
        if (System.currentTimeMillis() - lastTabUpdate < 2000) return

        if (current !is GenericContainerScreen) return
        val title = COLOR_STRIP.matcher(current.title.string).replaceAll("").trim()
        if (!title.startsWith("Pets")) return

        val handler = current.screenHandler
        for (slot in handler.slots) {
            val stack = slot.stack
            if (stack == null || stack.isEmpty || !ItemUtil.containsLore(stack, "Click to despawn")) continue

            val displayName = COLOR_STRIP.matcher(stack.name.string).replaceAll("").trim()
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
        val lore = stack.get(net.minecraft.component.DataComponentTypes.LORE) ?: return
        val lines = lore.lines()
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
    fun renderHud(ctx: DrawContext, tickCounter: RenderTickCounter) {
        if (!FishSettings.petHudEnabled) return
        val mc = MinecraftClient.getInstance()
        if (mc.player == null || !Location.inSkyblock() || petName == null) return

        if (FishSettings.petHudFadeIdle && pendingXp > 0 && (System.currentTimeMillis() - lastXpAt) > FishSettings.petHudFadeMs) {
            pendingXp = 0.0
        }

        val text = StringBuilder()
        if (FishSettings.petHudShowLevel && petLevel >= 0) text.append("§7[Lvl ").append(petLevel).append("] ")
        text.append("§6").append(petName)

        // Golden Dragon maxes at 200; all others at 100.
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
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(FishSettings.petHudX.toFloat(), FishSettings.petHudY.toFloat())
        ctx.matrices.scale(sc, sc)
        ctx.drawText(mc.textRenderer, text.toString(), 0, 0, -1, true)
        ctx.matrices.popMatrix()
    }

    private fun formatXp(v: Double): String {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000)
        if (v >= 1_000) return String.format("%.1fk", v / 1_000)
        return String.format("%.0f", v)
    }
}
