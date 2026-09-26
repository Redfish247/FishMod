package fishmod.features

import fishmod.features.item.ItemRarity
import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

private val TRAILING_PUNCT_RE = Regex("[!.]+$")

object PetHud {

    private val TAB_NAME_LINE: Pattern = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s+(.+)")

    private val AUTOPET_PAT: Pattern = Pattern.compile("Autopet equipped your \\[Lvl\\s*(\\d+)\\]\\s*(.+?)!")
    private val SUMMON_PAT: Pattern = Pattern.compile("You summoned your\\s+(.+?)!")
    private val DESPAWN_PAT: Pattern = Pattern.compile("(?:You|Autopet) despawned your\\s+(.+?)!")
    private val LOADOUT_EQUIP_PAT: Pattern = Pattern.compile("^You equipped (.+)!$")

    private val PET_ITEM_NAME: Pattern = Pattern.compile("\\[Lvl\\s*(\\d+)\\]\\s*(.+)")
    private val PROGRESS_PAT: Pattern = Pattern.compile("Progress to Level \\d+:\\s*([\\d.]+)%")
    private val PROGRESS_XP_PAT: Pattern = Pattern.compile("([\\d.,]+)\\s*/\\s*([\\d.,]+[KMB]?)")

    @JvmField
    var debugDumpPetLines = false

    private val TAB_OVERFLOW_XP: Pattern = Pattern.compile("\\+([\\d.,]+[KMB]?)\\s*XP")

    @JvmStatic fun activePetName(): String? = petName

    private var petName: String? = null
    private var petRarity: ItemRarity = ItemRarity.NONE
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

    private var lastTabUpdate = 0L

    private const val API_REFRESH_MS = 60_000L
    private var lastApiFetchAt = 0L
    private var apiFetchInFlight = false

    private const val CHAT_TRUST_WINDOW_MS = 15_000L
    private var lastChatPetName: String? = null
    private var lastChatPetChangeAt = 0L

    private const val TAB_CLOBBER_GUARD_MS = 2_000L

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

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (overlay || !FishSettings.petHudEnabled) return@register
            val s = HypixelApi.STRIP_COLOR.matcher(msg.string).replaceAll("").trim()

