package fishmod.features.scoreboard

import fishmod.utils.debug.FishDiag
import fishmod.features.dungeon.PartyCommandHandler
import fishmod.utils.MayorApi
import fishmod.utils.PingTracker
import fishmod.utils.SkyblockLocation
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CustomScoreboard {

    private const val LINE_H = 9
    private const val TITLE_H = 11
    private const val CACHE_TTL_MS = 250L
    private val BLANK: Component = Component.empty()

    private val HEADER = Regex("""^(\d{2}/\d{2}/\d{2})\s*(.*)$""")
    private val LOCATION = Regex("""^\s*[⏣ф]""")
    private val PURSE = Regex("""^(Purse|Piggy):\s*(.+)$""")
    private val BITS = Regex("""^Bits:\s*([\d,.]+)""")
    private val MOTES = Regex("""^Motes:\s*([\d,.]+)""")
    private val POWDER = Regex("""^\s*(Mithril|Gemstone|Glacite)(?: Powder)?:\s*([\d,]+)""")
    private val PARTY = Regex("""^Party \((\d+)\)""")
    private val IRL = DateTimeFormatter.ofPattern("h:mm a")
    private val MINING = setOf(SkyblockLocation.DWARVEN_MINES, SkyblockLocation.CRYSTAL_HOLLOWS, SkyblockLocation.MINESHAFT)
    private val OTHER_CURRENCIES = setOf(
        ScoreboardSection.BANK, ScoreboardSection.COPPER, ScoreboardSection.SOWDUST, ScoreboardSection.HEAT,
        ScoreboardSection.COLD, ScoreboardSection.NORTH_STARS, ScoreboardSection.SOULFLOW,
    )

    private class Raw(val c: Component, val s: String)

    private var builtAt = 0L
    private var title: Component = BLANK
    private var lines: List<Component> = emptyList()
    private var footer: Component? = null
    private var width = 0

    private fun bgColor(): Int {
        val pct = max(0, min(100, FishSettings.customScoreboardOpacity))
        return (pct * 2.55).roundToInt() shl 24
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenW: Int) {
        try {
            renderInner(ctx, screenW)
        } catch (e: Exception) {
            FishDiag.fail("CustomScoreboard.1", "custom scoreboard render failed (${lines.size} cached lines)", e)
        }
    }

    private fun renderInner(ctx: GuiGraphicsExtractor, screenW: Int) {
        if (fishmod.features.FishHudEditor.isOpen()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val obj: Objective = level.scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return
        val tr = mc.font

        val now = System.currentTimeMillis()
        if (now - builtAt >= CACHE_TTL_MS) {
            builtAt = now
            build(mc, obj)
            var w = if (FishSettings.sbSectionTitle) tr.width(title) else 0
            for (l in lines) w = max(w, tr.width(l))
            footer?.let { w = max(w, tr.width(it)) }
            width = w + 6
        }
        if (lines.isEmpty() && footer == null) return

        val x2 = if (FishSettings.customScoreboardHudX < 0) screenW - 3 else min(FishSettings.customScoreboardHudX, screenW - 3)
        val x1 = x2 - width
        val y0 = FishSettings.customScoreboardHudY
        val top = if (FishSettings.sbSectionTitle) TITLE_H else 2
        val totalH = top + (lines.size + (if (footer != null) 1 else 0)) * LINE_H + 2

        ctx.fill(x1 - 2, y0, x2 + 2, y0 + totalH, bgColor())
        if (FishSettings.sbSectionTitle) ctx.centeredText(tr, title, (x1 + x2) / 2, y0 + 1, 0xFFFFFFFF.toInt())
        var y = y0 + top
        for (l in lines) {
            if (l !== BLANK) ctx.text(tr, l, x1 + 1, y, 0xFFFFFFFF.toInt(), true)
            y += LINE_H
        }
        footer?.let { ctx.centeredText(tr, it, (x1 + x2) / 2, y, 0xFFFFFFFF.toInt()) }
    }

    private fun readScoreboard(mc: Minecraft, obj: Objective): List<Raw> {
        val sb = mc.level?.scoreboard ?: return emptyList()
        return sb.listPlayerScores(obj)
            .filter { !it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy { it.owner() })
            .take(20)
            .map { e ->
                val team = sb.getPlayersTeam(e.owner())
                val c = if (team != null) PlayerTeam.formatNameForTeam(team, e.ownerName()) else e.ownerName()
                Raw(c, clean(c.string))
            }
    }

    private fun build(mc: Minecraft, obj: Objective) {
        title = obj.displayName
        val raw = readScoreboard(mc, obj)
        val used = BooleanArray(raw.size)
        fun take(pred: (Raw) -> Boolean): Raw? {
            val i = raw.indices.firstOrNull { !used[it] && pred(raw[it]) } ?: return null
            used[i] = true
            return raw[i]
        }
        for (i in raw.indices) if (raw[i].s.isEmpty()) used[i] = true

        val groups = ArrayList<List<Component>>()

        // Hypixel's last line is the server footer
        val hyFooter = raw.indices.lastOrNull { !used[it] && raw[it].s.contains("hypixel", true) }?.let { used[it] = true; raw[it].c }
        footer = if (!FishSettings.sbSectionFooter) null
            else FishSettings.sbFooterText.takeIf { it.isNotBlank() }?.let { Component.literal(it.replace('&', '§')) } ?: hyFooter

        val top = ArrayList<Component>()
        take { HEADER.containsMatchIn(it.s) }?.let { h ->
            if (!FishSettings.sbSectionHeader) return@let
            val m = HEADER.find(h.s)!!
            val irl = if (FishSettings.sbHeaderIrlTime) " §7" + LocalTime.now().format(IRL) else ""
            top.add(Component.literal("§7${m.groupValues[1]}$irl  §8${m.groupValues[2]}"))
        }
        take { ScoreboardSection.DATE.matches(it.s) }?.let { if (FishSettings.sbSectionDate) top.add(it.c) }
        take { ScoreboardSection.TIME.matches(bare(it.s)) }?.let { if (FishSettings.sbSectionTime) top.add(it.c) }
        take { LOCATION.containsMatchIn(it.s) || ScoreboardSection.classify(it.s) == ScoreboardSection.LOCATION }
            ?.let { if (FishSettings.sbSectionLocation) top.add(it.c) }
        groups.add(top)

        val money = ArrayList<Component>()
        if (FishSettings.sbSectionLevel) levelLine(mc)?.let { money.add(it) }
        take { PURSE.containsMatchIn(it.s) }?.let { if (FishSettings.sbSectionPurse) money.add(compact(it, ChatFormatting.GOLD)) }
        val bits = take { BITS.containsMatchIn(it.s) }
        val motes = take { MOTES.containsMatchIn(it.s) }
        val showBits = bits != null && FishSettings.sbSectionBits
        val showMotes = motes != null && FishSettings.sbSectionMotes
        if (showBits && showMotes) {
            money.add(Component.literal("§fBits/Motes: §b${num(BITS.find(bits!!.s)!!.groupValues[1])}§7/§d${num(MOTES.find(motes!!.s)!!.groupValues[1])}"))
        } else if (showBits) money.add(compact(bits!!, ChatFormatting.AQUA))
        else if (showMotes) money.add(compact(motes!!, ChatFormatting.LIGHT_PURPLE))
        take { ScoreboardSection.GEMS.matches(bare(it.s)) }?.let { if (FishSettings.sbSectionGems) money.add(compact(it, ChatFormatting.GREEN)) }
        while (true) {
            val r = take { ScoreboardSection.classify(it.s) in OTHER_CURRENCIES } ?: break
            if (FishSettings.sbSectionOther && sectionEnabled(ScoreboardSection.classify(r.s))) money.add(compact(r, ChatFormatting.WHITE))
        }
        groups.add(money)

        val slayerIdx = raw.indices.firstOrNull { !used[it] && raw[it].s.equals("Slayer Quest", true) }
        if (slayerIdx != null) {
            val slayer = ArrayList<Component>()
            var i = slayerIdx
            while (i < raw.size && i <= slayerIdx + 2 && raw[i].s.isNotEmpty()) { used[i] = true; slayer.add(raw[i].c); i++ }
            if (FishSettings.sbSectionSlayer) groups.add(slayer)
        }

        // Everything else Hypixel shows (objective, dungeon, diana, ...) stays in order
        val area = ArrayList<Component>()
        var last: ScoreboardSection? = null
        for (i in raw.indices) {
            if (used[i]) continue
            val r = raw[i]
            val sec = if (r.s.startsWith("- ") && last != null) last else ScoreboardSection.classify(r.s)
            last = sec
            if (sec == ScoreboardSection.POWDER || sec == ScoreboardSection.PARTY) continue
            if (sectionEnabled(sec)) area.add(compact(r, ChatFormatting.WHITE))
        }
        groups.add(area)

        if (FishSettings.sbSectionPowder && SkyblockLocation.getCurrentLocation() in MINING) groups.add(powderLines())
        if (FishSettings.sbSectionMayor) groups.add(mayorLines())
        if (FishSettings.sbSectionParty) groups.add(partyLines())
        groups.add(extraLines(mc))

        val out = ArrayList<Component>()
        for (g in groups) {
            if (g.isEmpty()) continue
            if (out.isNotEmpty()) out.add(BLANK)
            out.addAll(g)
        }
        lines = out
    }

    // Hypixel pads each line with hidden emoji to keep them unique
    private fun clean(s: String): String = s.filter {
        !Character.isSurrogate(it) && Character.getType(it).toByte() != Character.FORMAT && Character.getType(it).toByte() != Character.PRIVATE_USE
    }.trim()

    private fun bare(s: String): String = s.replace(Regex("""^[^\p{L}\p{N}]+"""), "").trim()

    private fun num(s: String): String =
        if (FishSettings.customScoreboardCompactNumbers) CompactNumbers.apply(s) else s

    private fun compact(r: Raw, color: ChatFormatting): Component {
        if (!FishSettings.customScoreboardCompactNumbers) return r.c
        val c = CompactNumbers.apply(r.s)
        return if (c == r.s) r.c else Component.literal(c).withStyle(color)
    }

    private fun levelLine(mc: Minecraft): Component? {
        val name = mc.player?.gameProfile?.name ?: return null
        val re = Regex("""^\[(\d{1,4})\S*]\s+$name\b""")
        for (e in TabListCache.entries) {
            val m = re.find(e.stripped.trim()) ?: continue
            val lvl = m.groupValues[1]
            val style = if (FishSettings.prestigeColorsEnabled)
                Style.EMPTY.withColor(TextColor.fromRgb(fishmod.cosmetic.prestige.PrestigeLevelColors.rgb(lvl.toInt())))
                else styleOf(e.info.tabListDisplayName, lvl)
            return Component.literal("Level: ").withStyle(ChatFormatting.WHITE).append(Component.literal(lvl).withStyle(style))
        }
        return null
    }

    // Style Hypixel gave the level digits in the tab entry
    private fun styleOf(c: Component?, text: String): Style {
        var found: Style = Style.EMPTY.withColor(ChatFormatting.WHITE)
        c?.visit(FormattedText.StyledContentConsumer<Unit> { st, s ->
            if (s.contains(text)) { found = st; Optional.of(Unit) } else Optional.empty()
        }, Style.EMPTY)
        return found
    }

    private fun powderLines(): List<Component> {
        val out = ArrayList<Component>()
        for (e in TabListCache.entries) {
            val m = POWDER.find(e.stripped) ?: continue
            val col = when (m.groupValues[1]) { "Mithril" -> "§2"; "Gemstone" -> "§d"; else -> "§b" }
            out.add(Component.literal(" §7- §f${m.groupValues[1]}: $col${num(m.groupValues[2])}"))
            if (out.size == 3) break
        }
        if (out.isNotEmpty()) out.add(0, Component.literal("§fPowder"))
        return out
    }

    private fun mayorLines(): List<Component> {
        val name = MayorApi.mayorName() ?: return emptyList()
        val left = MayorApi.termMsLeft() / 60_000
        val time = if (left >= 1440) "${left / 1440}d ${left % 1440 / 60}h" else "${left / 60}h ${left % 60}m"
        val out = arrayListOf<Component>(Component.literal("§eMayor $name §7($time)"))
        for (p in MayorApi.mayorPerks()) out.add(Component.literal(" §7- §f$p"))
        return out
    }

    // Tab widget "Party (N)" followed by member rows; absent when not in a party
    private fun partyLines(): List<Component> {
        val tab = TabListCache.entries
        val i = tab.indexOfFirst { PARTY.containsMatchIn(it.stripped.trim()) }
        if (i < 0) return emptyList()
        val out = arrayListOf<Component>(Component.literal("§9Party §7(${PARTY.find(tab[i].stripped.trim())!!.groupValues[1]})"))
        var j = i + 1
        while (j < tab.size && out.size < 6) {
            val s = tab[j].stripped.trim()
            if (s.isEmpty() || s.endsWith(":")) break
            out.add(Component.literal(" §7- ").append(tab[j].info.tabListDisplayName ?: Component.literal(s)))
            j++
        }
        return out
    }

    private fun extraLines(mc: Minecraft): List<Component> {
        val out = ArrayList<Component>()
        if (FishSettings.sbSectionTps) {
            val tps = PartyCommandHandler.currentTps()
            out.add(Component.literal("§7TPS: " + (if (tps < 0) "§c—" else (if (tps < 19) "§c" else "§a") + String.format("%.2f", tps))))
        }
        if (FishSettings.sbSectionPing) {
            val ping = realPing(mc)
            out.add(Component.literal("§7Ping: " + (if (ping < 0) "§c—" else "§a${ping}ms")))
        }
        if (FishSettings.sbSectionFps) out.add(Component.literal("§7FPS: §a${mc.fps}"))
        if (FishSettings.sbSectionPetExtra) fishmod.features.PetHud.currentPetLine()?.let { out.add(Component.literal(it)) }
        if (FishSettings.sbSectionSkills) for (l in SkillLevels.lines()) out.add(Component.literal(l))
        if (FishSettings.sbSectionBestiary) for (l in BestiaryProgress.lines()) out.add(Component.literal(l))
        if (FishSettings.sbSectionCollections) for (l in CollectionsProgress.lines()) out.add(Component.literal(l))
        if (FishSettings.sbSectionElection && !FishSettings.sbSectionMayor) for (l in ElectionInfo.lines()) out.add(Component.literal(l))
        if (FishSettings.sbSectionFireSales) for (l in FireSaleInfo.lines()) out.add(Component.literal(l))
        return out
    }

    private fun realPing(mc: Minecraft): Int {
        val live = PingTracker.latest()
        if (live > 0) return live
        try {
            val uuid = mc.player?.uuid ?: return -1
            val self = mc.connection?.getPlayerInfo(uuid)
            if (self != null && self.latency > 0) return self.latency
        } catch (e: Exception) {
            FishDiag.fail("CustomScoreboard.2", "failed to read own tab latency", e)
        }
        try {
            val si = mc.currentServer
            if (si != null && si.ping > 0) return si.ping.toInt()
        } catch (e: Exception) {
            FishDiag.fail("CustomScoreboard.3", "failed to read server ping", e)
        }
        return -1
    }

    private fun sectionEnabled(section: ScoreboardSection): Boolean = when (section) {
        ScoreboardSection.DATE -> FishSettings.sbSectionDate
        ScoreboardSection.TIME -> FishSettings.sbSectionTime
        ScoreboardSection.LOCATION -> FishSettings.sbSectionLocation
        ScoreboardSection.PLAYERS -> FishSettings.sbSectionPlayers
        ScoreboardSection.GAME_MODE -> FishSettings.sbSectionGameMode
        ScoreboardSection.PURSE -> FishSettings.sbSectionPurse
        ScoreboardSection.BANK -> FishSettings.sbSectionBank
        ScoreboardSection.MOTES -> FishSettings.sbSectionMotes
        ScoreboardSection.BITS -> FishSettings.sbSectionBits
        ScoreboardSection.COPPER -> FishSettings.sbSectionCopper
        ScoreboardSection.SOWDUST -> FishSettings.sbSectionSowdust
        ScoreboardSection.GEMS -> FishSettings.sbSectionGems
        ScoreboardSection.HEAT -> FishSettings.sbSectionHeat
        ScoreboardSection.COLD -> FishSettings.sbSectionCold
        ScoreboardSection.NORTH_STARS -> FishSettings.sbSectionNorthStars
        ScoreboardSection.SOULFLOW -> FishSettings.sbSectionSoulflow
        ScoreboardSection.GUILD -> FishSettings.sbSectionGuild
        ScoreboardSection.COOKIE -> FishSettings.sbSectionCookie
        ScoreboardSection.SKILL_AVERAGE -> FishSettings.sbSectionSkillAverage
        ScoreboardSection.OBJECTIVE -> FishSettings.sbSectionObjective
        ScoreboardSection.SLAYER -> FishSettings.sbSectionSlayer
        ScoreboardSection.POWDER -> FishSettings.sbSectionPowder
        ScoreboardSection.DIANA -> FishSettings.sbSectionDiana
        ScoreboardSection.PARTY -> FishSettings.sbSectionParty
        ScoreboardSection.EQUIPMENT -> FishSettings.sbSectionEquipment
        ScoreboardSection.DUNGEON -> FishSettings.sbSectionDungeon
        ScoreboardSection.PET -> FishSettings.sbSectionPet
        ScoreboardSection.OTHER -> FishSettings.sbSectionOther
    }
}
