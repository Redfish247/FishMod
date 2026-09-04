package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonPlayers
import fishmod.features.dungeon.map.DungeonState
import fishmod.features.dungeon.map.MapHud
import fishmod.features.dungeon.map.Scan
import fishmod.utils.Location
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

/**
 * Custom Spirit Leap menu. Replaces Hypixel's
 * "Spirit Leap" / "Teleport to Player" chest GUI with a 2×2 grid of class-coloured cells; click a
 * quadrant or press 1-4 to leap. Uses [DungeonPlayers] for class / skin / dead state.
 */
object LeapMenu {

    private val NAME_RX = Regex("(?:\\[.+?] )?(\\w{1,16})")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private data class Target(val slot: Int, val name: String, val clazz: DungeonClass?, val dead: Boolean)

    private var cache: List<Target> = emptyList()

    // 1:1 with Odin's DungeonUtils.getDungeonTeammates: scan the entire tab list for
    // "[184] Name [rank] (Class Level)" and keep each class sticky for the dungeon
    // instance. The boss tab-list format drops the "(Class L)" suffix, so without the
    // stickiness every teammate falls back to "?" once Necron starts.
    private val TABLIST_RX = Regex("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")
    private val teammateClasses = HashMap<String, DungeonClass>()
    private var lastLevel: Any? = null

    private fun refreshTeammateClasses() {
        val mc = Minecraft.getInstance()
        if (mc.level !== lastLevel) { lastLevel = mc.level; teammateClasses.clear() }
        val conn = mc.connection ?: return
        for (info in conn.onlinePlayers) {
            val line = COLOR.replace(info.tabListDisplayName?.string ?: continue, "")
            val m = TABLIST_RX.find(line) ?: continue
            val name = m.groupValues[2]
            if (teammateClasses.containsKey(name)) continue          // sticky — never overwrite a resolved class
            val cls = runCatching { DungeonClass.valueOf(m.groupValues[3].uppercase()) }.getOrNull() ?: continue
            teammateClasses[name] = cls
        }
    }

