package fishmod.features.dungeon

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Party Finder list panel — a scrollable summary of every party listed in the open "Party Finder"
 * chest GUI, drawn beside the vanilla background. Each row shows the host, fill count, floor,
 * missing classes, the listing's note and (optionally) the slowest member PB for that floor.
 * Hovering a row highlights the matching head in the menu (and vice-versa); with "Click Row to
 * Join" on, a left-click on a row clicks that head.
 *
 * Parsing mirrors [PartyFinder]; PB lookups reuse [PartyFinder]'s session cache so nothing is
 * fetched twice.
 */
object PartyFinderPanel {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val MEMBER = Regex("^\\s*(\\w{1,16}):?\\s+(Archer|Tank|Berserk|Healer|Mage)\\s*\\((\\d+)\\)\\s*$")
    private val LEVEL_REQ = Regex("Dungeon Level Required:\\s*(\\d+)")
    private val FLOOR = Regex("Floor:\\s*(?:Floor\\s+)?(\\w+)")
    private val MEMBERS = Regex("Members:\\s*\\(?(\\d+)\\s*/\\s*(\\d+)")
    private val LEADER = Regex("^(\\w{1,16})['’]s? Party$")
    private val NAME = Regex("\\w{1,16}")
    private val PB_LINE = Regex("^(\\d+):(\\d{2})\\s+S\\+?$")
    private val CLASSES = listOf("Archer", "Tank", "Berserk", "Healer", "Mage")

    private const val PANEL_W = 340
    private const val HEADER_H = 18
    private const val ROW_H = 21
    private const val PAD = 5

    private data class Party(
        val slot: Int, val leader: String, val members: Int, val maxMembers: Int,
        val floor: Int, val master: Boolean, val present: Set<String>,
        val memberNames: List<String>, val note: String?, val levelReq: Int,
    )

    private var parties: List<Party> = emptyList()
    private var scroll = 0
    /** [x, y, w, h] of each drawn row, parallel to the visible window. */
    private var rowRects: List<IntArray> = emptyList()
    private var rowFirst = 0

    @JvmStatic
    fun init() {
        fishmod.features.FishHudEditor.register(
            "Party Finder List",
            { FishSettings.pfListX }, { v -> FishSettings.pfListX = v },
            { FishSettings.pfListY }, { v -> FishSettings.pfListY = v },
            PANEL_W, HEADER_H + 10 * ROW_H,
        )
    }

    private fun active(screen: AbstractContainerScreen<*>): Boolean =
        FishSettings.pfListPanel && COLOR.replace(screen.title.string, "") == "Party Finder"

    private fun strip(s: String) = COLOR.replace(s, "")

    private fun loreRaw(stack: ItemStack): List<String> =
        stack.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()

    private fun parseFloor(s: String): Int = s.toIntOrNull() ?: when (s.uppercase()) {
        "I" -> 1; "II" -> 2; "III" -> 3; "IV" -> 4; "V" -> 5; "VI" -> 6; "VII" -> 7
        else -> 0
    }

    private fun collect(screen: AbstractContainerScreen<*>): List<Party> {
        val menu = screen.menu
        val containerSize = (menu.slots.size - 36).coerceAtLeast(0)
        val out = ArrayList<Party>()
        for (i in 0 until containerSize) {
            val stack = menu.slots[i].item
            if (stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) continue
            val raw = loreRaw(stack)
            if (raw.isEmpty()) continue
            val plain = raw.map { strip(it) }
            if (plain.none { it.startsWith("Members:") } && plain.none { MEMBER.matches(it) }) continue

            val present = LinkedHashSet<String>()
            val names = ArrayList<String>()
            var floor = 0; var master = false; var levelReq = 0
            var mem = 0; var maxMem = 5; var note: String? = null
            for (idx in plain.indices) {
                val p = plain[idx]
                if (p.contains("Master Mode", true)) master = true
                MEMBER.find(p)?.let { m -> names.add(m.groupValues[1]); present.add(m.groupValues[2]) }
                FLOOR.find(p)?.let { floor = parseFloor(it.groupValues[1]) }
                LEVEL_REQ.find(p)?.let { levelReq = it.groupValues[1].toIntOrNull() ?: 0 }
                MEMBERS.find(p)?.let { mem = it.groupValues[1].toInt(); maxMem = it.groupValues[2].toInt() }
                if (note == null && p.startsWith("Note:")) {
                    note = raw[idx].substringAfter("Note:").trim().ifEmpty { null }
                }
            }
            val nameStr = strip(stack.hoverName.string).trim()
            val leader = LEADER.find(nameStr)?.groupValues?.get(1)
                ?: nameStr.split(' ').lastOrNull { it.matches(NAME) }
                ?: names.firstOrNull() ?: "?"
            // Fallback when the head has no "Members: (x/y)" line: count the distinct
            // roster, adding the leader only if the lore didn't already list them.
            if (mem == 0) mem = (names + leader).distinctBy { it.lowercase() }.size
            out.add(Party(i, leader, mem, maxMem, floor, master, present, names, note, levelReq))
        }
        return out
    }

