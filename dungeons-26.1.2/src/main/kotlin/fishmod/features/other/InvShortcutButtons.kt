package fishmod.features.other

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import fishmod.compat.showScreen
import fishmod.compat.curScreen
import fishmod.utils.FishUtil
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.InvButtonSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

// One user button; x/y are relative to the inventory's top-left corner.
class InvButton(
    var x: Int = 0,
    var y: Int = 0,
    var size: Int = 18,
    var command: String = "",
    var name: String = "",
    var icon: String = "",
    var color: Int = 0xC0202428.toInt(),
    var showName: Boolean = true,
) {
    @Transient private var iconKey: String? = null
    @Transient private var iconStack: ItemStack = ItemStack.EMPTY

    fun stack(): ItemStack {
        val id = icon.trim()
        if (id != iconKey) {
            iconKey = id
            iconStack = runCatching {
                if (id.isEmpty()) ItemStack.EMPTY
                else BuiltInRegistries.ITEM.getOptional(Identifier.parse(id.lowercase())).orElse(null)
                    ?.takeIf { it != Items.AIR }?.let { ItemStack(it) } ?: ItemStack.EMPTY
            }.getOrDefault(ItemStack.EMPTY)
        }
        return iconStack
    }

    private fun label() = if (showName) name.trim() else ""

    fun width(): Int {
        val l = label()
        return if (l.isEmpty()) size else size + Minecraft.getInstance().font.width(l) + 5
    }

    fun inBounds(mx: Double, my: Double) = mx >= x && mx < x + width() && my >= y && my < y + size
}

object InvShortcutButtons {
    const val INV_W = 176
    const val INV_H = 166
    private const val SNAP = 4
    private const val GUIDE = 0xFFFF4FB0.toInt()
    private const val BORDER = 0xFF3A3F48.toInt()
    private const val BORDER_HOVER = 0xFFFFFFFF.toInt()

    private val gson = Gson()
    private var loadedJson: String? = null
    @JvmField var buttons: MutableList<InvButton> = ArrayList()

    @JvmField var editMode = false
    private var dragging: InvButton? = null
    private var dragDX = 0.0
    private var dragDY = 0.0
    private var guideX: Int? = null
    private var guideY: Int? = null

    // edit/add pills sit just under the inventory's bottom-right corner
    private val EDIT = intArrayOf(INV_W - 34, INV_H + 2, 34, 11)
    private val ADD = intArrayOf(INV_W - 72, INV_H + 2, 36, 11)

    private fun load() {
        if (InvButtonSettings.buttonsJson == loadedJson) return
        loadedJson = InvButtonSettings.buttonsJson
        buttons = runCatching {
            gson.fromJson<MutableList<InvButton>>(InvButtonSettings.buttonsJson, object : TypeToken<MutableList<InvButton>>() {}.type)
        }.getOrNull()?.filterNotNull()?.toMutableList() ?: migrate()
    }

    // old fixed 7-button config -> list
    private fun migrate(): MutableList<InvButton> {
        val cmds = listOf(InvButtonSettings.invCmd1, InvButtonSettings.invCmd2, InvButtonSettings.invCmd3, InvButtonSettings.invCmd4, InvButtonSettings.invCmd5, InvButtonSettings.invCmd6, InvButtonSettings.invCmd7)
        val names = listOf(InvButtonSettings.name1, InvButtonSettings.name2, InvButtonSettings.name3, InvButtonSettings.name4, InvButtonSettings.name5, InvButtonSettings.name6, InvButtonSettings.name7)
        val icons = listOf(InvButtonSettings.icon1, InvButtonSettings.icon2, InvButtonSettings.icon3, InvButtonSettings.icon4, InvButtonSettings.icon5, InvButtonSettings.icon6, InvButtonSettings.icon7)
        val defs = listOf(77 to 5, 77 to 23, 77 to 41, 133 to 5, 151 to 5, 133 to 61, 151 to 61)
        val layout = InvButtonSettings.layout.split(';')
        val out = ArrayList<InvButton>()
        for (i in 0 until 7) {
            if (cmds[i].isBlank()) continue
            val xy = layout.getOrNull(i)?.split(',')
            out += InvButton(
                xy?.getOrNull(0)?.trim()?.toIntOrNull() ?: defs[i].first,
                xy?.getOrNull(1)?.trim()?.toIntOrNull() ?: defs[i].second,
                command = cmds[i], name = names[i], icon = icons[i])
        }
        return out
    }

    @JvmStatic
    fun save() {
        InvButtonSettings.buttonsJson = gson.toJson(buttons)
        loadedJson = InvButtonSettings.buttonsJson
        runCatching { FishConfig.manager.save() }.onFailure { FishDiag.fail("InvShortcutButtons.3", "saving inventory buttons failed", it) }
    }

