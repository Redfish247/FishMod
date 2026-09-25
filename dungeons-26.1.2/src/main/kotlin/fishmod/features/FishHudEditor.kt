package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.FishConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.function.BooleanSupplier
import java.util.function.DoubleConsumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier

class FishHudEditor(private val parent: Screen) : Screen(Component.literal("Edit HUD")) {

    class HudEntry @JvmOverloads constructor(
        private val nameVal: String,
        private val getXVal: IntSupplier,
        private val setXVal: IntConsumer,
        private val getYVal: IntSupplier,
        private val setYVal: IntConsumer,
        private val wVal: Int,
        private val hVal: Int,
        private val lockedVal: Boolean = false,
        private val getScaleVal: DoubleSupplier? = null,
        private val setScaleVal: DoubleConsumer? = null,
        private val visibleVal: BooleanSupplier? = null
    ) {
        fun name(): String = nameVal
        fun getX(): IntSupplier = getXVal
        fun setX(): IntConsumer = setXVal
        fun getY(): IntSupplier = getYVal
        fun setY(): IntConsumer = setYVal
        fun w(): Int = wVal
        fun h(): Int = hVal
        fun locked(): Boolean = lockedVal
        fun getScale(): DoubleSupplier? = getScaleVal
        fun setScale(): DoubleConsumer? = setScaleVal
        fun visible(): BooleanSupplier? = visibleVal

        fun scale(): Double = if (getScaleVal != null) getScaleVal.asDouble else 1.0
        fun isVisible(): Boolean = visibleVal == null || visibleVal.asBoolean
    }