    private fun isLeapMenu(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.leapMenuEnabled || !Location.inDungeon()) return false
        val t = screen.title.string
        return t == "Spirit Leap" || t == "Teleport to Player"
    }

    /** For [fishmod.mixin.HandledScreenMixin] — hide Hypixel's own slots while the overlay is up. */
    @JvmStatic
    fun isActive(screen: AbstractContainerScreen<*>): Boolean = isLeapMenu(screen)

    /** Map view (click a head on the dungeon map to leap) — optionally gated on the blood door. */
    /** Map view relies on live room positions, which stop updating in the boss — drop to the 2×2 grid there. */
    private fun mapView(): Boolean =
        FishSettings.leapMenuMap && !DungeonState.isInBoss() &&
            (!FishSettings.leapMenuMapAfterBR || DungeonState.bloodOpened)

    /** True when the leap overlay is the currently-open screen — for HUD elements that should hide behind it. */
    @JvmStatic
    fun isOverlayOpen(): Boolean {
        val s = Minecraft.getInstance().screen
        return s is AbstractContainerScreen<*> && isLeapMenu(s)
    }

    private fun collect(screen: AbstractContainerScreen<*>): List<Target> {
        refreshTeammateClasses()
        val menu = screen.menu
        val containerSize = menu.slots.size - 36
        val out = ArrayList<Target>(4)
        for (i in 0 until containerSize) {
            val stack = menu.slots[i].item
            if (stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) continue
            val name = NAME_RX.find(stack.hoverName.string.replace(COLOR, ""))?.groupValues?.get(1) ?: continue
            val dp = DungeonPlayers.get(name)
            // Odin's sticky tab-list class first, then the map roster, then the packet map.
            val clazz = teammateClasses[name] ?: dpClass(dp) ?: DungeonClass.getClass(name)
            out.add(Target(i, name, clazz, dp?.isDead() == true))
        }
        return when (FishSettings.leapMenuSort) {
            2 -> odinSort(out)
            1 -> out.sortedBy { it.name.lowercase() }.take(4)
            else -> {
                val order = FishSettings.leapMenuClassOrder.split(",").map { it.trim().uppercase() }
                out.sortedWith(compareBy({ order.indexOf(it.clazz?.name ?: "").let { i -> if (i < 0) 99 else i } }, { it.name.lowercase() })).take(4)
            }
        }
    }

    // DungeonClass -> home quadrant. Quadrants: 0 TL, 1 TR, 2 BL, 3 BR.
    // Tank always takes bottom-right; when you're the Mage, Mage floats into whichever slot is free.
    private fun homeQuadrant(c: DungeonClass?): Int = when (c) {
        DungeonClass.ARCHER -> 0
        DungeonClass.BERSERK -> 1
        DungeonClass.HEALER -> 2
        DungeonClass.TANK -> 3
        DungeonClass.MAGE -> 3
        else -> -1
    }
    // Lower first. Tank beats Mage for the shared BR corner.
    private fun classPriority(c: DungeonClass?): Int = when (c) {
        DungeonClass.BERSERK -> 0
        DungeonClass.TANK -> 1
        DungeonClass.ARCHER, DungeonClass.HEALER, DungeonClass.MAGE -> 2
        else -> 3
    }
    private val EMPTY_TARGET = Target(-1, "", null, true)

    /**
     * Place each player (in class-priority order) into their class's default quadrant; on a collision
     * queue them, then fill the remaining quadrants from the queue in slot order. Empty quadrants
     * become a dead placeholder so the 2×2 layout is stable.
     */
    private fun odinSort(players: List<Target>): List<Target> {
        val result = arrayOfNulls<Target>(4)
        val overflow = ArrayDeque<Target>()
        for (p in players.sortedBy { classPriority(it.clazz) }) {
            val q = homeQuadrant(p.clazz)
            if (q in 0..3 && result[q] == null) result[q] = p else overflow.addLast(p)
        }
        for (i in 0..3) if (result[i] == null && overflow.isNotEmpty()) result[i] = overflow.removeFirst()
        val out = result.map { it ?: EMPTY_TARGET }
        fishmod.utils.debug.Debug.LOGGER.info(
            "[LeapMenu] odinSort in={} -> TL={} TR={} BL={} BR={}",
            players.map { "${it.name}:${it.clazz}" },
            "${out[0].name}:${out[0].clazz}", "${out[1].name}:${out[1].clazz}",
            "${out[2].name}:${out[2].clazz}", "${out[3].name}:${out[3].clazz}",
        )
        return out
    }

    private fun dpClass(dp: DungeonPlayers.DungeonPlayer?): DungeonClass? =
        dp?.clazz?.let { c -> runCatching { DungeonClass.valueOf(c.uppercase()) }.getOrNull() }

    private fun scale(): Float = (FishSettings.leapMenuScale.coerceIn(40, 220) / 100f)

    // One card centred in each screen quadrant (TL=0, TR=1, BL=2, BR=3).
    private fun cellRects(mc: Minecraft): Array<IntArray> {
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        val cw = (200 * scale()).toInt().coerceAtMost(w / 2 - 8)
        val ch = (62 * scale()).toInt().coerceAtMost(h / 2 - 8)
        val qw = w / 2; val qh = h / 2
        fun cx(q: Int) = q * qw + qw / 2 - cw / 2
        fun cy(q: Int) = q * qh + qh / 2 - ch / 2
        return arrayOf(
            intArrayOf(cx(0), cy(0), cw, ch),
            intArrayOf(cx(1), cy(0), cw, ch),
            intArrayOf(cx(0), cy(1), cw, ch),
            intArrayOf(cx(1), cy(1), cw, ch),
        )
    }

    /** Whole-screen quadrant under the cursor — click anywhere in it, not just on the card. */
    private fun hovered(mc: Minecraft, mx: Int, my: Int): Int {
        val col = if (mx < mc.window.guiScaledWidth / 2) 0 else 1
        val row = if (my < mc.window.guiScaledHeight / 2) 0 else 1
        return row * 2 + col
    }

    private class MapMarker(val x: Int, val y: Int, val target: Target)
    private var markers: List<MapMarker> = emptyList()

    private fun nearestMarker(mx: Int, my: Int): MapMarker? {
        fun d2(m: MapMarker) = (m.x - mx) * (m.x - mx) + (m.y - my) * (m.y - my)
        return markers.filter { d2(it) <= 15 * 15 }.minByOrNull { d2(it) }
    }

    private fun renderMapView(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, mc: Minecraft) {
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        ctx.fill(0, 0, w, h, 0xC0000000.toInt())

        if (!DungeonState.isInDungeon() || Scan.rooms.isEmpty()) {
            markers = emptyList()
            ctx.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§7Map not ready"), w / 2, h / 2, -1)
            return
        }

        val baseW = MapHud.baseWidth(mc).toFloat()
        val baseH = MapHud.baseHeight(mc).toFloat()
        val s = Math.min((w * 0.72f) / baseW, (h * 0.82f) / baseH).coerceAtLeast(1f)
        val ox = (w - baseW * s) / 2f
        val oy = (h - baseH * s) / 2f
        MapHud.renderAt(ctx, mc, ox, oy, s, false)

        val bg = DungeonMapSettings.mapBackgroundSize
        val out = ArrayList<MapMarker>(4)
        for (t in cache) {
            if (t.dead) continue
            val p = DungeonPlayers.get(t.name)?.mapRenderPosition() ?: continue
            out.add(MapMarker((ox + (bg + p[0]) * s).toInt(), (oy + (bg + p[1]) * s).toInt(), t))
        }
        markers = out

        val hov = nearestMarker(mouseX, mouseY)
        for (m in out) {
            val col = 0xFF000000.toInt() or (DungeonClass.getColor(m.target.clazz) and 0xFFFFFF)
            val r = if (m === hov) 9 else 7
            ctx.fill(m.x - r, m.y - r, m.x + r, m.y - r + 1, col)
            ctx.fill(m.x - r, m.y + r - 1, m.x + r, m.y + r, col)
            ctx.fill(m.x - r, m.y - r, m.x - r + 1, m.y + r, col)
            ctx.fill(m.x + r - 1, m.y - r, m.x + r, m.y + r, col)
            if (m === hov) {
                val cls = m.target.clazz?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"
                ctx.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§f${m.target.name} §7$cls"),
                    m.x, m.y - r - 11, -1)
            }
        }
        ctx.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§7Click a teammate to leap"), w / 2, h - 16, -1)
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!isLeapMenu(screen)) return
        cache = collect(screen)
        val mc = Minecraft.getInstance()
        if (mapView()) { renderMapView(ctx, mouseX, mouseY, mc); return }
        ctx.fill(0, 0, mc.window.guiScaledWidth, mc.window.guiScaledHeight, 0xC0000000.toInt())

        if (cache.isEmpty()) {
            ctx.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§4No players found"),
                mc.window.guiScaledWidth / 2, mc.window.guiScaledHeight / 2, -1)
            return
        }

        val rects = cellRects(mc)
        val hov = hovered(mc, mouseX, mouseY)
        // Highlight the whole hovered quadrant so it's clear the entire area is clickable.
        if (hov < cache.size) {
            val w = mc.window.guiScaledWidth; val h = mc.window.guiScaledHeight
            val qx = (hov % 2) * (w / 2); val qy = (hov / 2) * (h / 2)
            ctx.fill(qx, qy, qx + w / 2, qy + h / 2, 0x18FFFFFF)
        }
        val rad = (6 * scale()).toInt().coerceIn(3, 12)
        cache.forEachIndexed { i, t ->
            val r = rects[i]
            val classCol = DungeonClass.getColor(t.clazz) and 0xFFFFFF
            var bg = 0xCC1E1E1E.toInt()
            if (i == hov) bg = 0xE0333333.toInt()
            if (FishSettings.leapMenuTintDead && t.dead) bg = if (i == hov) 0xE0662222.toInt() else 0xCC4A1E1E.toInt()
            roundFill(ctx, r[0], r[1], r[2], r[3], rad, 0xFF000000.toInt() or classCol)
            roundFill(ctx, r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4, rad - 1, bg)

            val headSize = r[3] - 14
            val hx = r[0] + 8
            val hy = r[1] + 7
            roundFill(ctx, hx - 1, hy - 1, headSize + 2, headSize + 2, 3, 0xFF000000.toInt() or classCol)
            val skin = DungeonPlayers.get(t.name)?.skin
            if (skin != null) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, skin.body().texturePath(), hx, hy, 8f, 8f, headSize, headSize, 8, 8, 64, 64)
            }
            val tx = hx + headSize + 8
            val showName = FishSettings.leapMenuShowName
            val showClass = FishSettings.leapMenuShowClass
            val status = if (t.dead) "§cDEAD" else "§7${t.clazz?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"}"
            when {
                showName && showClass -> {
                    ctx.text(mc.font, "§f${t.name}", tx, r[1] + r[3] / 2 - 10, -1)
                    ctx.text(mc.font, status, tx, r[1] + r[3] / 2 + 2, -1)
                }
                showName -> ctx.text(mc.font, "§f${t.name}", tx, r[1] + r[3] / 2 - 4, -1)
                showClass -> ctx.text(mc.font, status, tx, r[1] + r[3] / 2 - 4, -1)
            }
        }
    }

    /** Filled rect with circular-ish rounded corners of radius [rad]. */
    private fun roundFill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, rad: Int, c: Int) {
        if (rad <= 0) { ctx.fill(x, y, x + w, y + h, c); return }
        ctx.fill(x + rad, y, x + w - rad, y + h, c)
        ctx.fill(x, y + rad, x + rad, y + h - rad, c)
        ctx.fill(x + w - rad, y + rad, x + w, y + h - rad, c)
        for (i in 0 until rad) {
            val inset = rad - Math.sqrt((rad * rad - (rad - 1 - i) * (rad - 1 - i)).toDouble()).toInt()
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, c)
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c)
        }
    }

    /** @return true to swallow. */
    @JvmStatic
    fun mouseClicked(button: Int, mx: Double, my: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!isLeapMenu(screen)) return false
        if (FishSettings.leapMenuLeftClickOnly && button != 0) return true
        if (mapView()) {
            nearestMarker(mx.toInt(), my.toInt())?.let { leap(it.target, screen) }
            return true // eat every click over the full-screen overlay
        }
        val i = hovered(Minecraft.getInstance(), mx.toInt(), my.toInt())
        if (i < 0 || i >= cache.size) return true // over the backdrop, not a cell — still eat it
        leap(cache[i], screen)
        return true
    }

    @JvmStatic
    fun keyPressed(key: Int, screen: AbstractContainerScreen<*>): Boolean {
        if (!isLeapMenu(screen) || !FishSettings.leapMenuKeybinds) return false
        val idx = when (key) {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> 0
            GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> 1
            GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> 2
            GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4 -> 3
            else -> return false
        }
        if (idx >= cache.size) return true
        leap(cache[idx], screen)
        return true
    }

    private fun leap(t: Target, screen: AbstractContainerScreen<*>) {
        if (t.dead) return
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        mc.gameMode?.handleContainerInput(screen.menu.containerId, t.slot, 0, ContainerInput.PICKUP, p)
        if (mc.screen === screen) screen.onClose()
    }
}
