package fishmod.features.scoreboard

import fishmod.utils.debug.FishDiag
import fishmod.utils.MayorApi
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

        val top = if (FishSettings.sbSectionTitle) TITLE_H else 2
        val totalH = top + (lines.size + (if (footer != null) 1 else 0)) * LINE_H + 2
        val screenH = mc.window.guiScaledHeight
        val anchor = FishSettings.customScoreboardAnchor
        val x2 = when {
            anchor.endsWith("Left") -> width + 3
            anchor.endsWith("Right") -> screenW - 3
            FishSettings.customScoreboardHudX < 0 -> screenW - 3
            else -> min(FishSettings.customScoreboardHudX, screenW - 3)
        }
        val x1 = x2 - width
        val y0 = when {
            anchor.startsWith("Top") -> 3
            anchor.startsWith("Middle") -> (screenH - totalH) / 2
            anchor.startsWith("Bottom") -> screenH - totalH - 3
            else -> FishSettings.customScoreboardHudY
        }

        fishmod.features.ScreenTheme.roundedRect(ctx, x1 - 2, y0, width + 4, totalH, FishSettings.customScoreboardRadius, bgColor())
        if (FishSettings.sbSectionTitle) ctx.centeredText(tr, title, (x1 + x2) / 2, y0 + 1, 0xFFFFFFFF.toInt())
        var y = y0 + top
        for (l in lines) {
            if (l !== BLANK) ctx.text(tr, l, x1 + 1, y, 0xFFFFFFFF.toInt(), true)
            y += LINE_H
        }
        footer?.let { ctx.text(tr, it, x1 + 1, y, 0xFFFFFFFF.toInt(), true) }
    }

    private fun readScoreboard(mc: Minecraft, obj: Objective): List<Raw> {
        val sb = mc.level?.scoreboard ?: return emptyList()
        return sb.listPlayerScores(obj)
            .filter { !it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy { it.owner() })
            .take(20)
            .map { e ->
                val team = sb.getPlayersTeam(e.owner())
                // Owner is Hypixel's hidden filler; the text is the team prefix + suffix
                val c = if (team != null) Component.empty().append(team.playerPrefix).append(team.playerSuffix) else e.ownerName()
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
            val m = HEADER.find(h.s)!!
            if (FishSettings.sbSectionHeader) {
                val irl = if (FishSettings.sbHeaderIrlTime) " " + LocalTime.now().format(IRL) else ""
                top.add(Component.literal("§7${m.groupValues[1]}$irl"))
            }
            if (FishSettings.sbSectionServer && m.groupValues[2].isNotBlank()) top.add(Component.literal("§8${m.groupValues[2]}"))
        }
        groups.add(top)

        val world = ArrayList<Component>()
        take { ScoreboardSection.DATE.matches(bare(it.s)) }?.let { if (FishSettings.sbSectionDate) world.add(Component.literal("§f" + bare(it.s))) }
        val timeIdx = raw.indices.firstOrNull { !used[it] && ScoreboardSection.TIME.matches(bare(raw[it].s)) }
        if (timeIdx != null) {
            used[timeIdx] = true
            if (FishSettings.sbSectionTime) world.add(Component.literal("§7" + bare(raw[timeIdx].s)))
        }
        // Location follows the time; its icon is a custom font glyph, so keep Hypixel's component
        val locIdx = timeIdx?.plus(1)?.takeIf { it < raw.size && !used[it] }
            ?: raw.indices.firstOrNull { !used[it] && (LOCATION.containsMatchIn(raw[it].s) || ScoreboardSection.classify(raw[it].s) == ScoreboardSection.LOCATION) }
        if (locIdx != null) {
            used[locIdx] = true
            if (FishSettings.sbSectionLocation) world.add(raw[locIdx].c)
        }
        groups.add(world)

        val money = ArrayList<Component>()
        if (FishSettings.sbSectionLevel) levelLine(mc)?.let { money.add(it) }
        take { PURSE.containsMatchIn(it.s) }?.let { if (FishSettings.sbSectionPurse) money.add(kv(it.s, "§6")) }
        val bits = take { BITS.containsMatchIn(it.s) }
        val motes = take { MOTES.containsMatchIn(it.s) }
        val showBits = bits != null && FishSettings.sbSectionBits
        val showMotes = motes != null && FishSettings.sbSectionMotes
        if (showBits && showMotes) {
            money.add(Component.literal("§fBits/Motes: §b${num(BITS.find(bits!!.s)!!.groupValues[1])}§7/§5${num(MOTES.find(motes!!.s)!!.groupValues[1])}"))
        } else if (showBits) money.add(kv(bits!!.s, "§b"))
        else if (showMotes) money.add(kv(motes!!.s, "§5"))
        val gems = take { ScoreboardSection.GEMS.matches(bare(it.s)) }?.let { bare(it.s) } ?: tabValue(TAB_GEMS)?.let { "Gems: $it" }
        if (gems != null && FishSettings.sbSectionGems) money.add(kv(gems, "§a"))
        var soulflowShown = false
        while (true) {
            val r = take { ScoreboardSection.classify(it.s) in OTHER_CURRENCIES } ?: break
            if (ScoreboardSection.classify(r.s) == ScoreboardSection.SOULFLOW) soulflowShown = true
            if (FishSettings.sbSectionOther) money.add(compact(r, ChatFormatting.WHITE))
        }
        if (!soulflowShown && FishSettings.sbSectionOther) tabValue(TAB_SOULFLOW)?.let { money.add(kv("Soulflow: $it", "§3")) }
        groups.add(money)

        val slayerIdx = raw.indices.firstOrNull { !used[it] && raw[it].s.equals("Slayer Quest", true) }
        if (slayerIdx != null) {
            val slayer = ArrayList<Component>()
            var i = slayerIdx
            while (i < raw.size && i <= slayerIdx + 2 && raw[i].s.isNotEmpty()) {
                used[i] = true
                slayer.add(if (i == slayerIdx) Component.literal("§fSlayer Quest") else compact(raw[i], ChatFormatting.GRAY))
                i++
            }
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
            if (FishSettings.sbSectionOther) area.add(compact(r, ChatFormatting.WHITE))
        }
        groups.add(area)

        if (FishSettings.sbSectionPowder && SkyblockLocation.getCurrentLocation() in MINING) groups.add(powderLines())
        if (FishSettings.sbSectionMayor) groups.add(mayorLines())
        if (FishSettings.sbSectionParty) groups.add(partyLines())

        val out = ArrayList<Component>()
        for (g in groups) {
            if (g.isEmpty()) continue
            if (out.isNotEmpty()) out.add(BLANK)
            out.addAll(g)
        }
        lines = out
    }

    // Hypixel pads each line with hidden emoji to keep them unique
    private val LEGACY_CODE = Regex("§.")
    private val TAB_GEMS = Regex("""^Gems:\s*([\d,.]+[kKmMbB]?)""")
    private val TAB_SOULFLOW = Regex("""^Soulflow:\s*([\d,.]+[kKmMbB]?)""")

    // Value from a tab widget row like " Gems: 57,873"
    private fun tabValue(re: Regex): String? {
        for (e in TabListCache.entries) re.find(clean(e.stripped))?.let { return it.groupValues[1] }
        return null
    }

    private fun clean(s: String): String = s.replace(LEGACY_CODE, "").filter {
        !Character.isSurrogate(it) && Character.getType(it).toByte() != Character.FORMAT && Character.getType(it).toByte() != Character.PRIVATE_USE
    }.trim()

    // White label, coloured value: "Purse: 1,234" -> §fPurse: §6...
    private fun kv(s: String, col: String): Component {
        val i = s.indexOf(':')
        if (i < 0) return Component.literal("§f$s")
        return Component.literal("§f${s.substring(0, i)}: $col${num(s.substring(i + 1).trim())}")
    }

    private fun bare(s: String): String = s.replace(Regex("""^[^\p{L}\p{N}]+"""), "").trim()

    private fun num(s: String): String = s

    @Suppress("UNUSED_PARAMETER")
    private fun compact(r: Raw, color: ChatFormatting): Component = r.c

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
        val out = arrayListOf<Component>(Component.literal("§fMayor $name §7($time)"))
        for (p in MayorApi.mayorPerks()) out.add(Component.literal(" §7- §f$p"))
        return out
    }

    // Tab widget "Party (N)" followed by member rows; absent when not in a party
    private fun partyLines(): List<Component> {
        val tab = TabListCache.entries
        val i = tab.indexOfFirst { PARTY.containsMatchIn(it.stripped.trim()) }
        if (i < 0) return emptyList()
        val out = arrayListOf<Component>(Component.literal("§fParty §7(${PARTY.find(tab[i].stripped.trim())!!.groupValues[1]})"))
        var j = i + 1
        while (j < tab.size && out.size < 6) {
            val s = tab[j].stripped.trim()
            if (s.isEmpty() || s.endsWith(":")) break
            out.add(Component.literal(" §7- ").append(tab[j].info.tabListDisplayName ?: Component.literal(s)))
            j++
        }
        return out
    }
}