    companion object {
        private val ENTRIES: MutableList<HudEntry> = ArrayList()

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            getScale: DoubleSupplier, setScale: DoubleConsumer
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, null))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            getScale: DoubleSupplier, setScale: DoubleConsumer,
            visible: BooleanSupplier
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, visible))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            visible: BooleanSupplier
        ) {
            ENTRIES.add(HudEntry(name, getX, setX, getY, setY, w, h, false, null, null, visible))
        }

        @JvmStatic
        fun registerLocked(name: String, getX: IntSupplier, getY: IntSupplier, w: Int, h: Int) {
            ENTRIES.add(HudEntry(name, getX, IntConsumer { }, getY, IntConsumer { }, w, h, true))
        }

        @JvmStatic
        fun register(name: String, component: HUDComponent) {
            ENTRIES.add(
                HudEntry(
                    name,
                    IntSupplier { Math.round(component.scaledX * component.scale) },
                    IntConsumer { v ->
                        val ww = Minecraft.getInstance().window.guiScaledWidth
                        val cur = Math.round(component.scaledX * component.scale)
                        component.move((v - cur).toDouble() / ww, 0.0)
                    },
                    IntSupplier { Math.round(component.scaledY * component.scale) },
                    IntConsumer { v ->
                        val wh = Minecraft.getInstance().window.guiScaledHeight
                        val cur = Math.round(component.scaledY * component.scale)
                        component.move(0.0, (v - cur).toDouble() / wh)
                    },
                    component.width, component.height,
                    false,
                    DoubleSupplier { component.scale.toDouble() },
                    DoubleConsumer { v -> component.scale = v.toFloat() },
                    BooleanSupplier { component.editable() }
                )
            )
        }

        private val DEFAULTS: Map<String, DoubleArray> = java.util.Map.ofEntries(
            java.util.Map.entry("Pet", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Session Stats", doubleArrayOf(10.0, 120.0, 1.0)),
            java.util.Map.entry("Simon Says", doubleArrayOf(10.0, 360.0, 1.0)),
            java.util.Map.entry("Soulflow", doubleArrayOf(10.0, 60.0, 1.0)),
            java.util.Map.entry("Spirit Bear", doubleArrayOf(10.0, 165.0, 1.5)),
            java.util.Map.entry("Blessings", doubleArrayOf(10.0, 100.0, 1.0)),
            java.util.Map.entry("PB Pace", doubleArrayOf(10.0, 300.0, 1.0)),
            java.util.Map.entry("Tick Timer", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Wither Dragon Timer", doubleArrayOf(-1.0, 100.0, 2.0)),
            java.util.Map.entry("Crystal Spawn Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("Crystal Reminder", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Storm Death Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("LB Release Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Py Tick Timer", doubleArrayOf(10.0, 116.0, 1.0)),
            java.util.Map.entry("Storm Crushed", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Term Start Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Section Progress", doubleArrayOf(10.0, 116.0, 1.0)),
            java.util.Map.entry("Current Section", doubleArrayOf(10.0, 190.0, 1.0)),
            java.util.Map.entry("Device Completed", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Melody Warning", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Section Completion", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Players Leaped", doubleArrayOf(10.0, 90.0, 1.0)),
            java.util.Map.entry("Relic Spawn Timer", doubleArrayOf(10.0, 180.0, 1.0)),
            java.util.Map.entry("Goldor Splits", doubleArrayOf(10.0, 128.0, 1.0)),
            java.util.Map.entry("Splits", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Puzzles", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Party Finder List", doubleArrayOf(6.0, 45.0, 1.0)),
            java.util.Map.entry("Dungeon Map", doubleArrayOf(100.0, 100.0, 1.0)),
            java.util.Map.entry("Slayer Spawn", doubleArrayOf(10.0, 140.0, 1.0)),
            java.util.Map.entry("Slayer Stats", doubleArrayOf(10.0, 170.0, 1.0)),
            java.util.Map.entry("Slayer Boss Timer", doubleArrayOf(10.0, 255.0, 1.0)),
            java.util.Map.entry("Slayer Profit", doubleArrayOf(240.0, 90.0, 1.0))
        )

        // Sidebar groups, in display order. HUDs not listed land in "Other".
        private val GROUPS: List<Pair<String, List<String>>> = listOf(
            "Dungeons" to listOf(
                "Splits", "Session Stats", "PB Pace", "Blessings", "Puzzles", "Simon Says",
                "Spirit Bear", "Invincibility Timer", "Dungeon Breaker", "Secret Overlay", "Quiz Timer",
            ),
            "Dungeon Map" to listOf("Dungeon Map", "Dungeon Map Info", "Dungeon Score Title"),
            "Floor 7" to listOf(
                "Tick Timer", "Wither Dragon Timer", "Crystal Spawn Time", "Crystal Reminder",
                "Storm Death Time", "LB Release Timer", "Py Tick Timer", "Storm Crushed", "Term Start Timer",
                "Section Progress", "Goldor Splits", "Current Section", "Device Completed",
                "Melody Warning", "Section Completion", "Storm Over Alert", "Players Leaped",
                "S4 Alert", "S4 Debug", "Relic Spawn Timer",
            ),
            "HUD & Overlays" to listOf(
                "Custom Scoreboard", "Performance", "Pet", "Soulflow", "Chat Notifications", "Warp Cooldown", "Tac Timer", "Rag Timer", "Spring Boots",
            ),
            "Party & Social" to listOf("Party Finder List"),
            "Slayer" to listOf("Slayer Spawn", "Slayer Stats", "Slayer Boss Timer", "Slayer Profit"),
            "Mining" to listOf("Mining Profit"),
        )

        // right = optional value column drawn right-aligned at `width` (or the widest row).
        private class Sample(
            val lines: List<String>,
            val center: Boolean = false,
            val lineH: Int = 10,
            val right: List<String>? = null,
            val width: Int? = null,
            val bg: Int = 0,
        )

        private fun s(vararg lines: String, lineH: Int = 10) = Sample(lines.toList(), lineH = lineH)
        private fun c(vararg lines: String, lineH: Int = 10) = Sample(lines.toList(), center = true, lineH = lineH)
        private fun cols(rows: List<Pair<String, String>>, width: Int? = null, lineH: Int = 10) =
            Sample(rows.map { it.first }, lineH = lineH, right = rows.map { it.second }, width = width)

        // Example content drawn in the editor in place of a box, so you see what you're placing.
        private val SAMPLES: Map<String, Sample> = mapOf(
            "Tick Timer" to c("§f12.35"),
            "Crystal Spawn Time" to c("§d1.20"),
            "Crystal Reminder" to c("§bPlace Crystal!"),
            "Storm Death Time" to c("§538.45"),
            "LB Release Timer" to c("§c2.35"),
            "Storm Crushed" to c("§6||| §bStorm crushed! §6|||"),
            "Term Start Timer" to c("§e3.45"),
            "Section Progress" to c("§a(§c3§a/7)"),
            "Current Section" to s("§5Section: §f 2"),
            "Device Completed" to c("§aDevice Completed!"),
            "Melody Warning" to c("§5§lHEALER §r§dhas melody! 2/4"),
            "Section Completion" to c("§aSection completed!"),
            "S4 Alert" to c("§c⚠ Steve LEAPED EARLY"),
            "S4 Debug" to s(
                "§6S4 Tracker", "§fAlex: §6⚠ early§f (1)", "§fBob: §7...§f (0)",
                "§fJoe: §c☠ dead§f (1)", "§fSteve: §a✓ core§f (2)",
            ),
            "Goldor Splits" to cols(listOf(
                "§61st " to "§a12.34s§8 (§712.05s§8)",
                "§62nd " to "§a13.10s§8 (§712.80s§8)",
                "§63rd " to "§a11.92s§8 (§711.70s§8)",
                "§64th " to "§a14.05s§8 (§713.85s§8)",
            ), width = 120),
            "Splits" to cols(listOf(
                "§4Blood Open " to "§a25.70s§8 (§725.10s§8)",
                "§cBlood Clear " to "§a1m 7.02s§8 (§765.40s§8)",
                "§dPortal " to "§a31.44s§8 (§730.90s§8)",
                "§5Maxor " to "§a28.15s§8 (§727.60s§8)",
                "§3Storm " to "§a52.80s§8 (§751.95s§8)",
                "§eTerminals " to "§a46.21s§8 (§745.30s§8)",
            ), width = 165),
            "Est. Total (follows Splits)" to cols(listOf(
                "§3Est. Total " to "§a6m 12.40s",
                "§8Lag Lost " to "§c1.35s",
            ), width = 165),
            "Puzzles" to s("§fPuzzles: (3)", "§aCreeper Beams: [✔]", "§cIce Path: [✦]", "§9???: [✦]"),
            "PB Pace" to s("§6§lPB Pace §7(M7)", "§fStorm §c+1.24s", "§7vs PB: §cbehind §c+3.87s", lineH = 11),
            "Dungeon Map Info" to c(
                "§fSecrets: §b34§7-§e41§7-§c52   §fScore: §e287",
                "§fDeaths: §a0   §fM: §a✔   §fP: §c✖   §fCrypts: §e3",
            ),
            "Warp Cooldown" to s("§eWarp: §a23.4s"),
            "Blessings" to s("§4Power: §a34", "§5Time: §a5"),
            "Invincibility Timer" to s("§eBonzo §7●", "§7Spirit §7●", "§7Phoenix §7✔"),
            "Dungeon Breaker" to s("§cCharges: §e17§7/§e20§c⸕"),
            "Spirit Bear" to s("§cBear: §f18/25"),
            "Session Stats" to s("§7Runs: §a14", "§7Deaths: §c3", "§7R/hr: §e9.2", "§7Time: §f1h 31m", lineH = 12),
            "Simon Says" to s("§bSimon Says: §a3§7/5"),
            "Party Finder List" to s(
                "§e§lParty Finder §7(§f7§7)",
                "§8· §fTechnoFish  §d§lM7 §8[§e4§7/5§8]  §7lv §f45  §8· §7wPB §fSlowGuy §f5:48",
                "   §7need §cH  §8· §7s+ runs, no leechers",
                "§8· §fGoldorMain  §b§lF7 §8[§e2§7/5§8]  §7lv §f38  §8· §7wPB §fTechnoFish §f6:02",
                "   §7need §aT §9M  §8· §7chill run",
            ),
            "Relic Spawn Timer" to s("§dRelic §f1.85s"),
            "Wither Dragon Timer" to s("§53450ms"),
            "Chat Notifications" to c("§e⚑ Wither Key Picked Up!", lineH = 11),
            "Soulflow" to s("§3Soulflow: §f12,345"),
            "Quiz Timer" to s("§dQuiz §7(§f2/3§7): §b8.4s"),
            "Secret Overlay" to s("§7Secrets: §e5§7/§e9"),
            "Performance" to s("§7FPS: §a144", "§7TPS: §a19.9", "§7Ping: §a42ms"),
            "Storm Over Alert" to s("§dStorm Over!"),
            "Py Tick Timer" to c("§d12.35"),
            "Players Leaped" to s("§5Leaped (HEE2): §e2/4"),
            "Dungeon Score Title" to c("§aOn pace for 300 §7(12m 34s)"),
            "Custom Scoreboard" to Sample(
                listOf("§e§lSKYBLOCK") + List(13) { "" },
                center = true,
                lineH = 9,
                right = listOf(
                    "", "§707/14/26 §8m12AB", "", "Late Summer 5th", "§7☀ 2:40pm", "§7⏣ §bVillage",
                    "", "Purse: §61,234,567", "Bits: §b1,024", "", "§7TPS: §a19.98", "§7Ping: §a42ms", "",
                    "§ewww.hypixel.net",
                ),
                width = SCOREBOARD_W,
                bg = 0x4C000000,
            ),
            "Tac Timer" to s("§5Tac: §a2.4"),
            "Rag Timer" to s("§5Rag: §a8.5s"),
            "Spring Boots" to s("§aCharge: §f67%"),
            "Pet" to s("§6Ender Dragon §a+1.2k §7(845.3k/1.9M 44.5%)"),
            "Mining Profit" to s(
                "§b§lMining Profit", "§71284x §fFine Jade Gemstone §6385.2k", "§72450x §fEnchanted Mithril §6310.5k",
                "§7Total: §6695.7k", "§7Per hour: §61.39m", "§7Time: §f30m",
            ),
            "Slayer Spawn" to s("§5§lRevenant Horror V", "§7Spawn: §f1,850 §7/ §f2,400 §8(77%)", lineH = 12),
            "Slayer Stats" to s("§5§lSLAYER STATS", "§7XP: §f12.5K", "§7Kills: §f84", "§7XP/hr: §e45.2K", "§7Kills/hr: §e31", lineH = 12),
            "Slayer Boss Timer" to s("§6Boss: §f12.84s", "§7PB: §f9.51s", "§a§lNEW PB!", "§7Cycle: §f52.3s", "§7Since kill: §f18.4s", lineH = 12),
            "Slayer Profit" to cols(listOf(
                "§e§lRevenant Horror 5 Profit Tracker" to "",
                "§71x §fShard of the Shredded" to "§61.2M",
                "§73x §fFoul Flesh" to "§6186K",
                "§71,284x §6Mob Kill Coins" to "§6356K",
                "§7142x §fRevenant Flesh" to "§68.5K",
                " §7Slayer Spawn Costs:" to "§c-2.1M",
                "§7Bosses killed:" to "§e42",
                "§eTotal Profit:" to "§64,812,300 coins",
                "§eProfit/h:" to "§63.2M",
            ), lineH = 12),
        )

        const val SCOREBOARD_W = 118
        const val SCORE_TITLE_W = 200

        @JvmStatic
        fun isOpen(): Boolean = Minecraft.getInstance().screen is FishHudEditor

        private const val SNAP = 4
        private const val MARGIN = 4
        private const val GRID = 10
        private const val GRIP = 3
        private const val MIN_SCALE = 0.5
        private const val MAX_SCALE = 3.0

        private const val BTN_W = 60
        private const val BTN_H = 18
        private const val BTN_GAP = 6

        private const val SIDE_W = 160
        private const val SIDE_ROW = 12
        private const val SIDE_TOP = 26
        private const val HANDLE_W = 10
        private const val HANDLE_H = 44

        private val ACCENT = 0xFF00AACC.toInt()
        private val ACCENT_HOVER = 0xFF00CCEE.toInt()
        private val GUIDE = 0xFFFF4FB0.toInt()
        private val GRID_LINE = 0x14FFFFFF
        private val SIDE_BG = 0xE8101418.toInt()
        private val ROW_HOV = 0x22FFFFFF
        private val ROW_PICKED = 0x3300AACC

        private val CONTROLS = listOf(
            "Hover Left Edge to Pick HUDs",
            "Drag to Move",
            "Scroll or Drag Corner to Resize",
            "Tab to Swap Selected HUD",
            "Arrows for Precision Move (Shift = 10px)",
            "Ctrl+H to Center Horizontally",
            "Ctrl+V to Center Vertically",
            "Hold Alt to Disable Snapping",
            "R to Reset Selected HUD",
            "G to Toggle Grid",
            "Ctrl+Z / Ctrl+Y to Undo / Redo",
            "Enter to Save, Esc to Cancel",
            "F1 to Hide Controls",
        )

        private val FOLLOWS = Regex("\\(follows (.+)\\)")

        // Session-sticky so reopening the editor keeps the user's choices.
        private var showControls = true
        private var showGrid = false
        private val picked = LinkedHashSet<String>()
    }

    private data class Pos(val x: Int, val y: Int, val scale: Double)

    private sealed class Row {
        class Header(val text: String) : Row()
        class Action(val text: String, val run: () -> Unit) : Row()
        class Hud(val e: HudEntry) : Row()
    }

    private var selected: HudEntry? = null
    private var dragging: HudEntry? = null
    private var resizing: HudEntry? = null
    private var dragOffX = 0
    private var dragOffY = 0
    private var dragBefore: List<Pos>? = null
    private var guideX: Int? = null
    private var guideY: Int? = null
    private var resetArmed = false
    private var sideOpen = false
    private var sideScroll = 0

    private val undoStack = ArrayDeque<List<Pos>>()
    private val redoStack = ArrayDeque<List<Pos>>()
    private val opened: List<Pos> = snapshot()

    init {
        picked.retainAll(ENTRIES.map { it.name() }.toSet())
    }

    override fun isPauseScreen(): Boolean = false

    // ---- entries -------------------------------------------------------------------------------

    private fun available(): List<HudEntry> = ENTRIES

    // Movable HUDs in sidebar order.
    private fun listed(): List<HudEntry> {
        val avail = available().filter { !it.locked() }
        val seen = HashSet<HudEntry>()
        val out = ArrayList<HudEntry>()
        for ((_, names) in GROUPS) for (n in names) avail.firstOrNull { it.name() == n }?.let { if (seen.add(it)) out += it }
        avail.filter { it !in seen }.sortedBy { it.name() }.forEach { out += it }
        return out
    }

    private fun shown(): List<HudEntry> = available().filter { e ->
        if (e.locked()) FOLLOWS.find(e.name())?.groupValues?.get(1)?.let { it in picked } == true
        else e.name() in picked
    }

    private fun sidebarRows(): List<Row> {
        val rows = ArrayList<Row>()
        rows += Row.Action("Show all") { picked.clear(); listed().forEach { picked += it.name() } }
        rows += Row.Action("Hide all") { picked.clear(); selected = null }
        val avail = listed()
        val grouped = HashSet<HudEntry>()
        for ((g, names) in GROUPS) {
            val items = names.mapNotNull { n -> avail.firstOrNull { it.name() == n && it !in grouped } }
            if (items.isEmpty()) continue
            rows += Row.Header(g)
            items.forEach { grouped += it; rows += Row.Hud(it) }
        }
        val rest = avail.filter { it !in grouped }
        if (rest.isNotEmpty()) {
            rows += Row.Header("Other")
            rest.forEach { rows += Row.Hud(it) }
        }
        return rows
    }

    private fun sample(e: HudEntry): Sample? = SAMPLES[e.name()]

    // Unscaled box size: measured from the example when there is one, else the registered size.
    private fun baseW(e: HudEntry): Int {
        val s = sample(e) ?: return e.w()
        s.width?.let { return it }
        val r = s.right
        if (r != null) return s.lines.indices.maxOf { i -> this.font.width(s.lines[i]) + 6 + this.font.width(r.getOrElse(i) { "" }) }
        val tw = s.lines.maxOf { this.font.width(it) }
        return if (s.center) Math.max(e.w(), tw) else tw
    }

    private fun baseH(e: HudEntry): Int {
        val s = sample(e) ?: return e.h()
        return (s.lines.size - 1) * s.lineH + 9
    }

    private fun sw(e: HudEntry) = Math.max(4, (baseW(e) * e.scale()).toInt())
    private fun sh(e: HudEntry) = Math.max(4, (baseH(e) * e.scale()).toInt())

    private fun snapshot(): List<Pos> = ENTRIES.map { Pos(it.getX().asInt, it.getY().asInt, it.scale()) }

    private fun restore(s: List<Pos>) {
        ENTRIES.forEachIndexed { i, e ->
            val p = s.getOrNull(i) ?: return@forEachIndexed
            if (e.locked()) return@forEachIndexed
            e.setScale()?.accept(p.scale)
            e.setX().accept(p.x)
            e.setY().accept(p.y)
        }
    }

    private fun pushUndo(before: List<Pos>) {
        if (before == snapshot()) return
        undoStack.addLast(before)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun edit(action: () -> Unit) {
        val before = snapshot()
        action()
        pushUndo(before)
    }

    private fun moveTo(e: HudEntry, x: Int, y: Int) {
        e.setX().accept(Math.max(0, Math.min(this.width - sw(e), x)))
        e.setY().accept(Math.max(0, Math.min(this.height - sh(e), y)))
    }

    private fun resetEntry(e: HudEntry) {
        if (e.locked()) return
        val d = DEFAULTS[e.name()] ?: return
        e.setScale()?.accept(d[2])
        e.setX().accept(d[0].toInt())
        e.setY().accept(d[1].toInt())
    }

    private fun pickOnly(e: HudEntry) {
        picked.clear()
        picked += e.name()
        selected = e
    }

    private fun altDown(): Boolean {
        val w = Minecraft.getInstance().window
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT)
    }

    // Snaps the box's near edge, centre or far edge to screen edges/margins/centre and other shown HUDs.
    private fun snap(e: HudEntry, x: Int, y: Int): Pair<Int, Int> {
        val xs = mutableListOf(0, MARGIN, this.width / 2, this.width - MARGIN, this.width)
        val ys = mutableListOf(0, MARGIN, this.height / 2, this.height - MARGIN, this.height)
        for (o in shown()) {
            if (o === e) continue
            val ox = o.getX().asInt
            val oy = o.getY().asInt
            xs += listOf(ox, ox + sw(o) / 2, ox + sw(o))
            ys += listOf(oy, oy + sh(o) / 2, oy + sh(o))
        }
        fun best(pos: Int, size: Int, lines: List<Int>): Pair<Int, Int?> {
            var bd = SNAP + 1
            var bl: Int? = null
            for (off in intArrayOf(0, size / 2, size)) for (l in lines) {
                val d = l - (pos + off)
                if (Math.abs(d) < Math.abs(bd)) { bd = d; bl = l }
            }
            return if (bl != null && Math.abs(bd) <= SNAP) Pair(pos + bd, bl) else Pair(pos, null)
        }
        val (nx, gx) = best(x, sw(e), xs)
        val (ny, gy) = best(y, sh(e), ys)
        guideX = gx
        guideY = gy
        return Pair(nx, ny)
    }

    // ---- layout helpers ------------------------------------------------------------------------

    private fun doneX() = this.width / 2 - BTN_W - BTN_GAP / 2
    private fun resetX() = this.width / 2 + BTN_GAP / 2
    private fun btnY() = this.height - 28

    private fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx in x..(x + w) && my in y..(y + h)

    private fun handleY() = (this.height - HANDLE_H) / 2

    private fun sideMaxScroll(): Int = Math.max(0, sidebarRows().size * SIDE_ROW - (this.height - SIDE_TOP - 6))

    private fun updateSide(mx: Int, my: Int) {
        if (dragging != null || resizing != null) { sideOpen = false; return }
        sideOpen = if (sideOpen) mx <= SIDE_W else mx <= HANDLE_W && my in handleY()..(handleY() + HANDLE_H)
    }

    private fun rowAt(my: Int): Row? {
        if (my < SIDE_TOP) return null
        return sidebarRows().getOrNull((my - SIDE_TOP + sideScroll) / SIDE_ROW)
    }

    private fun onGrip(e: HudEntry, mx: Int, my: Int): Boolean {
        if (e.locked() || e.setScale() == null) return false
        val gx = e.getX().asInt + sw(e)
        val gy = e.getY().asInt + sh(e)
        return mx in (gx - GRIP - 1)..(gx + GRIP) && my in (gy - GRIP - 1)..(gy + GRIP)
    }

    private fun boxUnder(mx: Int, my: Int): HudEntry? = shown().lastOrNull { e ->
        val x = e.getX().asInt
        val y = e.getY().asInt
        mx in x..(x + sw(e)) && my in y..(y + sh(e))
    }

    // ---- rendering -----------------------------------------------------------------------------

    private fun outline(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, c: Int) {
        ctx.fill(x, y, x + w, y + 1, c)
        ctx.fill(x, y + h - 1, x + w, y + h, c)
        ctx.fill(x, y, x + 1, y + h, c)
        ctx.fill(x + w - 1, y, x + w, y + h, c)
    }

    private fun drawExample(ctx: GuiGraphicsExtractor, e: HudEntry, x: Int, y: Int) {
        val s = sample(e)
        val sc = e.scale().toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(sc, sc)
        if (s != null) {
            val bw = baseW(e)
            if (s.bg != 0) ctx.fill(0, 0, bw, baseH(e), s.bg)
            s.lines.forEachIndexed { i, line ->
                val lx = if (s.center) (bw - this.font.width(line)) / 2 else 0
                ctx.text(this.font, line, lx, i * s.lineH, 0xFFFFFFFF.toInt(), true)
                s.right?.getOrNull(i)?.let { r -> ctx.text(this.font, r, bw - this.font.width(r), i * s.lineH, 0xFFFFFFFF.toInt(), true) }
            }
        } else {
            // No example (e.g. the map): faint placeholder of the registered size.
            ctx.fill(0, 0, e.w(), e.h(), 0x40000000)
            ctx.centeredText(this.font, "§7" + e.name(), e.w() / 2, (e.h() - 8) / 2, 0xFFFFFFFF.toInt())
        }
        pose.popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        updateSide(mouseX, mouseY)
        ctx.fill(0, 0, this.width, this.height, 0x60000000)

        if (showGrid) {
            var gx = GRID
            while (gx < this.width) { ctx.fill(gx, 0, gx + 1, this.height, GRID_LINE); gx += GRID }
            var gy = GRID
            while (gy < this.height) { ctx.fill(0, gy, this.width, gy + 1, GRID_LINE); gy += GRID }
        }

        val sel = selected
        val header = when {
            sel != null -> "§b${sel.name()} §7· §f${Math.round(sel.scale() * 100)}% §7· §fx ${sel.getX().asInt}, y ${sel.getY().asInt}"
            picked.isEmpty() -> "§7Hover the left edge to pick a HUD to move"
            else -> "§7Click a HUD to select it"
        }
        ctx.centeredText(this.font, header, this.width / 2, 10, 0xFFFFFFFF.toInt())

        val hover = if (dragging == null && resizing == null && !sideOpen) boxUnder(mouseX, mouseY) else null
        for (e in shown()) {
            val x = e.getX().asInt
            val y = e.getY().asInt
            val w = sw(e)
            val h = sh(e)
            drawExample(ctx, e, x, y)
            when {
                e === sel -> {
                    outline(ctx, x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF.toInt())
                    val tag = e.name() + " " + Math.round(e.scale() * 100) + "%"
                    val tw = this.font.width(tag)
                    val ty = if (y >= 13) y - 12 else y + h + 2
                    ctx.fill(x - 1, ty, x + tw + 3, ty + 11, ACCENT)
                    ctx.text(this.font, tag, x + 1, ty + 2, 0xFF0B1417.toInt(), false)
                    if (e.setScale() != null && !e.locked()) {
                        ctx.fill(x + w - GRIP - 1, y + h - GRIP - 1, x + w + GRIP, y + h + GRIP, 0xFF0B1417.toInt())
                        ctx.fill(x + w - GRIP, y + h - GRIP, x + w + GRIP - 1, y + h + GRIP - 1, ACCENT_HOVER)
                    }
                }
                e.locked() -> outline(ctx, x - 1, y - 1, w + 2, h + 2, 0x55888888)
                e === hover -> outline(ctx, x - 1, y - 1, w + 2, h + 2, 0xAA00CCEE.toInt())
            }
        }

        guideX?.let { ctx.fill(it, 0, it + 1, this.height, GUIDE) }
        guideY?.let { ctx.fill(0, it, this.width, it + 1, GUIDE) }

        if (showControls) {
            var ly = this.height - 6 - CONTROLS.size * 10
            for (line in CONTROLS) {
                ctx.text(this.font, line, 6, ly, 0xFFFFFFFF.toInt(), true)
                ly += 10
            }
        } else {
            ctx.text(this.font, "F1 to Show Controls", 6, this.height - 16, 0xFFAAAAAA.toInt(), true)
        }

        val by = btnY()
        val dHov = inRect(mouseX, mouseY, doneX(), by, BTN_W, BTN_H)
        ctx.fill(doneX(), by, doneX() + BTN_W, by + BTN_H, if (dHov) ACCENT_HOVER else ACCENT)
        ctx.centeredText(this.font, "Done", doneX() + BTN_W / 2, by + (BTN_H - 8) / 2, 0xFFFFFFFF.toInt())

        val rHov = inRect(mouseX, mouseY, resetX(), by, BTN_W, BTN_H)
        val rFill = if (resetArmed) 0xFFAA3333.toInt() else if (rHov) 0xFF553333.toInt() else 0xFF442222.toInt()
        ctx.fill(resetX(), by, resetX() + BTN_W, by + BTN_H, rFill)
        ctx.centeredText(
            this.font, if (resetArmed) "§fSure?" else "Reset shown",
            resetX() + BTN_W / 2, by + (BTN_H - 8) / 2, 0xFFFFCCCC.toInt()
        )

        renderSidebar(ctx, mouseX, mouseY)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun renderSidebar(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!sideOpen) {
            val hy = handleY()
            ctx.fill(0, hy, HANDLE_W, hy + HANDLE_H, SIDE_BG)
            ctx.fill(HANDLE_W - 1, hy, HANDLE_W, hy + HANDLE_H, ACCENT)
            ctx.text(this.font, "›", 3, hy + HANDLE_H / 2 - 4, 0xFFFFFFFF.toInt(), false)
            return
        }
        sideScroll = Math.max(0, Math.min(sideMaxScroll(), sideScroll))
        ctx.fill(0, 0, SIDE_W, this.height, SIDE_BG)
        ctx.fill(SIDE_W - 1, 0, SIDE_W, this.height, ACCENT)
        ctx.text(this.font, "Pick HUDs to move", 8, 8, 0xFFFFFFFF.toInt(), true)
        ctx.text(this.font, "§8Shift-click to add more", 8, SIDE_TOP - 9, 0xFFFFFFFF.toInt(), false)

        ctx.enableScissor(0, SIDE_TOP, SIDE_W - 1, this.height)
        val hovRow = if (mouseX < SIDE_W) rowAt(mouseY) else null
        var y = SIDE_TOP - sideScroll + 2
        for (row in sidebarRows()) {
            if (y + SIDE_ROW >= SIDE_TOP && y < this.height) {
                when (row) {
                    is Row.Header -> ctx.text(this.font, "§3" + row.text.uppercase(), 8, y + 3, 0xFFFFFFFF.toInt(), false)
                    is Row.Action -> {
                        if (row === hovRow) ctx.fill(0, y, SIDE_W - 1, y + SIDE_ROW, ROW_HOV)
                        ctx.text(this.font, "§7" + row.text, 8, y + 2, 0xFFFFFFFF.toInt(), false)
                    }
                    is Row.Hud -> {
                        val on = row.e.name() in picked
                        if (on) ctx.fill(0, y, SIDE_W - 1, y + SIDE_ROW, ROW_PICKED)
                        if (row === hovRow) ctx.fill(0, y, SIDE_W - 1, y + SIDE_ROW, ROW_HOV)
                        if (on) ctx.fill(0, y, 2, y + SIDE_ROW, ACCENT)
                        val name = if (this.font.width(row.e.name()) > SIDE_W - 40) this.font.plainSubstrByWidth(row.e.name(), SIDE_W - 46) + "…" else row.e.name()
                        val off = !row.e.isVisible()
                        val col = if (off) 0xFF666666.toInt() else if (on) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()
                        ctx.text(this.font, name, 12, y + 2, col, false)
                        if (off) ctx.text(this.font, "§8off", SIDE_W - 22, y + 2, 0xFFFFFFFF.toInt(), false)
                    }
                }
            }
            y += SIDE_ROW
        }
        ctx.disableScissor()
    }

    // ---- input ---------------------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()

        if (sideOpen && mx <= SIDE_W) {
            when (val row = rowAt(my)) {
                is Row.Action -> row.run()
                is Row.Hud -> {
                    val shift = InputConstants.isKeyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                        InputConstants.isKeyDown(Minecraft.getInstance().window, GLFW.GLFW_KEY_RIGHT_SHIFT)
                    if (shift) {
                        if (!picked.remove(row.e.name())) { picked += row.e.name(); selected = row.e }
                        else if (selected === row.e) selected = null
                    } else pickOnly(row.e)
                }
                else -> {}
            }
            return true
        }

        val by = btnY()
        if (inRect(mx, my, doneX(), by, BTN_W, BTN_H)) { this.onClose(); return true }
        if (inRect(mx, my, resetX(), by, BTN_W, BTN_H)) {
            if (resetArmed) { edit { shown().forEach { resetEntry(it) } }; resetArmed = false } else resetArmed = true
            return true
        }
        resetArmed = false

        val sel = selected
        if (sel != null && onGrip(sel, mx, my)) {
            resizing = sel
            dragBefore = snapshot()
            return true
        }

        val hit = boxUnder(mx, my)
        if (hit == null) { selected = null; return super.mouseClicked(click, bl) }
        selected = hit
        if (!hit.locked()) {
            dragging = hit
            dragBefore = snapshot()
            dragOffX = mx - hit.getX().asInt
            dragOffY = my - hit.getY().asInt
        }
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        resizing?.let { e ->
            val x = e.getX().asInt
            val y = e.getY().asInt
            val fit = Math.min((this.width - x).toDouble() / baseW(e), (this.height - y).toDouble() / baseH(e))
            val s = Math.round((mx - x).toDouble() / baseW(e) * 20.0) / 20.0
            e.setScale()?.accept(Math.max(MIN_SCALE, Math.min(Math.min(MAX_SCALE, fit), s)))
            // HUDComponent positions are stored scaled, so pin the top-left after a scale change.
            e.setX().accept(x)
            e.setY().accept(y)
            return true
        }
        dragging?.let { e ->
            var nx = Math.max(0, Math.min(this.width - sw(e), mx - dragOffX))
            var ny = Math.max(0, Math.min(this.height - sh(e), my - dragOffY))
            if (altDown()) {
                guideX = null
                guideY = null
            } else {
                val p = snap(e, nx, ny)
                nx = p.first
                ny = p.second
            }
            moveTo(e, nx, ny)
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        dragBefore?.let { pushUndo(it) }
        dragBefore = null
        dragging = null
        resizing = null
        guideX = null
        guideY = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (sideOpen && mouseX <= SIDE_W) {
            sideScroll = Math.max(0, Math.min(sideMaxScroll(), sideScroll - (vertical * SIDE_ROW * 2).toInt()))
            return true
        }
        val e = boxUnder(mouseX.toInt(), mouseY.toInt())
        val setScale = e?.setScale()
        if (e == null || e.locked() || setScale == null) {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        }
        selected = e
        val x = e.getX().asInt
        val y = e.getY().asInt
        val step = if (vertical > 0) 0.05 else -0.05
        edit {
            setScale.accept(Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round((e.scale() + step) * 100.0) / 100.0)))
            moveTo(e, x, y)
        }
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val mods = input.modifiers()
        val ctrl = mods and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SUPER) != 0
        val shift = mods and GLFW.GLFW_MOD_SHIFT != 0
        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE -> { cancel(); return true }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { this.onClose(); return true }
            GLFW.GLFW_KEY_F1 -> { showControls = !showControls; return true }
            GLFW.GLFW_KEY_G -> if (!ctrl) { showGrid = !showGrid; return true }
            GLFW.GLFW_KEY_Z -> if (ctrl) { if (shift) redo() else undo(); return true }
            GLFW.GLFW_KEY_Y -> if (ctrl) { redo(); return true }
            GLFW.GLFW_KEY_TAB -> { swap(if (shift) -1 else 1); return true }
        }

        val e = selected
        if (e != null && !e.locked()) {
            when (input.key()) {
                GLFW.GLFW_KEY_H -> if (ctrl) { edit { moveTo(e, (this.width - sw(e)) / 2, e.getY().asInt) }; return true }
                GLFW.GLFW_KEY_V -> if (ctrl) { edit { moveTo(e, e.getX().asInt, (this.height - sh(e)) / 2) }; return true }
                GLFW.GLFW_KEY_R -> if (!ctrl) { edit { resetEntry(e) }; return true }
            }
            val step = if (shift) 10 else 1
            val dx = when (input.key()) { GLFW.GLFW_KEY_LEFT -> -step; GLFW.GLFW_KEY_RIGHT -> step; else -> 0 }
            val dy = when (input.key()) { GLFW.GLFW_KEY_UP -> -step; GLFW.GLFW_KEY_DOWN -> step; else -> 0 }
            if (dx != 0 || dy != 0) {
                edit { moveTo(e, e.getX().asInt + dx, e.getY().asInt + dy) }
                return true
            }
        }
        return super.keyPressed(input)
    }

    // With several HUDs picked, Tab cycles between them; with one or none it swaps to the next HUD in the list.
    private fun swap(dir: Int) {
        val movable = shown().filter { !it.locked() }
        if (movable.size > 1) {
            val i = movable.indexOf(selected)
            selected = movable[if (i < 0) 0 else Math.floorMod(i + dir, movable.size)]
            return
        }
        val all = listed()
        if (all.isEmpty()) return
        val i = all.indexOfFirst { it.name() in picked }
        pickOnly(all[if (i < 0) 0 else Math.floorMod(i + dir, all.size)])
    }

    private fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(s)
    }

    private fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(s)
    }

    private fun cancel() {
        restore(opened)
        FishConfig.manager.save()
        Minecraft.getInstance().setScreen(parent)
    }

    override fun onClose() {
        FishConfig.manager.save()
        Minecraft.getInstance().setScreen(parent)
    }
}