    private val FILTER_CLASS = arrayOf("", "Archer", "Berserk", "Healer", "Mage", "Tank")

    private fun passesFilter(p: Party): Boolean {
        FishSettings.pfFilterFloor.let { if (it in 1..7 && p.floor != it) return false }
        when (FishSettings.pfFilterMode) { 1 -> if (p.master) return false; 2 -> if (!p.master) return false }
        if (FishSettings.pfFilterHideFull && p.members >= p.maxMembers) return false
        FishSettings.pfFilterMaxLevel.let { if (it > 0 && p.levelReq > it) return false }
        FishSettings.pfFilterClass.let { if (it in 1..5 && FILTER_CLASS[it] in p.present) return false }
        return true
    }

    private fun filterSummary(): String {
        val bits = ArrayList<String>(5)
        FishSettings.pfFilterFloor.takeIf { it in 1..7 }?.let { bits.add("F$it") }
        when (FishSettings.pfFilterMode) { 1 -> bits.add("Cata"); 2 -> bits.add("MM") }
        FishSettings.pfFilterClass.takeIf { it in 1..5 }?.let { bits.add("needs ${FILTER_CLASS[it]}") }
        if (FishSettings.pfFilterHideFull) bits.add("not full")
        FishSettings.pfFilterMaxLevel.takeIf { it > 0 }?.let { bits.add("lv≤$it") }
        return bits.joinToString(" §8· ")
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!active(screen)) { parties = emptyList(); rowRects = emptyList(); return }
        val all = collect(screen)
        parties = all.filter(::passesFilter)

        val mc = Minecraft.getInstance()
        val font = mc.font
        val summary = filterSummary()

        if (parties.isEmpty()) {
            rowRects = emptyList()
            if (all.isNotEmpty()) {
                val px = FishSettings.pfListX.coerceIn(2, (mc.window.guiScaledWidth - PANEL_W - 2).coerceAtLeast(2))
                val py = FishSettings.pfListY.coerceIn(2, (mc.window.guiScaledHeight - 30).coerceAtLeast(2))
                ctx.text(font, "§e§lParty Finder §7(§c0§8/${all.size}§7) §6⚑", px, py + 2, -1, true)
                ctx.text(font, "§8no parties match — $summary", px, py + HEADER_H, -1, true)
            }
            return
        }
        val acc = screen as HandledScreenAccessor
        val bgX = acc.bgX; val bgY = acc.bgY

