package fishmod.features.scoreboard

import fishmod.utils.TextStrip

import fishmod.utils.debug.FishDiag
import fishmod.utils.MayorApi
import fishmod.utils.SidebarCache
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
    private val GUESTS = Regex("""\(\d+/\d+\)\s*$""")
    private val PARTY = Regex("""^Party \((\d+)\)""")
    private val IRL = DateTimeFormatter.ofPattern("h:mm a")
    private val MINING = setOf(SkyblockLocation.DWARVEN_MINES, SkyblockLocation.CRYSTAL_HOLLOWS, SkyblockLocation.MINESHAFT)
    private val OTHER_CURRENCIES = setOf(
        ScoreboardSection.BANK, ScoreboardSection.COPPER, ScoreboardSection.SOWDUST, ScoreboardSection.HEAT,
        ScoreboardSection.COLD, ScoreboardSection.NORTH_STARS, ScoreboardSection.SOULFLOW,
    )

    private class Raw(val c: Component, val s: String)

    private class Built(val title: Component, val lines: List<Component>, val footer: Component?, val width: Int) {
        val top: Int get() = if (FishSettings.sbSectionTitle) TITLE_H else 2
        val totalH: Int get() = top + (lines.size + (if (footer != null) (if (lines.isEmpty()) 1 else 2) else 0)) * LINE_H + 2
    }

    private var builtAt = 0L
    private var builtVersion = -1
    private var cur = Built(BLANK, emptyList(), null, 0)

    private fun bgColor(): Int {
        val pct = max(0, min(100, FishSettings.customScoreboardOpacity))
        return (pct * 2.55).roundToInt() shl 24
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenW: Int) {
        try {
            renderInner(ctx, screenW)
        } catch (e: Exception) {
            FishDiag.fail("CustomScoreboard.1", "custom scoreboard render failed (${cur.lines.size} cached lines)", e)
        }
    }

    private fun measure(tr: net.minecraft.client.gui.Font, b: Built): Int {
        var w = if (FishSettings.sbSectionTitle) tr.width(b.title) else 0
        for (l in b.lines) w = max(w, tr.width(l))
        b.footer?.let { w = max(w, tr.width(it)) }
        return w + 3
    }

    // Box drawing shared by the in-game sidebar and the HUD editor example; x1 = left of the text column
    private fun drawBoard(ctx: GuiGraphicsExtractor, tr: net.minecraft.client.gui.Font, b: Built, x1: Int, y0: Int, gs: Int) {
        val x2 = x1 + b.width
        val totalH = b.totalH
        ctx.pose().pushMatrix()
        ctx.pose().scale(1f / gs, 1f / gs)
        fishmod.features.ScreenTheme.roundedRect(ctx, (x1 - 2) * gs, y0 * gs, (b.width + 4) * gs, totalH * gs, FishSettings.customScoreboardRadius * gs, bgColor())
        ctx.pose().popMatrix()
        if (FishSettings.sbSectionTitle) ctx.centeredText(tr, b.title, (x1 + x2) / 2, y0 + 1, 0xFFFFFFFF.toInt())
        var y = y0 + b.top
        for (l in b.lines) {
            if (l !== BLANK) ctx.text(tr, l, x1 + 1, y, 0xFFFFFFFF.toInt(), true)
            y += LINE_H
        }
        b.footer?.let { ctx.text(tr, it, x1 + 1, if (b.lines.isEmpty()) y else y + LINE_H, 0xFFFFFFFF.toInt(), true) }
    }

    private fun exampleRaw(): List<Raw> = listOf(
        "§711/09/26  §8m12A" to "11/09/26  m12A",
        "§fWinter 9th" to "Winter 9th",
        "§712:00am" to "12:00am",
        "§7⏣ §aYour Island" to "⏣ Your Island",
        "§fPurse: §61,234,567" to "Purse: 1,234,567",
        "§fBits: §b1,024" to "Bits: 1,024",
        "§fGems: §a120" to "Gems: 120",
        "Bank: §650M" to "Bank: 50M",
        "" to "",
        "Slayer Quest" to "Slayer Quest",
        "§4Revenant Horror IV" to "Revenant Horror IV",
        "§7(§e1,234§7/§c2,400§7) Combat XP" to "(1,234/2,400) Combat XP",
        "" to "",
        "Objective" to "Objective",
        "§eTalk to Elizabeth" to "Talk to Elizabeth",
        "§ewww.hypixel.net" to "www.hypixel.net",
    ).map { Raw(Component.literal(it.first), it.second) }

    // Example tab/API data for the sections that don't come from the sidebar
    private fun exLevel(): Component {
        val style = if (FishSettings.prestigeColorsEnabled)
            Style.EMPTY.withColor(TextColor.fromRgb(fishmod.cosmetic.prestige.PrestigeLevelColors.rgb(287)))
            else Style.EMPTY.withColor(ChatFormatting.AQUA)
        return Component.literal("Level: ").withStyle(ChatFormatting.WHITE).append(Component.literal("287").withStyle(style))
    }
    private fun exPowder(): List<Component> = listOf(
        Component.literal("§fPowder"), Component.literal(" §7- §fMithril: §21,234,567"),
        Component.literal(" §7- §fGemstone: §d2,345,678"), Component.literal(" §7- §fGlacite: §b345,678"),
    )
    private fun exMayor(): List<Component> = listOf(
        Component.literal("§6Diana §7(3d 4h)"), Component.literal(" §8- §eMythological Ritual"), Component.literal(" §8- §ePet XP Buff"),
    )
    private fun exParty(): List<Component> = listOf(
        Component.literal("§9Party §7(3)"), Component.literal(" §7- §bPlayerOne"), Component.literal(" §7- §aPlayerTwo"),
    )

    private var pvAt = 0L
    private var pv: Built? = null

    // Example data fed through the real build(): header, world and money lines in Hypixel's own formats
    private fun preview(): Built {
        val now = System.currentTimeMillis()
        pv?.let { if (now - pvAt < 200L) return it }
        val mc = Minecraft.getInstance()
        val b0 = build(mc, Component.literal("§e§lSKYBLOCK"), exampleRaw(), ex = true)
        val b = Built(b0.title, b0.lines, b0.footer, measure(mc.font, b0))
        pv = b
        pvAt = now
        return b
    }

    @JvmStatic
    fun previewBoxW(): Int = preview().width + 4

    @JvmStatic
    fun previewBoxX(): Int {
        val sw = Minecraft.getInstance().window.guiScaledWidth
        val a = FishSettings.customScoreboardAnchor
        val right = when {
            a.endsWith("Left") -> preview().width + 3
            a.endsWith("Right") || FishSettings.customScoreboardHudX < 0 -> sw - 3
            else -> min(FishSettings.customScoreboardHudX, sw - 3)
        }
        return right + 2 - previewBoxW()
    }

    @JvmStatic
    fun previewBoxY(): Int {
        val sh = Minecraft.getInstance().window.guiScaledHeight
        val a = FishSettings.customScoreboardAnchor
        return when {
            a.startsWith("Top") -> 3
            a.startsWith("Middle") -> (sh - preview().totalH) / 2
            a.startsWith("Bottom") -> sh - preview().totalH - 3
            else -> FishSettings.customScoreboardHudY
        }
    }

    @JvmStatic
    fun setPreviewBoxX(v: Int) {
        if (v == previewBoxX()) return
        if (FishSettings.customScoreboardAnchor != "Custom") { FishSettings.customScoreboardHudY = previewBoxY(); FishSettings.customScoreboardAnchor = "Custom" }
        val sw = Minecraft.getInstance().window.guiScaledWidth
        val right = v + previewBoxW() - 2
        FishSettings.customScoreboardHudX = if (right >= sw - 5) -1 else right
    }

    @JvmStatic
    fun setPreviewBoxY(v: Int) {
        if (v == previewBoxY()) return
        if (FishSettings.customScoreboardAnchor != "Custom") { setAnchorCustomKeepX() }
        FishSettings.customScoreboardHudY = v
    }

    private fun setAnchorCustomKeepX() {
        val sw = Minecraft.getInstance().window.guiScaledWidth
        val right = previewBoxX() + previewBoxW() - 2
        FishSettings.customScoreboardAnchor = "Custom"
        FishSettings.customScoreboardHudX = if (right >= sw - 5) -1 else right
    }

    fun registerPreview() {
        fishmod.features.HudPreview.register("Custom Scoreboard") {
            val mc = Minecraft.getInstance()
            val b = preview()
            if (b.lines.isEmpty() && b.footer == null) return@register null
            val gs = mc.window.guiScale.toInt().coerceAtLeast(1)
            fishmod.features.HudExample(b.width + 4, b.totalH) { drawBoard(it, mc.font, b, 2, 0, gs) }
        }
    }

    private fun renderInner(ctx: GuiGraphicsExtractor, screenW: Int) {
        if (fishmod.features.FishHudEditor.isOpen()) return
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val sbTitle = SidebarCache.title ?: return
        val tr = mc.font

        val now = System.currentTimeMillis()
        if (now - builtAt >= CACHE_TTL_MS || builtVersion != SidebarCache.version) {
            builtAt = now
            builtVersion = SidebarCache.version
            val b0 = build(mc, sbTitle, readScoreboard())
            cur = Built(b0.title, b0.lines, b0.footer, measure(tr, b0))
        }
        val b = cur
        val width = b.width
        if (b.lines.isEmpty() && b.footer == null) return

        val totalH = b.totalH
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

        val gs = mc.window.guiScale.toInt().coerceAtLeast(1)
        drawBoard(ctx, tr, b, x1, y0, gs)
    }

    private fun readScoreboard(): List<Raw> {
        return SidebarCache.entries
            .filter { !it.hidden }
            .sortedWith(compareByDescending<SidebarCache.Entry> { it.score }.thenBy { it.owner })
            .take(20)
            .map { e ->
                val team = e.team
                // Owner is Hypixel's hidden filler; the text is the team prefix + suffix
                val c = if (team != null) Component.empty().append(team.playerPrefix).append(team.playerSuffix) else (e.display ?: Component.literal(e.owner))
                Raw(trimEnd(c), clean(c.string))
            }
    }

    private fun trimEnd(c: Component): Component {
        val segs = ArrayList<Pair<Style, String>>()
        c.visit(FormattedText.StyledContentConsumer<Unit> { st, t -> segs.add(st to t); Optional.empty() }, Style.EMPTY)
        while (segs.isNotEmpty()) {
            val (st, t) = segs.last()
            val tt = t.trimEnd { it.isWhitespace() || Character.getType(it).toByte() == Character.FORMAT }
            if (tt.isEmpty()) segs.removeAt(segs.size - 1) else { segs[segs.size - 1] = st to tt; break }
        }
        val out = Component.empty()
        for ((st, t) in segs) out.append(Component.literal(t).setStyle(st))
        return out
    }

    // ex = HUD editor example: tab/API-backed sections use example data instead of live state
    private fun build(mc: Minecraft, sbTitle: Component, raw: List<Raw>, ex: Boolean = false): Built {
        val used = BooleanArray(raw.size)
        fun take(pred: (Raw) -> Boolean): Raw? {
            val i = raw.indices.firstOrNull { !used[it] && pred(raw[it]) } ?: return null
            used[i] = true
            return raw[i]
        }
        for (i in raw.indices) if (raw[i].s.isEmpty()) used[i] = true

        val groups = ArrayList<List<Component>>()

        val hyFooter = raw.indices.lastOrNull { !used[it] && raw[it].s.contains("hypixel", true) }?.let { used[it] = true; raw[it].c }
        val footer: Component? = if (!FishSettings.sbSectionFooter) null
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
        val locIdx = timeIdx?.plus(1)?.takeIf { it < raw.size && !used[it] }
            ?: raw.indices.firstOrNull { !used[it] && (LOCATION.containsMatchIn(raw[it].s) || ScoreboardSection.classify(raw[it].s) == ScoreboardSection.LOCATION) }
        if (locIdx != null) {
            used[locIdx] = true
            if (FishSettings.sbSectionLocation) world.add(raw[locIdx].c)
            val guestIdx = locIdx + 1
            if (guestIdx < raw.size && !used[guestIdx] && GUESTS.containsMatchIn(raw[guestIdx].s)) {
                used[guestIdx] = true
                if (FishSettings.sbSectionLocation) world.add(raw[guestIdx].c)
            }
        }
        groups.add(world)

        val money = ArrayList<Component>()
        if (FishSettings.sbSectionLevel) (if (ex) exLevel() else levelLine(mc))?.let { money.add(it) }
        take { PURSE.containsMatchIn(it.s) }?.let { if (FishSettings.sbSectionPurse) money.add(kv(it.s, "§6")) }
        val bits = take { BITS.containsMatchIn(it.s) }
        val motes = take { MOTES.containsMatchIn(it.s) }
        val showBits = bits != null && FishSettings.sbSectionBits
        val showMotes = motes != null && FishSettings.sbSectionMotes
        if (showBits && showMotes) {
            money.add(Component.literal("§fBits/Motes: §b${num(BITS.find(bits!!.s)!!.groupValues[1])}§7/§5${num(MOTES.find(motes!!.s)!!.groupValues[1])}"))
        } else if (showBits) money.add(kv(bits!!.s, "§b"))
        else if (showMotes) money.add(kv(motes!!.s, "§5"))
        val gems = take { ScoreboardSection.GEMS.matches(bare(it.s)) }?.let { bare(it.s) } ?: (if (ex) null else tabValue(TAB_GEMS))?.let { "Gems: $it" }
        if (gems != null && FishSettings.sbSectionGems) money.add(kv(gems, "§a"))
        var soulflowShown = false
        while (true) {
            val r = take { ScoreboardSection.classify(it.s) in OTHER_CURRENCIES } ?: break
            if (ScoreboardSection.classify(r.s) == ScoreboardSection.SOULFLOW) soulflowShown = true
            if (FishSettings.sbSectionOther) money.add(compact(r, ChatFormatting.WHITE))
        }
        if (!soulflowShown && FishSettings.sbSectionOther) (if (ex) "12,345" else tabValue(TAB_SOULFLOW))?.let { money.add(kv("Soulflow: $it", "§3")) }
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

        if (FishSettings.sbSectionPowder && (ex || SkyblockLocation.getCurrentLocation() in MINING)) groups.add(if (ex) exPowder() else powderLines())
        if (FishSettings.sbSectionMayor) groups.add(if (ex) exMayor() else mayorLines())
        if (FishSettings.sbSectionParty) groups.add(if (ex) exParty() else partyLines())

        val out = ArrayList<Component>()
        for (g in groups) {
            if (g.isEmpty()) continue
            if (out.isNotEmpty()) out.add(BLANK)
            out.addAll(g)
        }
        return Built(sbTitle, out, footer, 0)
    }

    private val TAB_GEMS = Regex("""^Gems:\s*([\d,.]+[kKmMbB]?)""")
    private val TAB_SOULFLOW = Regex("""^Soulflow:\s*([\d,.]+[kKmMbB]?)""")

    private fun tabValue(re: Regex): String? {
        for (e in TabListCache.entries) re.find(clean(e.stripped))?.let { return it.groupValues[1] }
        return null
    }

    private fun clean(s: String): String = TextStrip.plain(s).filter {
        !Character.isSurrogate(it) && Character.getType(it).toByte() != Character.FORMAT && Character.getType(it).toByte() != Character.PRIVATE_USE
    }.trim()

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
        val out = arrayListOf<Component>(Component.literal("§6$name §7($time)"))
        for (p in MayorApi.mayorPerks()) out.add(Component.literal(" §8- §e$p"))
        return out
    }

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
}
