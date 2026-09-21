package fishmod.features

import fishmod.features.dungeon.PartyCommandHandler
import fishmod.utils.PingTracker
import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CompactTab {

    private const val BG_RGB = 0x0B0D13

    private fun bgPanel(): Int {
        val pct = max(0, min(100, FishSettings.compactTabOpacity))
        val a = (pct * 2.55).roundToInt()
        return (a shl 24) or BG_RGB
    }

    private fun bgHead(): Int {
        val pct = max(0, min(100, FishSettings.compactTabOpacity))
        val a = (pct * 2.55 * 0.45).roundToInt()
        return (a shl 24) or BG_RGB
    }

    private const val BORDER = 0x66303440
    private const val DIVIDER = 0x44454a58
    private const val LABEL = 0xFF8A8F9C.toInt()
    private const val VALUE = 0xFF55FF55.toInt()
    private const val GOLD = 0xFFFFD700.toInt()
    private const val NAME = 0xFFE8ECF2.toInt()
    private const val BAR_ON = 0xFF55E05A.toInt()
    private const val BAR_OFF = 0x55202530

    private val COL_KEY: Pattern = Pattern.compile("^!([A-Za-z])")
    private val SERVER_ID: Pattern = Pattern.compile("\\b((?:mini|mega|m)\\d+[A-Za-z]{1,3})\\b")

    // --- Tab Sort ---------------------------------------------------------
    // Purely text-based (no Hypixel API calls): everything is read off the
    // already-rendered tab-list display name string.

    private val SORT_LEVEL_TAG = Regex("""\[(\d{1,4})[^\[\]\d]{0,4}]""")
    private val SORT_RANK_TAG = Regex("""\[([A-Za-z+]{2,10})]""")

    // Highest to lowest. GM and MOD share a tier.
    private val RANK_TIER_GROUPS: List<Set<String>> = listOf(
        setOf("OWNER"),
        setOf("ADMIN"),
        setOf("GM", "MOD"),
        setOf("YOUTUBE"),
        setOf("MVP++"),
        setOf("MVP+"),
        setOf("MVP"),
        setOf("VIP+"),
        setOf("VIP"),
    )

    // Best-effort glyph set for Ironman/Bingo tags Hypixel appends near the
    // player's name. Codepoints unverified without live game data — tune
    // this constant after testing in-game; an empty match just means every
    // player falls through to "unflagged", so it never breaks the sort.
    private val IRONMAN_BINGO_MARKERS = Regex("[☘♻☢⚘🌱]") // ☘ ♻ ☢ ⚚ 🌱

    // Hypixel's usual tab-list relationship colors: gold = party, aqua = friend, dark green = guild.
    private val SOCIAL_COLOR_CODE = Regex("§([0-9a-fk-or])", RegexOption.IGNORE_CASE)
    private val BRACKET_TAG = Regex("""\[[^\[\]]*]""")

    private fun sortLevelOf(stripped: String): Int =
        SORT_LEVEL_TAG.find(stripped)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun sortRankTierOf(stripped: String): Int {
        for (m in SORT_RANK_TAG.findAll(stripped)) {
            val tag = m.groupValues[1].uppercase()
            val idx = RANK_TIER_GROUPS.indexOfFirst { tag in it }
            if (idx >= 0) return idx
        }
        return RANK_TIER_GROUPS.size
    }

    private fun sortIsIronmanBingo(raw: String): Boolean = IRONMAN_BINGO_MARKERS.containsMatchIn(raw)

    private fun sortSocialTierOf(raw: String): Int {
        // Strip bracket contents (rank/level tags) but keep the color codes
        // around them, so the last remaining color code is the one that was
        // applied to the actual username text.
        val withoutTags = BRACKET_TAG.replace(raw, "")
        val code = SOCIAL_COLOR_CODE.findAll(withoutTags).lastOrNull()?.groupValues?.get(1)?.lowercase()
        return when (code) {
            "6" -> 0 // party - gold
            "b" -> 1 // friends - aqua
            "2" -> 2 // guild - dark green
            else -> 3 // everyone else
        }
    }

    // Applies only to the real player-list column (col 0); every other
    // column is a fixed informational panel and must keep its server-given
    // row order regardless of the chosen sort mode. Row 0 of the players
    // column is a fixed header (e.g. "Players (19)"), not a sortable
    // player — it stays pinned in place and drawColumns never gives it a
    // face/ping bar.
    private fun sortPlayersColumn(entries: List<PlayerInfo>): List<PlayerInfo> {
        if (entries.size <= 1) return entries
        val header = entries[0]
        val rest = entries.subList(1, entries.size)
        val mode = FishSettings.compactTabSortMode
        val sortedRest = if (mode == "Random") rest.shuffled() else {
            fun stripped(e: PlayerInfo) = BLANK_COLOR.matcher(e.tabListDisplayName?.string ?: "").replaceAll("")
            fun raw(e: PlayerInfo) = e.tabListDisplayName?.string ?: ""
            val cmp: Comparator<PlayerInfo> = when (mode) {
                "SB Level" -> compareByDescending { sortLevelOf(stripped(it)) }
                "Name (Abc)" -> compareBy<PlayerInfo> { nameOf(it).lowercase() }
                    .thenByDescending { sortLevelOf(stripped(it)) }
                "Ironman/Bingo" -> compareBy<PlayerInfo> { if (sortIsIronmanBingo(raw(it))) 0 else 1 }
                    .thenByDescending { sortLevelOf(stripped(it)) }
                "Party/Friends/Guild" -> compareBy<PlayerInfo> { sortSocialTierOf(raw(it)) }
                    .thenByDescending { sortLevelOf(stripped(it)) }
                else -> compareBy<PlayerInfo> { sortRankTierOf(stripped(it)) } // "Rank (Default)" and unknown values
                    .thenByDescending { sortLevelOf(stripped(it)) }
            }
            rest.sortedWith(cmp)
        }
        val out = ArrayList<PlayerInfo>(entries.size)
        out.add(header)
        out.addAll(sortedRest)
        return out
    }

    private var shouldRenderVersion = -1
    private var shouldRenderCached = false

    @JvmStatic
    fun shouldRender(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.connection == null) return false
        val v = TabListCache.version
        if (v == shouldRenderVersion) return shouldRenderCached
        shouldRenderVersion = v
        shouldRenderCached = TabListCache.entries.any { COL_KEY.matcher(nameOf(it.info)).find() }
        return shouldRenderCached
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenW: Int, tabHeader: String?, tabFooter: String?) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.connection == null) return
        val tr = mc.font
        val lh = 10

        val mdl = model(mc, tabHeader, tabFooter) ?: return
        val columns = mdl.columns
        val colWidths = mdl.colWidths
        val rows = mdl.rows

        val ping = realPing(mc)
        val fps = mc.fps
        val tps = PartyCommandHandler.currentTps()
        val server = mdl.server

        val labels = arrayOf("SERVER", "TPS", "FPS", "PING")
        val values = arrayOf(
            server,
            if (tps < 0) "—" else String.format("%.2f", tps),
            fps.toString(),
            if (ping < 0) "—" else "${ping}ms"
        )
        fun valueColor(i: Int) = if (i == 1 && tps >= 0 && tps < 19) 0xFFFF5555.toInt() else VALUE

        val pad = 8
        val gap = 8
        var contentW = 0
        for (w in colWidths) contentW += w
        contentW += gap * (columns.size - 1)
        val bodyH = rows * lh + 6
        val footH = 12
        val topPad = 8
        val tabW = contentW + pad * 2
        val tabH = topPad + bodyH + footH
        val boxGap = 8

        if (!FishSettings.compactTabStatBarEnabled) {
            val w = min(screenW - 12, tabW)
            val x0 = (screenW - w) / 2
            val y0 = 4
            roundRect(ctx, x0, y0, x0 + w, y0 + tabH, bgPanel())
            drawColumns(ctx, tr, columns, colWidths, x0 + pad, y0 + topPad, rows, gap, lh)
            ctx.centeredText(tr, mdl.footer, x0 + w / 2, y0 + tabH - footH + 2, GOLD)
            return
        }

        val pos = FishSettings.compactTabStatBarPosition.uppercase()
        if (pos == "LEFT" || pos == "RIGHT") {
            val statLineH = 14
            var statW = 0
            for (i in labels.indices) statW = max(statW, tr.width("§7" + labels[i] + " " + values[i]))
            statW += 16
            val statH = tabH

            val totalW = tabW + boxGap + statW
            val w = min(screenW - 12, totalW)
            val x0 = (screenW - w) / 2
            val y0 = 4
            val tabX0 = if (pos == "LEFT") x0 + statW + boxGap else x0
            val statX0 = if (pos == "LEFT") x0 else x0 + tabW + boxGap

            roundRect(ctx, tabX0, y0, tabX0 + tabW, y0 + tabH, bgPanel())
            drawColumns(ctx, tr, columns, colWidths, tabX0 + pad, y0 + topPad, rows, gap, lh)
            ctx.centeredText(tr, mdl.footer, tabX0 + tabW / 2, y0 + tabH - footH + 2, GOLD)

            roundRect(ctx, statX0, y0, statX0 + statW, y0 + statH, bgPanel())
            val cellH = statH / labels.size
            for (i in labels.indices) {
                val cellTop = y0 + i * cellH
                val sy = cellTop + (cellH - statLineH) / 2 + 3
                if (i > 0) ctx.fill(statX0 + 6, cellTop, statX0 + statW - 6, cellTop + 1, DIVIDER)
                ctx.text(tr, "§7" + labels[i] + " ", statX0 + 8, sy, LABEL, false)
                val lw = tr.width("§7" + labels[i] + " ")
                ctx.text(tr, values[i], statX0 + 8 + lw, sy, valueColor(i), false)
            }
        } else {
            val statBarH = 32
            val gapTB = 4
            val w = min(screenW - 12, tabW)
            val x0 = (screenW - w) / 2
            val y0 = 4
            val bottom = pos == "BOTTOM"
            val tabY0 = if (bottom) y0 else y0 + statBarH + gapTB
            val statY0 = if (bottom) tabY0 + tabH + gapTB else y0

            roundRect(ctx, x0, tabY0, x0 + w, tabY0 + tabH, bgPanel())
            drawColumns(ctx, tr, columns, colWidths, x0 + pad, tabY0 + topPad, rows, gap, lh)
            ctx.centeredText(tr, mdl.footer, x0 + w / 2, tabY0 + tabH - footH + 2, GOLD)

            roundRect(ctx, x0, statY0, x0 + w, statY0 + statBarH, bgPanel())
            val cellW = w / labels.size
            for (i in labels.indices) {
                val cxL = x0 + i * cellW
                if (i > 0) ctx.fill(cxL, statY0 + 6, cxL + 1, statY0 + statBarH - 6, DIVIDER)
                val cxC = cxL + cellW / 2
                ctx.centeredText(tr, "§7" + labels[i], cxC, statY0 + 7, LABEL)
                ctx.centeredText(tr, values[i], cxC, statY0 + 18, valueColor(i))
            }
        }
    }

    private class Model(
        val columns: List<List<PlayerInfo>>,
        val colWidths: List<Int>,
        val rows: Int,
        val server: String,
        val footer: String,
    )

    private const val MODEL_TTL_MS = 1000L
    private var modelVersion = -1
    private var modelHeaderFooterSig = ""
    private var modelAt = 0L
    private var cachedModel: Model? = null

    private fun model(mc: Minecraft, tabHeader: String?, tabFooter: String?): Model? {
        val now = System.currentTimeMillis()
        val v = TabListCache.version
        val hf = (tabHeader ?: "") + " " + (tabFooter ?: "")
        val cached = cachedModel
        if (cached != null && v == modelVersion && hf == modelHeaderFooterSig && now - modelAt < MODEL_TTL_MS) return cached
        modelVersion = v
        modelHeaderFooterSig = hf
        modelAt = now
        cachedModel = buildModel(mc, tabHeader, tabFooter)
        return cachedModel
    }

    private fun buildModel(mc: Minecraft, tabHeader: String?, tabFooter: String?): Model? {
        val tr = mc.font
        val all = ArrayList<PlayerInfo>(TabListCache.entries.size)
        for (e in TabListCache.entries) all.add(e.info)
        all.sortWith(Comparator { a, b -> nameOf(a).compareTo(nameOf(b), ignoreCase = true) })
        val grouped = LinkedHashMap<String, MutableList<PlayerInfo>>()
        for (e in all) {
            val m = COL_KEY.matcher(nameOf(e))
            if (m.find()) grouped.getOrPut(m.group(1).uppercase()) { ArrayList() }.add(e)
        }
        if (grouped.isEmpty()) return null

        val columns = ArrayList<List<PlayerInfo>>()
        val colWidths = ArrayList<Int>()
        var rows = 0
        var first = true
        for (col in grouped.values) {
            var last = -1
            var nonBlank = 0
            for (i in col.indices) if (!blank(col[i])) {
                last = i
                nonBlank++
            }
            if (last < 0 || nonBlank <= 1) {
                first = false
                continue
            }
            val playersCol = first
            first = false
            val trimmed = if (playersCol) sortPlayersColumn(col.subList(0, last + 1))
                          else ArrayList(col.subList(0, last + 1))
            var maxW = 0
            for (e in trimmed) {
                val dn = e.tabListDisplayName
                if (dn != null) maxW = max(maxW, tr.width(dn))
            }
            val extra = if (playersCol) 26 else 8
            val w = max(if (playersCol) 116 else 60, min(maxW + extra, 230))
            columns.add(trimmed)
            colWidths.add(w)
            rows = max(rows, trimmed.size)
        }
        if (columns.isEmpty()) return null
        rows = min(rows, 22)

        return Model(columns, colWidths, rows, findServer(mc, tabFooter, tabHeader), footerLine(tabFooter))
    }

    private fun drawColumns(
        ctx: GuiGraphicsExtractor, tr: Font,
        columns: List<List<PlayerInfo>>, colWidths: List<Int>,
        startX: Int, cy: Int, rows: Int, gap: Int, lh: Int
    ) {
        var colX = startX
        for (c in columns.indices) {
            val w = colWidths[c]
            if (c > 0) ctx.fill(colX - gap / 2, cy, colX - gap / 2 + 1, cy + rows * lh, DIVIDER)
            val playersCol = c == 0
            val entries = columns[c]
            var r = 0
            while (r < entries.size && r < rows) {
                val e = entries[r]
                val dn = fishmod.cosmetic.prestige.PrestigeLevelColors.colorizeLevelPrefix(e.tabListDisplayName)
                if (dn == null) {
                    r++
                    continue
                }
                val ry = cy + r * lh
                var tx = colX
                if (playersCol && r > 0) {
                    try {
                        PlayerFaceExtractor.extractRenderState(ctx, e.skin, colX, ry - 1, 8)
                    } catch (ignored: Exception) {
                    }
                    tx = colX + 10
                }
                ctx.text(tr, dn, tx, ry, NAME, true)
                if (playersCol && r > 0 && e.latency > 0) drawSignal(ctx, colX + w - 13, ry, e.latency)
                r++
            }
            colX += w + gap
        }
    }

    private val BLANK_COLOR: Pattern = Pattern.compile("§.")
    private val BLANK_INVISIBLE: Pattern = Pattern.compile("[\\p{Cf}\\p{Z}\\s]")

    private fun blank(e: PlayerInfo): Boolean {
        val dn = e.tabListDisplayName ?: return true
        val s = BLANK_INVISIBLE.matcher(BLANK_COLOR.matcher(dn.string).replaceAll("")).replaceAll("")
        return s.isEmpty()
    }

    private fun roundRect(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        ctx.fill(x1 + 2, y1, x2 - 2, y1 + 1, color)
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color)
        ctx.fill(x1, y1 + 2, x2, y2 - 2, color)
        ctx.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color)
        ctx.fill(x1 + 2, y2 - 1, x2 - 2, y2, color)
    }

    private fun drawSignal(ctx: GuiGraphicsExtractor, x: Int, y: Int, latency: Int) {
        val filled = if (latency <= 75) 4 else if (latency <= 150) 3 else if (latency <= 300) 2 else 1
        for (b in 0 until 4) {
            val h = 2 + b * 2
            val bx = x + b * 3
            ctx.fill(bx, y + 8 - h, bx + 2, y + 8, if (b < filled) BAR_ON else BAR_OFF)
        }
    }

    private fun nameOf(e: PlayerInfo): String {
        return try {
            if (e.profile != null && e.profile.name != null) e.profile.name else ""
        } catch (ex: Exception) {
            ""
        }
    }

    private fun realPing(mc: Minecraft): Int {
        val live = PingTracker.latest()
        if (live > 0) return live
        try {
            val self = mc.connection?.getPlayerInfo(mc.player!!.uuid)
            if (self != null && self.latency > 0) return self.latency
        } catch (ignored: Exception) {
        }
        try {
            val si = mc.currentServer
            if (si != null && si.ping > 0) return si.ping.toInt()
        } catch (ignored: Exception) {
        }
        return -1
    }

    private fun findServer(mc: Minecraft, footer: String?, header: String?): String {
        var hay = (footer ?: "") + " " + (header ?: "")
        try {
            if (mc.level != null) {
                val sb = mc.level!!.scoreboard
                val obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR)
                if (obj != null) for (en in sb.listPlayerScores(obj)) {
                    val team = sb.getPlayersTeam(en.owner())
                    val raw = if (team != null) team.playerPrefix.string + en.owner() + team.playerSuffix.string else en.ownerName().string
                    hay += " " + raw.replace(Regex("§."), "")
                }
            }
        } catch (ignored: Exception) {
        }
        val m = SERVER_ID.matcher(hay)
        return if (m.find()) m.group(1) else "—"
    }

    private fun footerLine(footer: String?): String {
        if (footer != null && footer.isNotEmpty()) for (line in footer.split("\n")) {
            val s = line.replace(Regex("§."), "").trim()
            if (s.uppercase().contains("STORE") || s.uppercase().contains("RANKS")) return "§6$s"
        }
        return "§6Ranks, Boosters & MORE! §eSTORE.HYPIXEL.NET"
    }
}