            val a = AUTOPET_PAT.matcher(s)
            if (a.find()) {
                petLevel = safeInt(a.group(1), -1)
                petName = cleanPetName(a.group(2))
                petRarity = rarityBeforeName(msg.string, petName)
                petMaxed = false
                xpCurrent = -1.0; xpNext = -1.0; pendingXp = 0.0
                lastTabUpdate = System.currentTimeMillis()
                lastChatPetName = petName; lastChatPetChangeAt = System.currentTimeMillis()
                lastApiFetchAt = 0
                forceScanTicks = 10
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] autopet → [$petLevel] $petName"))
                return@register
            }
            val su = SUMMON_PAT.matcher(s)
            if (su.find()) {
                val n = cleanPetName(su.group(1))
                petName = n
                petRarity = rarityBeforeName(msg.string, n)
                petMaxed = false
                xpCurrent = -1.0; xpNext = -1.0; pendingXp = 0.0
                lastTabUpdate = System.currentTimeMillis()
                lastChatPetName = petName; lastChatPetChangeAt = System.currentTimeMillis()
                lastApiFetchAt = 0
                forceScanTicks = 10
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] summon → $petName"))
                return@register
            }
            val lo = LOADOUT_EQUIP_PAT.matcher(s)
            if (lo.find()) {
                lastApiFetchAt = 0
                if (debugDumpPetLines) fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§d[pet] loadout equip → re-sync"))
            }
        }

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (!FishSettings.petHudEnabled || !overlay || petName == null) return@register
            val s = HypixelApi.STRIP_COLOR.matcher(msg.string).replaceAll("")
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

            if (Location.inDungeon()) return@register

            scanPetsMenuIfOpen(client.screen)

            if (forceScanTicks > 0) {
                forceScanTicks--
                TabListCache.forceScan()
                scanTabList()
            }

            tickCount++
            if (tickCount >= 5) {
                tickCount = 0
                scanTabList()
            }
        }
    }

    private fun scanTabList() {
        var tempName: String? = null
        var tempLevel = -1
        var tempRarity = ItemRarity.NONE
        var maxed = false
        var overflowXp = -1.0

        for (entry in TabListCache.entries) {
            val raw = entry.info.tabListDisplayName?.string ?: continue
            val text = entry.stripped.trim()
            val nameMatch = TAB_NAME_LINE.matcher(text)
            if (nameMatch.find()) {
                tempLevel = safeInt(nameMatch.group(1), -1)
                tempName = nameMatch.group(2).replace("✦", "").trim()
                tempRarity = rarityBeforeName(raw, tempName)
            } else if (text.equals("MAX LEVEL", ignoreCase = true)) {
                maxed = true
            } else {
                val ov = TAB_OVERFLOW_XP.matcher(text)
                if (ov.find()) overflowXp = parseAbbrev(ov.group(1))
            }
        }

        if (tempName != null) {
            val withinClobberGuard = System.currentTimeMillis() - lastChatPetChangeAt < TAB_CLOBBER_GUARD_MS
            if (withinClobberGuard && lastChatPetName != null && !tempName.equals(lastChatPetName, ignoreCase = true)) {
                return
            }
            petName = tempName
            petLevel = tempLevel
            if (tempRarity != ItemRarity.NONE) petRarity = tempRarity
            petMaxed = maxed
            if (maxed) {
                xpNext = -1.0
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

        val withinTrustWindow = System.currentTimeMillis() - lastChatPetChangeAt < CHAT_TRUST_WINDOW_MS
        if (withinTrustWindow && lastChatPetName != null && !lastChatPetName.equals(info.name, ignoreCase = true)) {
            lastApiFetchAt = System.currentTimeMillis() - API_REFRESH_MS + 3_000L
            return
        }

        petName = info.name
        rarityFromTier(info.tier).let { if (it != ItemRarity.NONE) petRarity = it }
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

    private fun scanPetsMenuIfOpen(current: Screen?) {
        if (System.currentTimeMillis() - lastTabUpdate < 2000) return

        if (current !is ContainerScreen) return
        val title = HypixelApi.STRIP_COLOR.matcher(current.title.string).replaceAll("").trim()
        if (!title.startsWith("Pets")) return

        val handler: AbstractContainerMenu = current.menu
        for (slot in handler.slots) {
            val stack: ItemStack = slot.item
            if (stack == null || stack.isEmpty || !ItemUtil.containsLore(stack, "Click to despawn")) continue

            val displayName = HypixelApi.STRIP_COLOR.matcher(stack.hoverName.string).replaceAll("").trim()
            val m = PET_ITEM_NAME.matcher(displayName)
            if (m.find()) {
                petLevel = safeInt(m.group(1), petLevel)
                petName = m.group(2).trim()
                rarityBeforeName(stack.hoverName.string, petName).let { if (it != ItemRarity.NONE) petRarity = it }
            }
            scanProgressFromLore(stack)
            return
        }
    }

    private fun scanProgressFromLore(stack: ItemStack) {
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return
        val lines: List<net.minecraft.network.chat.Component> = lore.lines()
        for (i in lines.indices) {
            val s = HypixelApi.STRIP_COLOR.matcher(lines[i].string).replaceAll("").trim()
            val pm = PROGRESS_PAT.matcher(s)
            if (!pm.find()) continue
            try { xpPct = pm.group(1).toFloat() } catch (ignored: NumberFormatException) {}
            if (i + 1 < lines.size) {
                val s2 = HypixelApi.STRIP_COLOR.matcher(lines[i + 1].string).replaceAll("").trim()
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
        petRarity = ItemRarity.NONE
        petLevel = -1
        petOverflowLevel = -1
        petMaxed = false
        pendingXp = 0.0
        xpCurrent = -1.0
        xpNext = -1.0
        lastChatPetName = null
        lastChatPetChangeAt = 0
    }

    private fun cleanPetName(s: String?): String? {
        if (s == null) return null
        return s.replace("✦", "").replace(TRAILING_PUNCT_RE, "").trim()
    }

    private val RARITY_BY_CODE: Map<Char, ItemRarity> = mapOf(
        'f' to ItemRarity.COMMON, 'a' to ItemRarity.UNCOMMON, '9' to ItemRarity.RARE,
        '5' to ItemRarity.EPIC, '6' to ItemRarity.LEGENDARY, 'd' to ItemRarity.MYTHIC,
        'b' to ItemRarity.DIVINE, 'c' to ItemRarity.SPECIAL
    )

    private fun rarityBeforeName(raw: String?, name: String?): ItemRarity {
        if (raw == null || name.isNullOrEmpty()) return ItemRarity.NONE
        val plain = name.substringBefore("✦").trim()
        if (plain.isEmpty()) return ItemRarity.NONE
        val idx = raw.indexOf(plain)
        if (idx < 2) return ItemRarity.NONE
        var i = idx - 1
        while (i >= 1) {
            if (raw[i - 1] == '§') {
                val r = RARITY_BY_CODE[raw[i].lowercaseChar()]
                if (r != null) return r
                i -= 2
                continue
            }
            i--
        }
        return ItemRarity.NONE
    }

    private fun rarityFromTier(tier: String?): ItemRarity = when (tier?.uppercase()) {
        "COMMON" -> ItemRarity.COMMON
        "UNCOMMON" -> ItemRarity.UNCOMMON
        "RARE" -> ItemRarity.RARE
        "EPIC" -> ItemRarity.EPIC
        "LEGENDARY" -> ItemRarity.LEGENDARY
        "MYTHIC" -> ItemRarity.MYTHIC
        "DIVINE" -> ItemRarity.DIVINE
        else -> ItemRarity.NONE
    }

    private fun rarityCode(r: ItemRarity): String = when (r) {
        ItemRarity.COMMON -> "§f"
        ItemRarity.UNCOMMON -> "§a"
        ItemRarity.RARE -> "§9"
        ItemRarity.EPIC -> "§5"
        ItemRarity.LEGENDARY -> "§6"
        ItemRarity.MYTHIC -> "§d"
        ItemRarity.DIVINE -> "§b"
        ItemRarity.SPECIAL, ItemRarity.VERY_SPECIAL -> "§c"
        else -> "§6"
    }

    private fun nameColorCode(): String =
        if (FishSettings.petHudShowRarity && petRarity != ItemRarity.NONE) rarityCode(petRarity) else "§6"

    private fun safeInt(s: String?, fallback: Int): Int {
        return try { s!!.toInt() } catch (e: NumberFormatException) { fallback } catch (e: NullPointerException) { fallback }
    }

    @JvmStatic
    fun currentPetLine(): String? {
        val name = petName ?: return null
        val text = StringBuilder("§7[Lvl ").append(petLevel).append("] ").append(nameColorCode()).append(name)
        val maxLvl = if ("Golden Dragon".equals(name, ignoreCase = true)) 200 else 100
        if (petLevel >= maxLvl || petMaxed) {
            text.append(" §a§lMAXED")
        } else if (xpCurrent >= 0 && xpNext > 0) {
            text.append(String.format(" §7(%.1f%%)", xpPct))
        }
        return text.toString()
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

        val icon = if (FishSettings.petHudIcon) PetIcons.icon(petName) else null
        val text = StringBuilder()
        if (FishSettings.petHudShowLevel && petLevel >= 0) text.append("§7[Lvl ").append(petLevel).append("] ")
        if (icon == null) text.append(nameColorCode()).append(petName)

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
        if (icon != null) {
            ctx.item(icon, 0, -4)
            ctx.text(mc.font, text.toString().trimStart(), 18, 0, -1, true)
        } else {
            ctx.text(mc.font, text.toString(), 0, 0, -1, true)
        }
        ctx.pose().popMatrix()
    }

    private fun formatXp(v: Double): String {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000)
        if (v >= 1_000) return String.format("%.1fk", v / 1_000)
        return String.format("%.0f", v)
    }
}
