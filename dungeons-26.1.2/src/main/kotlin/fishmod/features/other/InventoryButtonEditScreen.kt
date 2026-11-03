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

    private val pw = 220
    private fun lx() = (width - pw) / 2
    private fun ty() = (height - 200) / 2

    override fun isPauseScreen() = false

    private fun box(y: Int, max: Int, value: String, hint: String): EditBox {
        val f = EditBox(font, lx() + 60, y, pw - 70, 16, Component.literal(hint))
        f.setMaxLength(max)
        f.value = value
        addRenderableWidget(f)
        return f
    }

    override fun init() {
        val y = ty() + 24
        cmd = box(y, 120, b.command, "Command")
        name = box(y + 22, 40, b.name, "Name")
        icon = box(y + 44, 80, b.icon, "Icon")
        color = box(y + 66, 8, "%08X".format(b.color), "Colour")
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
        ctx.fill(lx - 6, ty - 6, lx + pw + 6, ty + 196, 0xE0101418.toInt())
        ctx.text(font, "Edit Inventory Button", lx, ty + 4, -1, true)
        val labels = listOf("Command", "Name", "Icon id", "Colour")
        for ((i, l) in labels.withIndex()) ctx.text(font, l, lx, ty + 28 + i * 22, 0xFFAAAAAA.toInt(), false)
        var y = ty + 24 + 88
        ctx.text(font, "Size: ${b.size}", lx, y + 4, 0xFFAAAAAA.toInt(), false)
        pill(ctx, "smaller", "-", lx + 60, y, 20, false, mouseX, mouseY)
        pill(ctx, "bigger", "+", lx + 84, y, 20, false, mouseX, mouseY)
        pill(ctx, "showName", if (b.showName) "Name: shown" else "Name: hidden", lx + 112, y, pw - 112, b.showName, mouseX, mouseY)
        y += 22
        pill(ctx, "held", "Icon = held item", lx, y, pw, false, mouseX, mouseY)
        y += 22
        ctx.text(font, "Commands without /, e.g. warp dhub", lx, y + 4, 0xFF777777.toInt(), false)
        y += 18
        pill(ctx, "delete", "Delete", lx, y, 60, false, mouseX, mouseY)
        pill(ctx, "done", "Done", lx + pw - 60, y, 60, true, mouseX, mouseY)
        super.extractRenderState(ctx, mouseX, mouseY, delta)
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