        val maxRows = FishSettings.pfListMaxRows.coerceIn(3, 10)
        val visible = minOf(maxRows, parties.size)
        val maxScroll = (parties.size - visible).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)
        rowFirst = scroll

        val sw = mc.window.guiScaledWidth
        val panelH = HEADER_H + visible * ROW_H + PAD
        val panelX = FishSettings.pfListX.coerceIn(2, (sw - PANEL_W - 2).coerceAtLeast(2))
        val panelY = FishSettings.pfListY.coerceIn(2, (mc.window.guiScaledHeight - panelH - 2).coerceAtLeast(2))

        // no solid panel — just the header line + a hairline rule over the world
        val tag = if (maxScroll > 0) "  §8${scroll + 1}-${scroll + visible} / ${parties.size}" else ""
        val count = if (all.size != parties.size) "§7(§f${parties.size}§8/${all.size}§7) §6⚑ §8$summary" else "§7(§f${parties.size}§7)"
        ctx.text(font, "§e§lParty Finder $count$tag", panelX, panelY + 2, -1, true)
        ctx.fill(panelX - PAD, panelY + HEADER_H - 4, panelX + PANEL_W + PAD, panelY + HEADER_H - 3, 0x50E0C060)

        val hoveredMenuIdx = acc.`fishmod$getHoveredSlot`()?.index ?: -1
        val rects = ArrayList<IntArray>(visible)
        var hoverParty = -1

        for (i in 0 until visible) {
            val p = parties[scroll + i]
            val ry = panelY + HEADER_H + i * ROW_H
            rects.add(intArrayOf(panelX, ry, PANEL_W, ROW_H))
            val mouseIn = mouseX >= panelX && mouseX < panelX + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H
            val hot = mouseIn || p.slot == hoveredMenuIdx
            if (hot) hoverParty = scroll + i

            // per-row band so text stays readable over the world; blue tint on hover
            ctx.fill(panelX - PAD, ry - 1, panelX + PANEL_W + PAD, ry + ROW_H - 1,
                if (hot) 0x484CC2FF else if (i % 2 == 1) 0x28000000 else 0x18000000)
            // left accent bar marks the hovered row
            if (hot) ctx.fill(panelX - PAD, ry - 1, panelX - PAD + 2, ry + ROW_H - 1, 0xFF4CC2FF.toInt())

            val fillCol = when {
                p.members >= p.maxMembers -> "§c"
                p.members >= p.maxMembers - 1 -> "§e"
                else -> "§a"
            }
            val fl = if (p.master) "§d§lM${p.floor}" else "§b§lF${p.floor}"

            val line1 = buildString {
                append(if (hot) "§e» " else "§8· ")
                append("§f${p.leader}  $fl §8[$fillCol${p.members}§7/${p.maxMembers}§8]")
                if (p.levelReq > 0) append("  §7lv §f${p.levelReq}")
                if (FishSettings.pfListWorstPb) append("  §8· ").append(worstPb(p))
            }
            ctx.text(font, clip(font, line1, PANEL_W - 4), panelX, ry + 2, -1, true)

            val missing = CLASSES.filter { it !in p.present }.joinToString(" ") { it.first().toString() }
            val line2 = buildString {
                append(if (missing.isEmpty()) "§a§ofull party" else "§7need §c$missing")
                if (FishSettings.pfListNotes && !p.note.isNullOrBlank()) append("  §8· §7").append(strip(p.note))
            }
            ctx.text(font, clip(font, line2, PANEL_W - 14), panelX + 10, ry + 12, -1, true)
        }
        rowRects = rects

        // mirror the hover onto the head in the menu
        if (hoverParty in parties.indices) {
            val s = screen.menu.slots.getOrNull(parties[hoverParty].slot) ?: return
            val sx = bgX + s.x; val sy = bgY + s.y
            ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x804CC2FF.toInt())
            ctx.fill(sx - 1, sy - 1, sx + 17, sy, 0xFF4CC2FF.toInt())
            ctx.fill(sx - 1, sy + 16, sx + 17, sy + 17, 0xFF4CC2FF.toInt())
            ctx.fill(sx - 1, sy, sx, sy + 16, 0xFF4CC2FF.toInt())
            ctx.fill(sx + 16, sy, sx + 17, sy + 16, 0xFF4CC2FF.toInt())
        }
    }

    private fun worstPb(p: Party): String {
        if (p.floor <= 0) return "§8wPB —"
        var worstName: String? = null
        var worstSec = -1
        var pending = false
        for (n in (listOf(p.leader) + p.memberNames).distinct()) {
            val d = PartyFinder.cached(n)
            if (d == null) { pending = true; PartyFinder.prefetch(n); continue }
            val arr = if (p.master) d.masterPbs else d.cataPbs
            val m = arr.getOrNull(p.floor)?.trim()?.let { PB_LINE.find(it) }
            val sec = if (m == null) Int.MAX_VALUE else m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            if (sec > worstSec) { worstSec = sec; worstName = n }
        }
        if (worstName == null) return if (pending) "§8wPB …" else "§8wPB —"
        val t = if (worstSec == Int.MAX_VALUE) "§cno S+" else "§f${worstSec / 60}:${(worstSec % 60).toString().padStart(2, '0')}"
        return "§7wPB §f$worstName §7$t${if (pending) " §8…" else ""}"
    }

    private fun clip(font: net.minecraft.client.gui.Font, s: String, maxW: Int): String {
        if (font.width(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && font.width("$t..") > maxW) t = t.dropLast(1)
        return t.trimEnd('§') + ".."
    }

    private fun rowUnder(mx: Double, my: Double): Int {
        rowRects.forEachIndexed { i, r ->
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return rowFirst + i
        }
        return -1
    }

    /** @return true to swallow the scroll (cursor was over the panel). */
    @JvmStatic
    fun mouseScrolled(mx: Double, my: Double, vt: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!active(screen) || parties.isEmpty() || rowRects.isEmpty()) return false
        val first = rowRects.first(); val last = rowRects.last()
        val inX = mx >= first[0] && mx < first[0] + PANEL_W
        val inY = my >= first[1] - HEADER_H && my < last[1] + last[3]
        if (!inX || !inY) return false
        if (vt != 0.0) scroll = (scroll + if (vt > 0) -1 else 1).coerceAtLeast(0)
        return true
    }

    /** @return true to swallow the click. */
    @JvmStatic
    fun mouseClicked(button: Int, mx: Double, my: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!active(screen) || parties.isEmpty() || rowRects.isEmpty()) return false
        val idx = rowUnder(mx, my)
        if (idx < 0 || idx >= parties.size) return false
        if (button == 0 && FishSettings.pfListClickJoin) {
            val mc = Minecraft.getInstance()
            val p = mc.player
            if (p != null) mc.gameMode?.handleContainerInput(
                screen.menu.containerId, parties[idx].slot, 0, ContainerInput.PICKUP, p)
        }
        return true
    }
}
