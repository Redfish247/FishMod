package fishmod.features
import fishmod.compat.curScreen
import fishmod.compat.showScreen

import fishmod.utils.debug.FishDiag
import com.mojang.blaze3d.platform.InputConstants
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.features.chat.ChatRuleStore
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import fishmod.compat.GLFW
import java.util.function.BooleanSupplier
import java.util.function.DoubleConsumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier

class FishHudEditor(private val parent: Screen) : Screen(Component.literal("Edit HUD")), HasUiOverlay {

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
        private val visibleVal: BooleanSupplier? = null,
        private val enabledVal: BooleanSupplier? = null
    ) {
        fun name(): String = nameVal
        fun getX(): IntSupplier = getXVal
        fun setX(): IntConsumer = setXVal
        fun getY(): IntSupplier = getYVal
        fun setY(): IntConsumer = setYVal
        fun w(): Int = wVal
        fun h(): Int = hVal
        fun locked(): Boolean = lockedVal
        fun setScale(): DoubleConsumer? = setScaleVal

        fun scale(): Double = if (getScaleVal != null) getScaleVal.asDouble else 1.0
        fun isVisible(): Boolean = visibleVal == null || visibleVal.asBoolean
        /** Setting-level on/off for the list pill (not "showing right now"). */
        fun isEnabledInList(): Boolean =
            enabledVal?.asBoolean ?: FeatureMasters.map[nameVal]?.invoke() ?: isVisible()
    }

    companion object {
        private val ENTRIES: MutableList<HudEntry> = ArrayList()

        private fun addEntry(e: HudEntry) {
            if (ENTRIES.any { it.name() == e.name() }) FishDiag.fail("FishHudEditor.1", "HUD '${e.name()}' registered twice (entries=${ENTRIES.size})")
            ENTRIES.add(e)
        }

        /** Draws a HUD's example at its configured spot for the config screen's shift preview; false if it has none. */
        @JvmStatic
        fun drawPreview(ctx: GuiGraphicsExtractor, name: String): Boolean {
            val e = ENTRIES.firstOrNull { it.name() == name } ?: return false
            val ex = HudPreview.example(name) ?: return false
            val sc = e.scale().toFloat()
            val pose = ctx.pose()
            pose.pushMatrix()
            try {
                pose.translate(e.getX().asInt.toFloat(), e.getY().asInt.toFloat())
                pose.scale(sc, sc)
                HudPreview.draw(ex, ctx, name)
            } finally {
                pose.popMatrix()
            }
            return true
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int
        ) {
            addEntry(HudEntry(name, getX, setX, getY, setY, w, h))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            getScale: DoubleSupplier, setScale: DoubleConsumer
        ) {
            addEntry(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, null))
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
            addEntry(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, visible))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            getScale: DoubleSupplier, setScale: DoubleConsumer,
            visible: BooleanSupplier,
            enabled: BooleanSupplier?
        ) {
            addEntry(HudEntry(name, getX, setX, getY, setY, w, h, false, getScale, setScale, visible, enabled))
        }

        @JvmStatic
        fun register(
            name: String,
            getX: IntSupplier, setX: IntConsumer,
            getY: IntSupplier, setY: IntConsumer,
            w: Int, h: Int,
            visible: BooleanSupplier
        ) {
            addEntry(HudEntry(name, getX, setX, getY, setY, w, h, false, null, null, visible))
        }

        @JvmStatic
        @JvmOverloads
        fun registerLocked(name: String, getX: IntSupplier, getY: IntSupplier, w: Int, h: Int, enabled: BooleanSupplier? = null) {
            addEntry(HudEntry(name, getX, IntConsumer { }, getY, IntConsumer { }, w, h, true, null, null, null, enabled))
        }

        @JvmStatic
        fun register(name: String, component: HUDComponent) {
            addEntry(
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
            java.util.Map.entry("Boss Tick Clock", doubleArrayOf(10.0, 80.0, 1.0)),
            java.util.Map.entry("Wither Dragon Timer", doubleArrayOf(-1.0, 100.0, 2.0)),
            java.util.Map.entry("Crystal Respawn", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("Crystal Not Placed", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Storm Kill Time", doubleArrayOf(10.0, 92.0, 1.0)),
            java.util.Map.entry("LB Release Timer", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Py Tick Timer", doubleArrayOf(10.0, 116.0, 1.0)),
            java.util.Map.entry("Necron LB Timer", doubleArrayOf(10.0, 128.0, 1.0)),
            java.util.Map.entry("Storm Crush Banner", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Pillar Blast Timer", doubleArrayOf(10.0, 140.0, 1.0)),
            java.util.Map.entry("Terminals Open In", doubleArrayOf(10.0, 104.0, 1.0)),
            java.util.Map.entry("Section Count", doubleArrayOf(10.0, 116.0, 1.0)),
            java.util.Map.entry("P3 Section", doubleArrayOf(10.0, 190.0, 1.0)),
            java.util.Map.entry("Device Done", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Melody Callout", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Section Cleared", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Players Leaped", doubleArrayOf(10.0, 90.0, 1.0)),
            java.util.Map.entry("Relic Countdown", doubleArrayOf(10.0, 180.0, 1.0)),
            java.util.Map.entry("Goldor Splits", doubleArrayOf(10.0, 128.0, 1.0)),
            java.util.Map.entry("Run Splits", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Puzzles", doubleArrayOf(0.0, 0.0, 1.0)),
            java.util.Map.entry("Party Finder List", doubleArrayOf(6.0, 45.0, 1.0)),
            java.util.Map.entry("Dungeon Map", doubleArrayOf(100.0, 100.0, 1.0)),
            java.util.Map.entry("Slayer Spawn", doubleArrayOf(10.0, 140.0, 1.0)),
            java.util.Map.entry("Slayer Stats", doubleArrayOf(10.0, 170.0, 1.0)),
            java.util.Map.entry("Slayer Carries", doubleArrayOf(10.0, 120.0, 1.0)),
            java.util.Map.entry("Experiment Profit", doubleArrayOf(10.0, 60.0, 1.0)),
            java.util.Map.entry("Slayer Boss Timer", doubleArrayOf(10.0, 255.0, 1.0)),
            java.util.Map.entry("Slayer Profit", doubleArrayOf(240.0, 90.0, 1.0))
        )

        private val RESETTERS: Map<String, Runnable> = mapOf(
            "Mask & Phoenix Timer" to Runnable { FishSettings.invincHudX = 10; FishSettings.invincHudY = 140; FishSettings.invincScale = 1.0 },
            "Dungeon Breaker" to Runnable { FishSettings.dungeonBreakerHudX = 10; FishSettings.dungeonBreakerHudY = 80; FishSettings.dungeonBreakerHudScale = 1.0 },
            "Secret Overlay" to Runnable { FishSettings.secretOverlayX = 10; FishSettings.secretOverlayY = 180; FishSettings.secretOverlayScale = 1.5 },
            "Quiz Timer" to Runnable { FishSettings.quizHudX = 10; FishSettings.quizHudY = 200; FishSettings.quizHudScale = 1.5 },
            "Dungeon Map Info" to Runnable { DungeonMapSettings.mapInfoX = 100f; DungeonMapSettings.mapInfoY = 100f; DungeonMapSettings.mapInfoScale = 1f },
            "Dungeon Score Title" to Runnable { DungeonMapSettings.mapScoreTitleX = -1f; DungeonMapSettings.mapScoreTitleY = -1f; DungeonMapSettings.mapScoreTitleScale = 1.5f },
            "Storm Over Alert" to Runnable { FishSettings.stormOverHudX = 200; FishSettings.stormOverHudY = 100; FishSettings.stormOverScale = 2.5 },
            "Veno Stack Count" to Runnable { FishSettings.venoStackHudX = 200; FishSettings.venoStackHudY = 140; FishSettings.venoStackScale = 1.5 },
            "Custom Scoreboard" to Runnable { FishSettings.customScoreboardHudX = -1; FishSettings.customScoreboardHudY = 2 },
            "Performance" to Runnable { FishSettings.perfHudX = 10; FishSettings.perfHudY = 60; FishSettings.perfHudScale = 1.0 },
            "Chat Alerts" to Runnable { ChatRuleStore.setHudX(10); ChatRuleStore.setHudY(400); ChatRuleStore.setHudScale(1.0) },
            "Party Warp Timer" to Runnable { FishSettings.warpCooldownHudX = 10; FishSettings.warpCooldownHudY = 160; FishSettings.warpCooldownScale = 1.0 },
            "Tac Timer" to Runnable { FishSettings.tacTimerHudX = 10; FishSettings.tacTimerHudY = 180; FishSettings.tacTimerScale = 1.0 },
            "Rag Timer" to Runnable { FishSettings.ragnarockTimerHudX = 10; FishSettings.ragnarockTimerHudY = 150; FishSettings.ragnarockTimerScale = 1.5 },
            "Spring Boots" to Runnable { FishSettings.springBootsHudX = 10; FishSettings.springBootsHudY = 200; FishSettings.springBootsScale = 1.0 },
        )

        private val GROUPS: List<Pair<String, List<String>>> = listOf(
            "Dungeons" to listOf(
                "Run Splits", "Session Stats", "PB Pace", "Blessings", "Puzzles", "Simon Says",
                "Spirit Bear", "Mask & Phoenix Timer", "Dungeon Breaker", "Secret Overlay", "Quiz Timer",
            ),
            "Dungeon Map" to listOf("Dungeon Map", "Dungeon Map Info", "Dungeon Score Title"),
            "Floor 7" to listOf(
                "Boss Tick Clock", "Wither Dragon Timer", "Crystal Respawn", "Crystal Not Placed",
                "Storm Kill Time", "LB Release Timer", "Py Tick Timer", "Necron LB Timer", "Storm Crush Banner", "Pillar Blast Timer", "Terminals Open In",
                "Section Count", "Goldor Splits", "Terminal Tracker", "P3 Section", "Device Done",
                "Melody Callout", "Section Cleared", "Storm Over Alert", "Veno Stack Count", "Players Leaped",
                "Relic Countdown", "Necron Drag Timer", "Necron Drop Lava/Platform",
            ),
            "HUD & Overlays" to listOf(
                "Custom Scoreboard", "Performance", "Pet", "Soulflow", "Chat Alerts", "Party Warp Timer", "Tac Timer", "Rag Timer", "Spring Boots", "Experiment Profit",
                "Spotify", "Stopwatch", "Achievement Popup", "Arrow HUD",
            ),
            "Party & Social" to listOf("Party Finder List"),
            "Slayer" to listOf("Slayer Spawn", "Slayer Stats", "Slayer Boss Timer", "Slayer Profit", "Slayer Carries", "Slayer RNG Meter"),
            "Diana" to listOf("Diana Loot Tracker", "Diana Mob Tracker", "Diana Stats", "Diana Magic Find", "Diana Mythos HP", "Diana No Shuriken", "Diana Rare Mob Alert", "Diana Warp Title", "Crown of Avarice"),
            "Mining" to listOf("Mineshaft Pity", "Fossil Solver", "Mining Profit", "Pickobulus", "Maniac Miner", "SkyMall", "Commissions"),
            "Farming" to listOf(
                "Visitor Timer", "Visitor Stats", "Charmed Visitor", "Pest Timer", "Farming Profit", "Composter", "Jacob's Contest", "Rare Crops",
                "Rare Drop Alert", "Garden Titles", "Crop Milestone", "Garden Level", "Hoe Level", "Jeff Buy Order", "Greenhouse", "Stereo Harmony",
                "Log Book", "Sensitivity", "Mooshroom Cow Perk",
            ),
            "Fishing" to listOf("Fishing Sea Creatures", "Fishing Profit", "Fishing Efficiency", "Barn Fishing Timer", "Fishing Festival", "Fishing Area Tracker",
                "Fishing Creature Alert", "Fishing Rare Drop Alert", "Fishing Hotspot Alert", "Fishing Wormhole Alert", "Fishing Trophy Alert", "Fishing Deployable Alert", "Fishing Bait Alert"),
            "Rift" to listOf("Rift Guide"),
            "Kuudra" to listOf("Kuudra Backbone", "Kuudra Build", "Kuudra Chest Counter", "Kuudra Chest Value", "Kuudra Fresh Timers", "Kuudra Health", "Kuudra Profit", "Kuudra Splits", "Kuudra Supplies"),
        )

        const val SCOREBOARD_W = 118
        const val SCORE_TITLE_W = 200

        @JvmStatic
        fun isOpen(): Boolean = Minecraft.getInstance().curScreen is FishHudEditor

        private const val SNAP = 4
        private const val MARGIN = 4
        private const val GRID = 10
        private const val GRIP = 3
        private const val MIN_SCALE = 0.5
        private const val MAX_SCALE = 3.0

        private const val BTN_W = 60
        private const val BTN_H = 18
        private const val BTN_GAP = 6

        private const val SIDE_W_MIN = 110
        private const val SIDE_W_MAX = 200
        private const val SIDE_ROW = 12
        private const val SIDE_TOP = 58
        private const val SEARCH_Y = 20
        private const val SEARCH_H = 14
        private const val HANDLE_W = 10
        private const val HANDLE_H = 44

        private val ACCENT get() = ScreenTheme.ACCENT
        private val ACCENT_HOVER get() = ScreenTheme.ACCENT_HOVER
        private val TEXT = ScreenTheme.TEXT_COLOR
        private val SUBTEXT = ScreenTheme.SUBTEXT_COLOR
        private val ON_ACCENT = 0xFF06302F.toInt()
        private val GUIDE = 0xFFFF4FB0.toInt()
        private val GRID_LINE = 0x14FFFFFF
        private val SIDE_BG = 0xFF101418.toInt()
        private val ROW_HOV = 0x1AFFFFFF
        private val ROW_PICKED = 0x2624B6B0
        private val HOVER_OUTLINE = 0xB33AD8D1.toInt()
        private val PILL_BG = 0xE00E1115.toInt()
        private val PILL_BORDER = 0xFF2B333C.toInt()
        private val PILL_TEXT = 0xFFCFD6DD.toInt()
        private val LABEL = 0xFFAEB8C2.toInt()
        private val FAINT = 0xFF5A636D.toInt()
        private val CAP_BG = 0xFF1B2027.toInt()
        private val CAP_BORDER = 0xFF3A3F48.toInt()
        private val OFF_BG = 0xFF262B33.toInt()

        private val CONTROLS = listOf(
            "Hover left edge" to "Pick HUDs",
            "Shift/Ctrl+click" to "Add/remove HUDs",
            "Drag" to "Move",
            "Scroll / corner" to "Resize",
            "Tab" to "Swap selected",
            "←↑→↓" to "Nudge (Shift 10px)",
            "Ctrl+H / V" to "Center",
            "Alt" to "No snapping",
            "R" to "Reset selected",
            "G" to "Grid",
            "Ctrl+Z / Y" to "Undo / redo",
            "Enter / Esc" to "Save & close",
            "F1" to "Hide controls",
        )

        private val FOLLOWS = Regex("\\(follows (.+)\\)")

        private var showControls = true
        private var showGrid = false
        private val picked = LinkedHashSet<String>()
    }

    private data class Pos(val x: Int, val y: Int, val scale: Double)

    private sealed class Row {
        class Header(val text: String, val items: List<HudEntry>) : Row()
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

    private var k = 1f
    private fun refreshK() { k = UiScale.userScale(this) }
    private fun toC(raw: Number) = (raw.toDouble() / k).toInt()
    private fun cW() = (this.width / k).toInt()
    private fun cH() = (this.height / k).toInt()

    private var sideWFor = -1
    private var sideWCache = SIDE_W_MIN

    private val sideW: Int
        get() {
            if (sideWFor == ENTRIES.size) return sideWCache
            val offW = UiRecorder.textWidth("off", 5.5f) + 6f
            val pickW = UiRecorder.textWidth("pick all", 5.5f)
            var w = 8f + UiRecorder.textWidth("Pick HUDs to move", 7.5f) + 1f
            w = Math.max(w, 8f + UiRecorder.textWidth("Click a category to pick it all", 6f))
            w = Math.max(w, 8f + UiRecorder.textWidth("Shift/Ctrl-click to add more", 6f))
            for (a in listOf("Show all matches", "Hide all")) w = Math.max(w, 8f + UiRecorder.textWidth(a, 6.5f))
            for (g in GROUPS.map { it.first } + listOf("Other", "No HUDs match")) {
                w = Math.max(w, 8f + UiRecorder.textWidth(g.uppercase(), 6f) + 1f + 6f + pickW)
            }
            for (e in listed()) w = Math.max(w, 12f + UiRecorder.textWidth(e.name(), 6.5f) + 4f + offW)
            sideWCache = Math.max(SIDE_W_MIN, Math.min(SIDE_W_MAX, Math.ceil(w + 10.0).toInt()))
            sideWFor = ENTRIES.size
            return sideWCache
        }

    private val search = EditBox(Minecraft.getInstance().font, 0, 0, 0, 0, Component.empty()).apply { setMaxLength(32) }
    private var searchFocused = false

    private fun focusSearch(on: Boolean) {
        searchFocused = on
        search.isFocused = on
    }

    private val undoStack = ArrayDeque<List<Pos>>()
    private val redoStack = ArrayDeque<List<Pos>>()

    init {
        picked.retainAll(ENTRIES.map { it.name() }.toSet())
    }

    override fun isPauseScreen(): Boolean = false

    private fun available(): List<HudEntry> = ENTRIES

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

    private var cachedRows: List<Row> = emptyList()
    private var cachedRowsQuery: String? = null
    private var cachedRowsAt = 0L

    private fun sidebarRows(): List<Row> {
        val q = search.value.trim().lowercase()
        val now = System.currentTimeMillis()
        if (q == cachedRowsQuery && now - cachedRowsAt < 100L) return cachedRows
        return buildSidebarRows(q).also {
            cachedRows = it
            cachedRowsQuery = q
            cachedRowsAt = now
        }
    }

    private fun buildSidebarRows(q: String): List<Row> {
        val body = ArrayList<Row>()
        val avail = listed()
        val grouped = HashSet<HudEntry>()
        fun addGroup(g: String, items: List<HudEntry>) {
            val hit = if (q.isEmpty() || g.lowercase().contains(q)) items else items.filter { it.name().lowercase().contains(q) }
            if (hit.isEmpty()) return
            body += Row.Header(g, hit)
            hit.forEach { body += Row.Hud(it) }
        }
        for ((g, names) in GROUPS) {
            val items = names.mapNotNull { n -> avail.firstOrNull { it.name() == n && it !in grouped } }
            grouped += items
            addGroup(g, items)
        }
        addGroup("Other", avail.filter { it !in grouped })

        val matches = body.filterIsInstance<Row.Hud>().map { it.e }
        val rows = ArrayList<Row>()
        if (matches.isNotEmpty()) {
            rows += Row.Action(if (q.isEmpty()) "Show all" else "Show all matches") {
                picked.clear(); matches.forEach { picked += it.name() }
            }
        }
        rows += Row.Action("Hide all") { picked.clear(); selected = null }
        if (matches.isEmpty()) rows += Row.Header("No HUDs match", emptyList())
        return rows + body
    }

    private fun pickGroup(items: List<HudEntry>, add: Boolean) {
        if (items.isEmpty()) return
        if (!add) picked.clear()
        items.forEach { picked += it.name() }
        selected = items.first()
    }

    // Shift or Ctrl held = add/remove from the pick instead of replacing it
    private fun shiftDown(): Boolean {
        val w = Minecraft.getInstance().window
        return fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT) || fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT) ||
            fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL) || fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL)
    }

    // Examples are rebuilt at most every 200ms (hit-tests ask for sizes many times a frame)
    private class CachedExample(val at: Long, val ex: HudExample?)
    private val exampleCache = HashMap<String, CachedExample>()

    private fun example(e: HudEntry): HudExample? {
        if (!HudPreview.has(e.name())) return null
        val now = System.currentTimeMillis()
        exampleCache[e.name()]?.let { if (now - it.at < 200L) return it.ex }
        val ex = HudPreview.example(e.name())
        exampleCache[e.name()] = CachedExample(now, ex)
        return ex
    }

    private fun baseW(e: HudEntry): Int = example(e)?.w ?: e.w()

    private fun baseH(e: HudEntry): Int = example(e)?.h ?: e.h()

    private fun sw(e: HudEntry) = Math.max(4, (baseW(e) * e.scale()).toInt())
    private fun sh(e: HudEntry) = Math.max(4, (baseH(e) * e.scale()).toInt())

    private fun snapshot(): List<Pos> = ENTRIES.map { Pos(it.getX().asInt, it.getY().asInt, it.scale()) }

    private fun restore(s: List<Pos>) {
        FishDiag.check(s.size == ENTRIES.size, "FishHudEditor.2") { "undo snapshot size ${s.size} != entries ${ENTRIES.size}" }
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
        RESETTERS[e.name()]?.let { it.run(); return }
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
        return fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || fishmod.compat.GLFW.keyDown(w, GLFW.GLFW_KEY_RIGHT_ALT)
    }

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

    private fun doneX() = cW() / 2 - BTN_W - BTN_GAP / 2
    private fun resetX() = cW() / 2 + BTN_GAP / 2
    private fun btnY() = cH() - 28

    private fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx in x..(x + w) && my in y..(y + h)

    private fun handleY(): Int {
        val rows = if (showControls) CONTROLS.size else 1
        val cardTop = cH() - 6 - (10 + rows * 10 - 2)
        return Math.min((cH() - HANDLE_H) / 2, cardTop - HANDLE_H - 6)
    }

    private fun sideMaxScroll(): Int = Math.max(0, sidebarRows().size * SIDE_ROW - (cH() - SIDE_TOP - 6))

    private fun updateSide(mx: Int, my: Int) {
        if (dragging != null || resizing != null) { sideOpen = false; return }
        if (searchFocused) { sideOpen = true; return }
        sideOpen = if (sideOpen) mx <= sideW else mx <= HANDLE_W && my in handleY()..(handleY() + HANDLE_H)
    }

    private fun onSearch(mx: Int, my: Int) = mx in 6..(sideW - 7) && my in SEARCH_Y..(SEARCH_Y + SEARCH_H)

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

    private fun outline(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, c: Int) {
        ctx.fill(x, y, x + w, y + 1, c)
        ctx.fill(x, y + h - 1, x + w, y + h, c)
        ctx.fill(x, y, x + 1, y + h, c)
        ctx.fill(x + w - 1, y, x + w, y + h, c)
    }

    private fun drawExample(ctx: GuiGraphicsExtractor, e: HudEntry, x: Int, y: Int) {
        val sc = e.scale().toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        try {
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(sc, sc)
        val ex = example(e)
        if (ex != null) {
            HudPreview.draw(ex, ctx, e.name())
        } else {
            ctx.fill(0, 0, e.w(), e.h(), 0x40000000)
            ctx.centeredText(this.font, "§7" + e.name(), e.w() / 2, (e.h() - 8) / 2, 0xFFFFFFFF.toInt())
        }
        } finally {
            pose.popMatrix()
        }
    }

    private fun ellipsize(s: String, maxW: Float, size: Float): String {
        if (UiRecorder.textWidth(s, size) <= maxW) return s
        val n = fishmod.utils.rendering.TextFit.prefixLength(s, "…", maxW, 0) { UiRecorder.textWidth(it, size) }
        return s.substring(0, n) + "…"
    }

    private fun centerText(s: String, cx: Float, y: Float, size: Float, color: Int) {
        UiRecorder.textBold(s, cx - UiRecorder.textWidthBold(s, size) / 2f, y, size, color)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        try {
            renderEditor(ctx, mouseX, mouseY, delta)
        } catch (t: Throwable) {
            FishDiag.fail("FishHudEditor.3", "HUD editor render failed selected=${selected?.name()} shown=${picked.size}", t)
        }
    }

    private fun renderEditor(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        refreshK()
        val cmx = toC(mouseX)
        val cmy = toC(mouseY)
        updateSide(cmx, cmy)
        UiRecorder.clear()
        ctx.fill(0, 0, this.width, this.height, 0x60000000)

        if (showGrid) {
            var gx = GRID
            while (gx < this.width) { ctx.fill(gx, 0, gx + 1, this.height, GRID_LINE); gx += GRID }
            var gy = GRID
            while (gy < this.height) { ctx.fill(0, gy, this.width, gy + 1, GRID_LINE); gy += GRID }
        }

        val sel = selected
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
                    val ts = 6.5f
                    val ty = if (y - 2 - 10f * k >= 1f) (y - 2) / k - 10f else (y + h + 2) / k
                    val tx = (x - 1) / k
                    UiRecorder.fillRoundedRect(tx, ty, UiRecorder.textWidthBold(tag, ts) + 7f, 10f, 3f, ACCENT)
                    UiRecorder.textBold(tag, tx + 3.5f, ty + (10f - ts) / 2f, ts, ON_ACCENT)
                    if (e.setScale() != null && !e.locked()) {
                        UiRecorder.roundedRectRing(
                            (x + w - GRIP) / k, (y + h - GRIP) / k, GRIP * 2f / k, GRIP * 2f / k,
                            2f / k, 1f / k, ACCENT_HOVER, 0xFF0B1417.toInt()
                        )
                    }
                }
                e.locked() -> outline(ctx, x - 1, y - 1, w + 2, h + 2, 0x55888888)
                e === hover -> outline(ctx, x - 1, y - 1, w + 2, h + 2, HOVER_OUTLINE)
            }
        }

        guideX?.let { ctx.fill(it, 0, it + 1, this.height, GUIDE) }
        guideY?.let { ctx.fill(0, it, this.width, it + 1, GUIDE) }

        drawReadout(sel)
        if (!sideOpen) drawControls()
        drawButtons(cmx, cmy)
        renderSidebar(cmx, cmy)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun drawReadout(sel: HudEntry?) {
        val ts = 6.5f
        val lead = sel?.name() ?: if (picked.isEmpty()) "Hover the left edge to pick a HUD to move" else "Click a HUD to select it"
        val rest = if (sel != null) "  ·  ${Math.round(sel.scale() * 100)}%  ·  x ${sel.getX().asInt}, y ${sel.getY().asInt}" else ""
        val lw = if (sel != null) UiRecorder.textWidthBold(lead, ts) else UiRecorder.textWidth(lead, ts)
        val pw = lw + UiRecorder.textWidth(rest, ts) + 18f
        val ph = 13f
        val px = (cW() - pw) / 2f
        val py = 6f
        UiRecorder.roundedRectRing(px, py, pw, ph, ph / 2f, 1f, PILL_BG, PILL_BORDER)
        val ty = py + (ph - ts) / 2f
        if (sel != null) {
            UiRecorder.textBold(lead, px + 9f, ty, ts, ACCENT_HOVER)
            UiRecorder.text(rest, px + 9f + lw, ty, ts, PILL_TEXT)
        } else {
            UiRecorder.text(lead, px + 9f, ty, ts, PILL_TEXT)
        }
    }

    private fun drawControls() {
        val rows = if (showControls) CONTROLS else listOf("F1" to "Show controls")
        val ts = 6f
        val ks = 5.5f
        val rowH = 10f
        val capH = rowH - 2f
        val pad = 5f
        val capCol = rows.maxOf { UiRecorder.textWidth(it.first, ks) } + 6f
        val cw = pad * 2 + capCol + 5f + rows.maxOf { UiRecorder.textWidth(it.second, ts) }
        val ch = pad * 2 + rows.size * rowH - 2f
        val cx = 6f
        val cy = cH() - 6f - ch
        UiRecorder.roundedRectRing(cx, cy, cw, ch, 5f, 1f, PILL_BG, PILL_BORDER)
        var y = cy + pad
        for ((k, t) in rows) {
            UiRecorder.roundedRectRing(cx + pad, y, UiRecorder.textWidth(k, ks) + 6f, capH, 2f, 1f, CAP_BG, CAP_BORDER)
            UiRecorder.text(k, cx + pad + 3f, y + (capH - ks) / 2f, ks, TEXT)
            UiRecorder.text(t, cx + pad + capCol + 5f, y + (capH - ts) / 2f, ts, LABEL)
            y += rowH
        }
    }

    private fun drawButtons(mouseX: Int, mouseY: Int) {
        val ts = 7f
        val by = btnY().toFloat()
        val bw = BTN_W.toFloat()
        val bh = BTN_H.toFloat()
        val ty = by + (bh - ts) / 2f

        val dx = doneX().toFloat()
        val dHov = inRect(mouseX, mouseY, doneX(), btnY(), BTN_W, BTN_H)
        UiRecorder.fillPillBar(dx, by, bw, bh, if (dHov) ACCENT_HOVER else ACCENT)
        centerText("Done", dx + bw / 2f, ty, ts, ON_ACCENT)

        val rx = resetX().toFloat()
        val rHov = inRect(mouseX, mouseY, resetX(), btnY(), BTN_W, BTN_H)
        if (resetArmed) {
            UiRecorder.fillPillBar(rx, by, bw, bh, if (rHov) ScreenTheme.DANGER_HOVER else ScreenTheme.DANGER)
            centerText("Sure?", rx + bw / 2f, ty, ts, 0xFF2A0B0B.toInt())
        } else {
            UiRecorder.roundedRectRing(
                rx, by, bw, bh, bh / 2f, 1f, if (rHov) 0xF2231A1D.toInt() else 0xE614181D.toInt(),
                if (rHov) ScreenTheme.DANGER_HOVER else 0x80E05A5A.toInt()
            )
            centerText("Reset shown", rx + bw / 2f, ty, ts, if (rHov) ScreenTheme.DANGER_HOVER else ScreenTheme.DANGER)
        }
    }

    private fun rowBg(y: Float, color: Int) = UiRecorder.fillRoundedRect(3f, y + 0.5f, sideW - 8f, SIDE_ROW - 1f, 3f, color)

    private fun renderSidebar(mouseX: Int, mouseY: Int) {
        if (!sideOpen) {
            val hy = handleY().toFloat()
            UiRecorder.dropShadow(-6f, hy, HANDLE_W + 6f, HANDLE_H.toFloat(), 5f, 6f, 0x50000000)
            UiRecorder.roundedRectRing(-6f, hy, HANDLE_W + 6f, HANDLE_H.toFloat(), 5f, 1f, SIDE_BG, ACCENT)
            UiRecorder.chevron(2.5f, hy + HANDLE_H / 2f, false, ACCENT_HOVER)
            return
        }
        sideScroll = Math.max(0, Math.min(sideMaxScroll(), sideScroll))
        val sh = cH().toFloat()
        UiRecorder.dropShadow(-10f, 4f, sideW + 10f, sh - 8f, 8f, 18f, 0x73000000)
        UiRecorder.roundedRectRing(-10f, 4f, sideW + 10f, sh - 8f, 8f, 1f, SIDE_BG, ACCENT)
        UiRecorder.textBold("Pick HUDs to move", 8f, 9f, 7.5f, TEXT)

        val sw = sideW - 13
        val sHov = onSearch(mouseX, mouseY)
        val border = if (searchFocused) ACCENT else if (sHov) 0xFF5A606A.toInt() else CAP_BORDER
        UiRecorder.roundedRectRing(6f, SEARCH_Y.toFloat(), sw.toFloat(), SEARCH_H.toFloat(), 4f, 1f, 0xFF0B0F12.toInt(), border)
        if (search.value.isEmpty() && !searchFocused) {
            UiRecorder.text("Search HUDs…", 9f, SEARCH_Y + (SEARCH_H - 6.5f) / 2f, 6.5f, FAINT)
        } else {
            ScreenTheme.nTextFieldContent(search, searchFocused, 6, SEARCH_Y, sw, SEARCH_H, 6.5f)
        }
        UiRecorder.text("Click a category to pick it all", 8f, SEARCH_Y + SEARCH_H + 5f, 6f, FAINT)
        UiRecorder.text("Shift/Ctrl-click to add more", 8f, SEARCH_Y + SEARCH_H + 13f, 6f, FAINT)

        UiRecorder.pushScissor(0f, SIDE_TOP.toFloat(), sideW - 1f, sh - SIDE_TOP - 5f)
        val hovRow = if (mouseX < sideW) rowAt(mouseY) else null
        var y = SIDE_TOP - sideScroll
        for (row in sidebarRows()) {
            if (y + SIDE_ROW >= SIDE_TOP && y < cH()) {
                val yf = y.toFloat()
                val ty = yf + (SIDE_ROW - 6.5f) / 2f
                val hov = row === hovRow
                when (row) {
                    is Row.Header -> {
                        val clickable = row.items.isNotEmpty()
                        if (clickable && hov) {
                            rowBg(yf, ROW_HOV)
                            val tag = "pick all"
                            UiRecorder.text(tag, sideW - 9f - UiRecorder.textWidth(tag, 5.5f), yf + (SIDE_ROW - 5.5f) / 2f, 5.5f, SUBTEXT)
                        }
                        val all = clickable && row.items.all { it.name() in picked }
                        val c = if (!clickable) 0xFF555B63.toInt() else if (all) ACCENT_HOVER else ACCENT
                        UiRecorder.textBold(row.text.uppercase(), 8f, yf + (SIDE_ROW - 6f) / 2f, 6f, c)
                    }
                    is Row.Action -> {
                        if (hov) rowBg(yf, ROW_HOV)
                        UiRecorder.text(row.text, 8f, ty, 6.5f, if (hov) TEXT else SUBTEXT)
                    }
                    is Row.Hud -> {
                        val on = row.e.name() in picked
                        if (on) rowBg(yf, ROW_PICKED)
                        if (hov) rowBg(yf, ROW_HOV)
                        if (on) UiRecorder.fillRoundedRect(4f, yf + 2.5f, 2f, SIDE_ROW - 5f, 1f, ACCENT)
                        val off = !row.e.isEnabledInList()
                        val col = if (off) 0xFF666B72.toInt() else if (on) TEXT else LABEL
                        val tw = UiRecorder.textWidth("off", 5.5f)
                        val ox = sideW - 10f - tw - 6f
                        UiRecorder.text(ellipsize(row.e.name(), ox - 4f - 12f, 6.5f), 12f, ty, 6.5f, col)
                        if (off) {
                            UiRecorder.fillRoundedRect(ox, yf + 2.5f, tw + 6f, SIDE_ROW - 5f, 2.5f, OFF_BG)
                            UiRecorder.text("off", ox + 3f, yf + (SIDE_ROW - 5.5f) / 2f, 5.5f, SUBTEXT)
                        }
                    }
                }
            }
            y += SIDE_ROW
        }
        UiRecorder.popScissor()
    }

    override fun paintUiOverlay() {
        try {
            UiRenderer.paint(this.width, this.height, k)
        } catch (t: Throwable) {
            FishDiag.fail("FishHudEditor.4", "HUD editor UI overlay paint failed ${this.width}x${this.height} k=$k", t)
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean =
        try {
            clickInner(click, bl)
        } catch (t: Throwable) {
            FishDiag.fail("FishHudEditor.5", "HUD editor click failed at ${click.x()},${click.y()} button=${fishmod.compat.GLFW.btn(click)}", t)
            true
        }

    private fun clickInner(click: MouseButtonEvent, bl: Boolean): Boolean {
        refreshK()
        val mx = click.x().toInt()
        val my = click.y().toInt()
        val cmx = toC(click.x())
        val cmy = toC(click.y())

        if (sideOpen && cmx <= sideW) {
            if (onSearch(cmx, cmy)) { focusSearch(true); return true }
            focusSearch(false)
            when (val row = rowAt(cmy)) {
                is Row.Action -> row.run()
                is Row.Header -> pickGroup(row.items, shiftDown())
                is Row.Hud -> {
                    if (shiftDown()) {
                        if (!picked.remove(row.e.name())) { picked += row.e.name(); selected = row.e }
                        else if (selected === row.e) selected = null
                    } else pickOnly(row.e)
                }
                else -> {}
            }
            return true
        }

        if (searchFocused) { focusSearch(false); return true }

        val by = btnY()
        if (inRect(cmx, cmy, doneX(), by, BTN_W, BTN_H)) { this.onClose(); return true }
        if (inRect(cmx, cmy, resetX(), by, BTN_W, BTN_H)) {
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

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean =
        try {
            dragInner(click, deltaX, deltaY)
        } catch (t: Throwable) {
            FishDiag.fail("FishHudEditor.6", "HUD editor drag failed dragging=${dragging?.name()} resizing=${resizing?.name()}", t)
            true
        }

    private fun dragInner(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        resizing?.let { e ->
            val x = e.getX().asInt
            val y = e.getY().asInt
            val fit = Math.min((this.width - x).toDouble() / baseW(e), (this.height - y).toDouble() / baseH(e))
            val s = Math.round((mx - x).toDouble() / baseW(e) * 20.0) / 20.0
            e.setScale()?.accept(Math.max(MIN_SCALE, Math.min(Math.min(MAX_SCALE, fit), s)))
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean =
        try {
            scrollInner(mouseX, mouseY, horizontal, vertical)
        } catch (t: Throwable) {
            FishDiag.fail("FishHudEditor.7", "HUD editor scroll failed at $mouseX,$mouseY", t)
            true
        }

    private fun scrollInner(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        refreshK()
        if (sideOpen && mouseX / k <= sideW) {
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

    override fun keyPressed(input: KeyEvent): Boolean =
        try {
            keyInner(input)
        } catch (t: Throwable) {
            FishDiag.fail("FishHudEditor.8", "HUD editor key ${input.key()} failed selected=${selected?.name()}", t)
            true
        }

    private fun keyInner(input: KeyEvent): Boolean {
        val mods = input.modifiers()
        val ctrl = mods and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SUPER) != 0
        val shift = mods and GLFW.GLFW_MOD_SHIFT != 0
        val key = input.key()

        if (searchFocused) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> { if (search.value.isNotEmpty()) search.value = "" else focusSearch(false); return true }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    sidebarRows().firstNotNullOfOrNull { (it as? Row.Hud)?.e }?.let { pickOnly(it) }
                    focusSearch(false)
                    return true
                }
            }
            search.keyPressed(input)
            sideScroll = 0
            return true
        }
        if (sideOpen && !ctrl && (key in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z || key in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9)) {
            focusSearch(true)
            return true
        }

        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { this.onClose(); return true }
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

    override fun charTyped(input: CharacterEvent): Boolean {
        if (!searchFocused) return super.charTyped(input)
        search.charTyped(input)
        sideScroll = 0
        return true
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

    override fun onClose() {
        try { FishConfig.manager.save() } catch (t: Throwable) { FishDiag.fail("FishHudEditor.9", "config save on HUD editor close failed", t) }
        fishmod.features.achievements.AchUsage.onHudSaved()
        try { fishmod.features.chat.ChatRuleStore.save() } catch (t: Throwable) { FishDiag.fail("FishHudEditor.10", "chat rule save on HUD editor close failed", t) }
        Minecraft.getInstance().showScreen(parent)
    }
}
