package fishmod.features.other

import fishmod.compat.showScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

// In-game editor for one inventory button (opened by right-click in edit mode).
class InventoryButtonEditScreen(private val b: InvButton, private val parent: Screen?) : Screen(Component.literal("Edit Button")) {

    private lateinit var cmd: EditBox
    private lateinit var name: EditBox
    private lateinit var icon: EditBox
    private lateinit var color: EditBox
    private val rects = HashMap<String, IntArray>()
    private var deleted = false

    private val pw = 200
    private val ph = 138
    private fun lx() = (width - pw) / 2
    private fun ty() = (height - ph) / 2

    override fun isPauseScreen() = false

    private fun box(y: Int, max: Int, value: String, hint: String): EditBox {
        val f = EditBox(font, lx(), y, pw, 16, Component.literal(hint))
        f.setMaxLength(max)
        f.value = value
        f.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
        addRenderableWidget(f)
        return f
    }

    override fun init() {
        val y = ty() + 20
        cmd = box(y, 120, b.command, "Command (no /)")
        name = box(y + 20, 40, b.name, "Name")
        icon = box(y + 40, 80, b.icon, "Icon id")
        color = box(y + 60, 8, "%08X".format(b.color), "Colour (hex)")
        setInitialFocus(cmd)
    }

    private fun apply() {
        b.command = cmd.value.trim()
        b.name = name.value
        b.icon = icon.value.trim()
        color.value.trim().removePrefix("#").toLongOrNull(16)?.let {
            b.color = if (color.value.trim().removePrefix("#").length <= 6) (0xFF000000 or it).toInt() else it.toInt()
        }
    }

    private fun pill(ctx: GuiGraphicsExtractor, id: String, label: String, x: Int, y: Int, w: Int, on: Boolean, mx: Int, my: Int) {
        val r = intArrayOf(x, y, w, 16)
        rects[id] = r
        val hov = mx >= x && mx < x + w && my >= y && my < y + 16
        ctx.fill(x, y, x + w, y + 16, if (on) 0xFF3A6EA5.toInt() else if (hov) 0xFF3A3F48.toInt() else 0xFF22262C.toInt())
        ctx.text(font, label, x + (w - font.width(label)) / 2, y + 4, -1, false)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val lx = lx()
        val ty = ty()
        ctx.fill(lx - 8, ty - 8, lx + pw + 8, ty + ph + 8, 0xF0101418.toInt())
        ctx.fill(lx - 8, ty - 8, lx + pw + 8, ty - 7, 0xFF3A6EA5.toInt())
        ctx.text(font, "Edit Inventory Button", lx, ty + 4, -1, true)
        var y = ty + 20 + 82
        pill(ctx, "smaller", "-", lx, y, 20, false, mouseX, mouseY)
        pill(ctx, "bigger", "+", lx + 24, y, 20, false, mouseX, mouseY)
        pill(ctx, "showName", if (b.showName) "Name: shown" else "Name: hidden", lx + 48, y, pw - 48, b.showName, mouseX, mouseY)
        y += 20
        pill(ctx, "held", "Icon = held item", lx, y, pw, false, mouseX, mouseY)
        y += 20
        pill(ctx, "delete", "Delete", lx, y, 60, false, mouseX, mouseY)
        pill(ctx, "done", "Done", lx + pw - 60, y, 60, true, mouseX, mouseY)
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        val tip = when {
            hit("smaller", mouseX.toDouble(), mouseY.toDouble()) || hit("bigger", mouseX.toDouble(), mouseY.toDouble()) -> "Button size: ${b.size}"
            hit("held", mouseX.toDouble(), mouseY.toDouble()) -> "Use the item in your main hand as the icon"
            cmd.isHovered -> "Command without /, e.g. warp dhub"
            icon.isHovered -> "Item id, e.g. minecraft:compass (or use the held item)"
            color.isHovered -> "Hex colour, RRGGBB or AARRGGBB"
            else -> null
        }
        if (tip != null) ctx.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY)
    }

    private fun hit(id: String, mx: Double, my: Double): Boolean {
        val r = rects[id] ?: return false
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x()
        val my = click.y()
        when {
            hit("smaller", mx, my) -> b.size = (b.size - 2).coerceAtLeast(10)
            hit("bigger", mx, my) -> b.size = (b.size + 2).coerceAtMost(40)
            hit("showName", mx, my) -> b.showName = !b.showName
            hit("held", mx, my) -> {
                val held = Minecraft.getInstance().player?.mainHandItem
                if (held != null && !held.isEmpty) icon.value = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.item).toString()
            }
            hit("delete", mx, my) -> { deleted = true; onClose() }
            hit("done", mx, my) -> onClose()
            else -> return super.mouseClicked(click, doubled)
        }
        return true
    }

    override fun onClose() {
        if (deleted) InvShortcutButtons.buttons.remove(b) else apply()
        InvShortcutButtons.save()
        Minecraft.getInstance().showScreen(parent)
    }
}
