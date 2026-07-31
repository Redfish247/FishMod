package fishmod.features

import fishmod.features.dungeon.PartyCommandHandler
import fishmod.utils.PingTracker
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

/** Compact custom tab list. Hypixel packs every column into player-list entries ordered column-major by `!A-a`/`!B-a` sort keys, so entries are rendered verbatim into a translucent panel rather than re-deriving data. Adds a header stat bar and footer. Opt-in via config. */
object CompactTab {

    private const val BG_RGB = 0x0B0D13

    /** Panel background alpha derived from the FishSettings slider (0..100). */
    private fun bgPanel(): Int {
        val pct = max(0, min(100, FishSettings.compactTabOpacity))
        val a = (pct * 2.55).roundToInt() // 0..255
        return (a shl 24) or BG_RGB
    }

    /** Header strip uses ~45% of the panel alpha so it reads as a lighter band. */
    private fun bgHead(): Int {
        val pct = max(0, min(100, FishSettings.compactTabOpacity))
        val a = (pct * 2.55 * 0.45).roundToInt() // 0..115
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

    /** True if the current tab uses Hypixel's lobby column-major encoding (entries named "!A-…"/"!B-…"); dungeons/Kuudra/Rift/Garden etc. don't, and fall back to vanilla rendering. */
    @JvmStatic
    fun shouldRender(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.connection == null) return false
        for (e in mc.connection!!.onlinePlayers) {
            if (COL_KEY.matcher(nameOf(e)).find()) return true
        }
        return false
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenW: Int, tabHeader: String?, tabFooter: String?) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.connection == null) return
        val tr = mc.font
        val lh = 10

        // group entries into Hypixel's tab columns (by their !X- sort key)
        val all = ArrayList(mc.connection!!.onlinePlayers)
        all.sortWith(Comparator { a, b -> nameOf(a).compareTo(nameOf(b), ignoreCase = true) })
        val grouped = LinkedHashMap<String, MutableList<PlayerInfo>>()
        for (e in all) {
            val m = COL_KEY.matcher(nameOf(e))
            if (m.find()) grouped.getOrPut(m.group(1).uppercase()) { ArrayList() }.add(e)
        }
        // Non-lobby tabs (dungeons / Kuudra / Rift / Garden / etc.) have no !X- keys —
        // caller should have routed to vanilla via shouldRender(); guard anyway.
        if (grouped.isEmpty()) return

        // smart sizing: trim trailing blank rows, drop empty columns, width = content
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
            // Drop empty columns AND header-only columns (e.g. an "Info" column with no content).
            if (last < 0 || nonBlank <= 1) {
                first = false
                continue
            }
            val trimmed = ArrayList(col.subList(0, last + 1))
            val playersCol = first
            first = false
            var maxW = 0
            for (e in trimmed) {
                val dn = e.tabListDisplayName
                if (dn != null) maxW = max(maxW, tr.width(dn))
            }
            val extra = if (playersCol) 26 else 8 // head + signal bars on players col
            val w = max(if (playersCol) 116 else 60, min(maxW + extra, 230))
            columns.add(trimmed)
            colWidths.add(w)
            rows = max(rows, trimmed.size)
        }
        if (columns.isEmpty()) return
        rows = min(rows, 22)

        val ping = realPing(mc)
        val fps = mc.fps
        val tps = PartyCommandHandler.currentTps()
        val server = findServer(mc, tabFooter, tabHeader)

        // panel geometry
        val pad = 8
        val gap = 8
        var contentW = 0
        for (w in colWidths) contentW += w
        contentW += gap * (columns.size - 1)
        val pw = min(screenW - 12, contentW + pad * 2)
        val x0 = (screenW - pw) / 2
        val y0 = 4
        val headH = 28
        val bodyH = rows * lh + 6
        val footH = 12
        val totalH = headH + bodyH + footH

        // rounded translucent panel
        roundRect(ctx, x0, y0, x0 + pw, y0 + totalH, bgPanel())
        roundRect(ctx, x0, y0, x0 + pw, y0 + headH, bgHead())

        // header stat bar (PLAYERS cell removed — count already shown atop the Players column)
        val labels = arrayOf("SERVER", "TPS", "FPS", "PING")
        val values = arrayOf(
            server,
            if (tps < 0) "—" else String.format("%.2f", tps),
            fps.toString(),
            if (ping < 0) "—" else "${ping}ms"
        )
        val cellW = pw / labels.size
        for (i in labels.indices) {
            val cxL = x0 + i * cellW
            if (i > 0) ctx.fill(cxL, y0 + 5, cxL + 1, y0 + headH - 5, DIVIDER)
            val cxC = cxL + cellW / 2
            ctx.centeredText(tr, "§7" + labels[i], cxC, y0 + 5, LABEL)
            val vc = if (i == 1 && tps >= 0 && tps < 19) 0xFFFF5555.toInt() else VALUE
            ctx.centeredText(tr, values[i], cxC, y0 + 16, vc)
        }
        ctx.fill(x0 + 4, y0 + headH - 1, x0 + pw - 4, y0 + headH, DIVIDER)

        // columns
        val cy = y0 + headH + 3
        var colX = x0 + pad
        for (c in columns.indices) {
            val w = colWidths[c]
            if (c > 0) ctx.fill(colX - gap / 2, cy, colX - gap / 2 + 1, cy + rows * lh, DIVIDER)
            val playersCol = c == 0
            val entries = columns[c]
            var r = 0
            while (r < entries.size && r < rows) {
                val e = entries[r]
                val dn = e.tabListDisplayName
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
                // draw styled text directly (no plain-string trim -> keeps rank colors)
                ctx.text(tr, dn, tx, ry, NAME, true)
                if (playersCol && r > 0 && e.latency > 0) drawSignal(ctx, colX + w - 13, ry, e.latency)
                r++
            }
            colX += w + gap
        }

        ctx.centeredText(tr, footerLine(tabFooter), x0 + pw / 2, y0 + totalH - footH + 2, GOLD)
    }

    private fun blank(e: PlayerInfo): Boolean {
        val dn = e.tabListDisplayName ?: return true
        // Strip color codes and invisible formatting chars so Hypixel's hidden-char padding rows read as blank.
        val s = dn.string.replace(Regex("§."), "").replace(Regex("[\\p{Cf}\\p{Z}\\s]"), "")
        return s.isEmpty()
    }

    /** 2px-radius rounded rectangle fill. */
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

    /** [PingTracker]'s client->server->client round trip is the freshest source; falls back to tab latency then server-list ping when unavailable (briefly after join). */
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
        // sidebar often carries the server id (e.g. "05/27/26 m108AB")
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
