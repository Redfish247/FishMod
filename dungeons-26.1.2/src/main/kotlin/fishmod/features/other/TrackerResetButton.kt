package fishmod.features.other

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen

// Red "[Reset]" under a tracker HUD while the inventory is open; two clicks reset that tracker
object TrackerResetButton {

    private const val CONFIRM_MS = 3_000L
    private class Btn(val key: String, val x0: Double, val y0: Double, val x1: Double, val y1: Double, val reset: () -> Unit)
    private val btns = ArrayList<Btn>()
    private val armedAt = HashMap<String, Long>()

    private fun armed(key: String) = System.currentTimeMillis() - (armedAt[key] ?: 0L) <= CONFIRM_MS

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@AfterInit
            ScreenEvents.beforeExtract(screen).register(ScreenEvents.BeforeExtract { _, _, _, _, _ -> btns.clear() })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                if (click.button() != 0) return@AllowMouseClick true
                val b = btns.firstOrNull { click.x() in it.x0..it.x1 && click.y() in it.y0..it.y1 } ?: return@AllowMouseClick true
                if (armed(b.key)) { armedAt.remove(b.key); b.reset() } else armedAt[b.key] = System.currentTimeMillis()
                false
            })
        })
    }

    // x/y = screen position of the button's top-left, same scale as the tracker above it
    fun draw(ctx: GuiGraphicsExtractor, key: String, x: Int, y: Int, scale: Double, mx: Int, my: Int, reset: () -> Unit) {
        val font = Minecraft.getInstance().font
        val text = if (armed(key)) "[Click again]" else "[Reset]"
        val w = font.width("§l$text") * scale
        val h = 10 * scale
        val hover = mx >= x && mx <= x + w && my >= y && my <= y + h
        val col = if (armed(key) || hover) "§4§l" else "§c§l"
        btns += Btn(key, x.toDouble(), y.toDouble(), x + w, y + h, reset)
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(scale.toFloat(), scale.toFloat())
        ctx.text(font, "$col$text", 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