    private fun inside(mx: Double, my: Double, r: IntArray) = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]

    private fun altDown(): Boolean {
        val w = Minecraft.getInstance().window
        return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT)
    }

    private fun carried(): ItemStack = Minecraft.getInstance().player?.containerMenu?.carried ?: ItemStack.EMPTY

    @JvmStatic
    fun on(): Boolean = InvButtonSettings.enableInventoryButtons && !fishmod.compat.BladeCompat.active

    @JvmStatic
    fun parseClicks(mouseX: Double, mouseY: Double, button: Int): Boolean {
        load()
        if (inside(mouseX, mouseY, EDIT)) {
            editMode = !editMode
            dragging = null
            return true
        }
        if (editMode && inside(mouseX, mouseY, ADD)) {
            val b = InvButton(INV_W + 4, buttons.size * 20 % INV_H)
            buttons += b
            save()
            openEditor(b)
            return true
        }
        val hit = buttons.lastOrNull { it.inBounds(mouseX, mouseY) } ?: return false
        if (editMode) {
            if (button == 1) {
                val held = carried()
                if (!held.isEmpty) {
                    hit.icon = BuiltInRegistries.ITEM.getKey(held.item).toString()
                    save()
                } else openEditor(hit)
                return true
            }
            dragging = hit
            dragDX = mouseX - hit.x
            dragDY = mouseY - hit.y
            return true
        }
        if (button != 0 || hit.command.isBlank()) return false
        try {
            FishUtil.executeCommand(hit.command)
        } catch (e: Exception) {
            FishDiag.fail("InvShortcutButtons.1", "inventory button click failed", e)
        }
        return true
    }

    private fun openEditor(b: InvButton) {
        val mc = Minecraft.getInstance()
        mc.showScreen(InventoryButtonEditScreen(b, mc.curScreen))
    }

    private fun snap(b: InvButton, x: Int, y: Int): Pair<Int, Int> {
        val xs = mutableListOf(0, INV_W / 2, INV_W)
        val ys = mutableListOf(0, INV_H / 2, INV_H)
        for (o in buttons) {
            if (o === b) continue
            xs += listOf(o.x, o.x + o.width() / 2, o.x + o.width())
            ys += listOf(o.y, o.y + o.size / 2, o.y + o.size)
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
        val (nx, gx) = best(x, b.width(), xs)
        val (ny, gy) = best(y, b.size, ys)
        guideX = gx
        guideY = gy
        return Pair(nx, ny)
    }

    @JvmStatic
    fun drag(mouseX: Double, mouseY: Double): Boolean {
        val b = dragging ?: return false
        val x = (mouseX - dragDX).toInt()
        val y = (mouseY - dragDY).toInt()
        if (altDown()) {
            guideX = null; guideY = null
            b.x = x; b.y = y
        } else {
            val (nx, ny) = snap(b, x, y)
            b.x = nx; b.y = ny
        }
        return true
    }

    @JvmStatic
    fun release(): Boolean {
        if (dragging == null) return false
        dragging = null
        guideX = null; guideY = null
        save()
        return true
    }

    @JvmStatic
    fun onClose() {
        if (dragging != null) release()
    }

    private fun pill(ctx: GuiGraphicsExtractor, r: IntArray, label: String, on: Boolean, mx: Int, my: Int) {
        val hov = inside(mx.toDouble(), my.toDouble(), r)
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], if (on) 0xD03A6EA5.toInt() else if (hov) 0xA0303540.toInt() else 0x60000000)
        val font = Minecraft.getInstance().font
        ctx.text(font, label, r[0] + (r[2] - font.width(label)) / 2, r[1] + 2, if (on || hov) -1 else 0xFFAAAAAA.toInt(), false)
    }

    private fun draw(ctx: GuiGraphicsExtractor, b: InvButton, mx: Int, my: Int) {
        val w = b.width()
        val hov = b.inBounds(mx.toDouble(), my.toDouble())
        ctx.fill(b.x, b.y, b.x + w, b.y + b.size, b.color)
        val ring = if (hov) BORDER_HOVER else BORDER
        ctx.fill(b.x, b.y, b.x + w, b.y + 1, ring)
        ctx.fill(b.x, b.y + b.size - 1, b.x + w, b.y + b.size, ring)
        ctx.fill(b.x, b.y, b.x + 1, b.y + b.size, ring)
        ctx.fill(b.x + w - 1, b.y, b.x + w, b.y + b.size, ring)
        val font = Minecraft.getInstance().font
        val st = b.stack()
        if (!st.isEmpty) {
            val s = (b.size - 2) / 16f
            val pose = ctx.pose()
            pose.pushMatrix()
            pose.translate(b.x + (b.size - 16 * s) / 2f, b.y + (b.size - 16 * s) / 2f)
            pose.scale(s, s)
            ctx.item(st, 0, 0)
            pose.popMatrix()
        } else if (editMode && b.command.isBlank()) {
            ctx.text(font, "?", b.x + (b.size - font.width("?")) / 2 + 1, b.y + (b.size - font.lineHeight) / 2 + 1, 0xFFAAAAAA.toInt(), true)
        }
        if (b.showName && b.name.isNotBlank())
            ctx.text(font, b.name.trim(), b.x + b.size + 2, b.y + (b.size - font.lineHeight) / 2 + 1, -1, true)
    }

    @JvmStatic
    fun renderAll(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        load()
        if (editMode) {
            guideX?.let { context.fill(it, -400, it + 1, 600, GUIDE) }
            guideY?.let { context.fill(-400, it, 800, it + 1, GUIDE) }
            val font = Minecraft.getInstance().font
            context.text(font, "Drag to move, right-click to edit (or with an item to set icon), Alt = no snap", -40, INV_H + 16, 0xFFFFFF55.toInt(), true)
            pill(context, ADD, "+ Add", false, mouseX, mouseY)
        }
        pill(context, EDIT, if (editMode) "Done" else "Edit", editMode, mouseX, mouseY)
        for (b in buttons) {
            if (!editMode && b.command.isBlank()) continue
            try {
                draw(context, b, mouseX, mouseY)
            } catch (e: Exception) {
                FishDiag.fail("InvShortcutButtons.2", "inventory button render failed", e)
            }
        }
    }
}
