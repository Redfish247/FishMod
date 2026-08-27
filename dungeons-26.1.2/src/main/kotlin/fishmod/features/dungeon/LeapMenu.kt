package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonPlayers
import fishmod.utils.Location
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
 * Custom Spirit Leap menu (ported from NoammAddons' LeapMenu, core). Replaces Hypixel's
 * "Spirit Leap" / "Teleport to Player" chest GUI with a 2×2 grid of class-coloured cells; click a
 * quadrant or press 1-4 to leap. Uses [DungeonPlayers] for class / skin / dead state.
 */
object LeapMenu {

    private val NAME_RX = Regex("(?:\\[.+?] )?(\\w{1,16})")
    private data class Target(val slot: Int, val name: String, val clazz: DungeonClass?, val dead: Boolean)

    private var cache: List<Target> = emptyList()

    private fun isLeapMenu(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.leapMenuEnabled || !Location.inDungeon()) return false
        val t = screen.title.string
        return t == "Spirit Leap" || t == "Teleport to Player"
    }

    private fun collect(screen: AbstractContainerScreen<*>): List<Target> {
        val menu = screen.menu
        val containerSize = menu.slots.size - 36
        val out = ArrayList<Target>(4)
        for (i in 0 until containerSize) {
            val stack = menu.slots[i].item
            if (stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) continue
            val name = NAME_RX.find(stack.hoverName.string.replace(Regex("§."), ""))?.groupValues?.get(1) ?: continue
            val dp = DungeonPlayers.get(name)
            out.add(Target(i, name, DungeonClass.getClass(name), dp?.isDead() == true))
        }
        return when (FishSettings.leapMenuSort) {
            1 -> out.sortedBy { it.name.lowercase() }
            else -> {
                val order = FishSettings.leapMenuClassOrder.split(",").map { it.trim().uppercase() }
                out.sortedWith(compareBy({ order.indexOf(it.clazz?.name ?: "").let { i -> if (i < 0) 99 else i } }, { it.name.lowercase() }))
            }
        }.take(4)
    }

    private fun scale(): Float = (FishSettings.leapMenuScale.coerceIn(40, 220) / 100f)

    // 2x2 layout rects in GUI-scaled space.
    private fun cellRects(mc: Minecraft): Array<IntArray> {
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        val cw = (188 * scale()).toInt()
        val ch = (58 * scale()).toInt()
        val pad = (16 * scale()).toInt()
        val gx = w / 2 - cw - pad / 2
        val gy = h / 2 - ch - pad / 2
        return arrayOf(
            intArrayOf(gx, gy, cw, ch),
            intArrayOf(gx + cw + pad, gy, cw, ch),
            intArrayOf(gx, gy + ch + pad, cw, ch),
            intArrayOf(gx + cw + pad, gy + ch + pad, cw, ch),
        )
    }

    private fun hovered(mc: Minecraft, mx: Int, my: Int): Int {
        cellRects(mc).forEachIndexed { i, r ->
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return i
        }
        return -1
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!isLeapMenu(screen)) return
        cache = collect(screen)
        val mc = Minecraft.getInstance()
        ctx.fill(0, 0, mc.window.guiScaledWidth, mc.window.guiScaledHeight, 0xC0000000.toInt())

        if (cache.isEmpty()) {
            ctx.centeredText(mc.font, net.minecraft.network.chat.Component.literal("§4No players found"),
                mc.window.guiScaledWidth / 2, mc.window.guiScaledHeight / 2, -1)
            return
        }

        val rects = cellRects(mc)
        val hov = hovered(mc, mouseX, mouseY)
        val rad = (6 * scale()).toInt().coerceIn(3, 12)
        cache.forEachIndexed { i, t ->
            val r = rects[i]
            val classCol = DungeonClass.getColor(t.clazz) and 0xFFFFFF
            var bg = 0xCC1E1E1E.toInt()
            if (i == hov) bg = 0xE0333333.toInt()
            if (FishSettings.leapMenuTintDead && t.dead) bg = if (i == hov) 0xE0662222.toInt() else 0xCC4A1E1E.toInt()
            roundFill(ctx, r[0], r[1], r[2], r[3], rad, 0xFF000000.toInt() or classCol)   // border
            roundFill(ctx, r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4, rad - 1, bg)            // inner

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
