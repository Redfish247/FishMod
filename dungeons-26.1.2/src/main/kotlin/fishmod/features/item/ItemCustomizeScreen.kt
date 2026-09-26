package fishmod.features.item

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.data.ItemUtil
import fishmod.utils.data.LegacyFormatting
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class ItemCustomizeScreen : Screen(Component.literal("Item Customize")), HasUiOverlay {

    private enum class Tab(val label: String) { NAME("Name"), MODEL("Model"), DYE("Dye"), TRIM("Trim") }

    private companion object {
        const val CELL = 18
        const val PANEL_W = 480
        const val PANEL_H = 285
        const val HEADER_H = 32
        const val LEFT_W = 170
        const val ARM_STEP = 28

        val BG_PANEL = 0xF20E1016.toInt()
        val BG_SECTION = 0xFF171A22.toInt()
        val PANEL_BORDER = 0xFF2A2D38.toInt()
        val FIELD_BG = 0xFF1B1E27.toInt()
        val FIELD_BORDER = 0xFF2E333D.toInt()
        val ACCENT = ScreenTheme.ACCENT
        val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        val ACCENT_INK = 0xFF06302F.toInt()
        val TEXT_PRIM = ScreenTheme.TEXT_COLOR
        val TEXT_HINT = ScreenTheme.SUBTEXT_COLOR
        val ROW_HOVER = 0xFF2A2D38.toInt()
        val LIST_BG = 0xFF14161D.toInt()
        val DANGER = ScreenTheme.DANGER
        val DANGER_HOVER = ScreenTheme.DANGER_HOVER
        val WARN = 0xFFF2C14E.toInt()

        val MC_BG = 0xFF171A22.toInt()
        val MC_EDGE = 0xFF2A2D38.toInt()
        val MC_SLOT = 0xFF1E2129.toInt()
        val MC_SLOT_HOVER = 0xFF2C303B.toInt()
        val MC_SLOT_EDGE = 0xFF2E333D.toInt()
        val MC_TAG = ScreenTheme.SUBTEXT_COLOR

        val ARMOR_SLOTS = intArrayOf(39, 38, 37, 36)
        val ARMOR_TAGS = arrayOf("H", "C", "L", "B")
        val ARMOR_EQUIP = setOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

        val CODE_COLORS: Array<IntArray> = arrayOf(
            intArrayOf('0'.code, 0x000000), intArrayOf('1'.code, 0x0000AA), intArrayOf('2'.code, 0x00AA00), intArrayOf('3'.code, 0x00AAAA),
            intArrayOf('4'.code, 0xAA0000), intArrayOf('5'.code, 0xAA00AA), intArrayOf('6'.code, 0xFFAA00), intArrayOf('7'.code, 0xAAAAAA),
            intArrayOf('8'.code, 0x555555), intArrayOf('9'.code, 0x5555FF), intArrayOf('a'.code, 0x55FF55), intArrayOf('b'.code, 0x55FFFF),
            intArrayOf('c'.code, 0xFF5555), intArrayOf('d'.code, 0xFF55FF), intArrayOf('e'.code, 0xFFFF55), intArrayOf('f'.code, 0xFFFFFF)
        )
        val CODE_FORMATS: Array<String> = arrayOf("l", "o", "n", "m", "k", "r", "*")

        val DYES: Array<Array<String>> = arrayOf(
            arrayOf("Pure White", "FFFFFF"), arrayOf("Pure Black", "000000"), arrayOf("Pure Yellow", "FFF700"), arrayOf("Pure Blue", "0013FF"),
            arrayOf("Aquamarine", "7FFFD4"), arrayOf("Bingo Blue", "002FA7"), arrayOf("Bone", "E3DAC9"), arrayOf("Brick Red", "CB4154"),
            arrayOf("Byzantium", "702963"), arrayOf("Carmine", "960018"), arrayOf("Celadon", "ACE1AF"), arrayOf("Celeste", "B2FFFF"),
            arrayOf("Cyclamen", "F56FA1"), arrayOf("Dark Purple", "301934"), arrayOf("Emerald", "50C878"), arrayOf("Flame", "E25822"),
            arrayOf("Holly", "3C6746"), arrayOf("Iceberg", "71A6D2"), arrayOf("Livid", "6699CC"), arrayOf("Mango", "FDBE02"),
            arrayOf("Midnight", "702670"), arrayOf("Nadeshiko", "F6ADC6"), arrayOf("Necron", "E7413C"), arrayOf("Nyanza", "E9FFDB"),
            arrayOf("Tentacle", "324D6C"), arrayOf("Wild Strawberry", "FF43A4"),
            arrayOf("White", "F9FFFE"), arrayOf("Light Gray", "999999"), arrayOf("Gray", "4C4C4C"), arrayOf("Ink Sac (Black)", "191919"),
            arrayOf("Rose Red", "993333"), arrayOf("Orange", "D87F33"), arrayOf("Dandelion Yellow", "E5E533"), arrayOf("Lime", "7FCC19"),
            arrayOf("Cactus Green", "667F33"), arrayOf("Light Blue", "6699D8"), arrayOf("Cyan", "4C7F99"), arrayOf("Lapis (Blue)", "334CB2"),
            arrayOf("Purple", "7F3FB2"), arrayOf("Magenta", "B24CD8"), arrayOf("Pink", "F27FA5"), arrayOf("Cocoa (Brown)", "664C33")
        )
        val DYE_RGB: IntArray = IntArray(DYES.size) { i -> java.lang.Long.parseLong(DYES[i][1], 16).toInt() }

        const val SWATCH = 16
        const val SWATCH_GAP = 4

        fun cap(s: String): String = if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

        fun inBox(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
            mx >= x && mx <= x + w && my >= y && my <= y + h

        fun brightness(rgb: Int): Int {
            val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
    }

    private var ready = false
    private var panelX = 0
    private var panelY = 0
    private var selectedIndex = 0
    private var tab = Tab.NAME
    private var appliedAt = 0L

    private lateinit var nameField: EditBox
    private lateinit var modelField: EditBox
    private lateinit var dyeField: EditBox
    private lateinit var trimMatDropdown: Dropdown
    private lateinit var trimPatDropdown: Dropdown

    private val trimMaterials by lazy { ArmorTrimCache.materials() }
    private val trimPatterns by lazy { ArmorTrimCache.patterns() }

    private val keyRects = ArrayList<IntArray>()
    private val keyCodes = ArrayList<String>()
    private val tabRects = ArrayList<IntArray>()

    private fun inv(): Inventory = minecraft!!.player!!.inventory
    private fun mainCount(): Int = min(36, inv().containerSize)
    private fun selected(): ItemStack = inv().getItem(selectedIndex)

    private val lx get() = panelX + 14
    private val rx get() = panelX + LEFT_W + 30
    private val rw get() = PANEL_W - LEFT_W - 44
    private val pickY get() = panelY + 40
    private val armBoxY get() = panelY + 60
    private val invBoxY get() = panelY + 97
    private val pvY get() = panelY + 189
    private val footY get() = panelY + PANEL_H - 26
    private val contentY get() = panelY + 70

    private fun armSlotX(r: Int) = lx + 14 + r * ARM_STEP
    private fun armSlotY() = armBoxY + 3
    private fun invSlotX(i: Int) = lx + 4 + (i % 9) * CELL
    private fun invSlotY(i: Int) = if (i < 9) invBoxY + 4 + 3 * CELL + 4 else invBoxY + 4 + ((i - 9) / 9) * CELL

    private fun slotAt(mx: Int, my: Int): Int {
        for (r in 0 until 4) if (inBox(mx, my, armSlotX(r), armSlotY(), CELL - 1, CELL - 1)) return ARMOR_SLOTS[r]
        for (i in 0 until mainCount()) if (inBox(mx, my, invSlotX(i), invSlotY(i), CELL - 1, CELL - 1)) return i
        return -1
    }

    override fun init() {
        if (minecraft == null || minecraft!!.player == null) return

        val vw = (this.width / UiScale.factor()).toInt()
        val vh = (this.height / UiScale.factor()).toInt()
        panelX = (vw - PANEL_W) / 2
        panelY = max(8, (vh - PANEL_H) / 2)

        if (!ready) {
            val held = minecraft!!.player!!.mainHandItem
            for (i in 0 until mainCount()) if (inv().getItem(i) === held) { selectedIndex = i; break }
        }

        val fieldY = contentY + 10
        nameField = EditBox(this.font, rx, fieldY, rw, 18, Component.literal("Name"))
        nameField.setMaxLength(128)
        nameField.setBordered(false)

        modelField = EditBox(this.font, rx, fieldY, rw, 18, Component.literal("Model"))
        modelField.setMaxLength(64)
        modelField.setBordered(false)

        dyeField = EditBox(this.font, rx + 28, 0, 70, 18, Component.literal("Hex"))
        dyeField.setMaxLength(6)
        dyeField.setBordered(false)

        val half = (rw - 8) / 2
        trimMatDropdown = Dropdown("Material", rx, fieldY, half)
        for (s in trimMaterials) trimMatDropdown.labels.add(cap(s.substringAfterLast(':')))
        trimMatDropdown.onChange = Runnable { applyTrim() }
        trimPatDropdown = Dropdown("Pattern", rx + half + 8, fieldY, half)
        for (s in trimPatterns) trimPatDropdown.labels.add(cap(s.substringAfterLast(':')))
        trimPatDropdown.onChange = Runnable { applyTrim() }

        focusedField = null
        ready = true
        loadFields()
    }

    private class ClickRect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun hit(mx: Int, my: Int) = inBox(mx, my, x, y, w, h)
    }

    private fun resetRect() = ClickRect(lx, footY, 84, 18)
    private fun doneRect() = ClickRect(panelX + PANEL_W - 14 - 70, footY, 70, 18)
    private fun applyRect() = ClickRect(panelX + PANEL_W - 14 - 70 - 8 - 70, footY, 70, 18)
    private fun modelDefaultRect() = ClickRect(rx, contentY + 58, 84, 16)
    private fun dyeClearRect() = ClickRect(rx + rw - 60, dyeHexY(), 60, 18)
    private fun trimClearRect() = ClickRect(rx, contentY + 36, 70, 16)
    private fun dyeHexY() = contentY + 10 + dyeRows() * (SWATCH + SWATCH_GAP) + 16
    private fun dyeCols() = max(1, (rw + SWATCH_GAP) / (SWATCH + SWATCH_GAP))
    private fun dyeRows() = (DYES.size + dyeCols() - 1) / dyeCols()

    private fun uuidOf(st: ItemStack): String? = if (st.isEmpty) null else ItemUtil.getUuid(st)

    private fun hasCustom(st: ItemStack): Boolean {
        val id = uuidOf(st) ?: return false
        return ItemCustomizationStore.getItemName(id) != null || ItemCustomizationStore.getModelId(id) != null ||
            ItemCustomizationStore.getDyeColor(id) != null || ItemCustomizationStore.getArmorTrim(id) != null ||
            ItemCustomizationStore.getAnimatedDye(id) != null
    }

    private fun defaultModel(st: ItemStack): String =
        if (!st.isEmpty) BuiltInRegistries.ITEM.getKey(st.item).toString() else ""

    private fun loadFields() {
        val sel = selected()
        val id = uuidOf(sel)

        nameField.setValue(if (id != null) ItemCustomizationStore.getItemName(id) ?: "" else "")

        val def = defaultModel(sel)
        modelField.setValue(if (id != null) ItemCustomizationStore.getModelId(id) ?: def else def)

        val dye = if (id != null) ItemCustomizationStore.getDyeColor(id) else null
        dyeField.setValue(if (dye != null) String.format("%06X", dye and 0xFFFFFF) else "")

        val trim = if (id != null) ItemCustomizationStore.getArmorTrim(id) else null
        trimMatDropdown.selected = if (trim != null) trimMaterials.indexOf(trim.material) else -1
        trimPatDropdown.selected = if (trim != null) trimPatterns.indexOf(trim.pattern) else -1

        trimMatDropdown.close(); trimPatDropdown.close()
    }

    private fun dyeAllowed(st: ItemStack?): Boolean {
        if (st == null || st.isEmpty) return false
        return try { st.has(DataComponents.DYED_COLOR) } catch (e: Exception) { false }
    }

    private fun isArmour(st: ItemStack): Boolean {
        if (st.isEmpty) return false
        val eq = try { st.get(DataComponents.EQUIPPABLE) } catch (e: Exception) { null } ?: return false
        return eq.assetId().isPresent && eq.slot() in ARMOR_EQUIP
    }

    private fun applyName() {
        val id = uuidOf(selected()) ?: return
        val v = nameField.value
        if (v.isBlank()) ItemCustomizationStore.removeItemName(id) else ItemCustomizationStore.setItemName(id, v)
    }

    private fun applyModel() {
        val sel = selected()
        val id = uuidOf(sel) ?: return
        val v = modelField.value.trim()
        if (v.isBlank() || v == defaultModel(sel) || net.minecraft.resources.Identifier.tryParse(v) == null) {
            ItemCustomizationStore.removeModelId(id)
        } else {
            ItemCustomizationStore.setModelId(id, v)
        }
    }

    private fun applyDye() {
        val sel = selected()
        val id = uuidOf(sel) ?: return
        if (!dyeAllowed(sel)) return
        val d = dyeField.value.trim()
        if (d.length == 6) {
            try {
                val rgb = d.toLong(16).toInt()
                ItemCustomizationStore.setDyeColor(id, (0xFF shl 24) or (rgb and 0xFFFFFF))
            } catch (ignored: NumberFormatException) {}
        } else if (d.isEmpty()) {
            ItemCustomizationStore.removeDyeColor(id)
        }
    }

    private fun applyTrim() {
        val id = uuidOf(selected()) ?: return
        if (trimMatDropdown.selected >= 0 && trimPatDropdown.selected >= 0 &&
            trimMaterials.isNotEmpty() && trimPatterns.isNotEmpty()
        ) {
            ItemCustomizationStore.setArmorTrim(
                id, ItemCustomizationStore.ArmorTrimId(trimMaterials[trimMatDropdown.selected], trimPatterns[trimPatDropdown.selected])
            )
        } else {
            ItemCustomizationStore.removeArmorTrim(id)
        }
    }

    private fun applyAll() { applyName(); applyModel(); applyDye(); applyTrim() }

    private fun reset() {
        val id = uuidOf(selected()) ?: return
        ItemCustomizationStore.removeItemName(id)
        ItemCustomizationStore.removeModelId(id)
        ItemCustomizationStore.removeDyeColor(id)
        ItemCustomizationStore.removeArmorTrim(id)
        ItemCustomizationStore.removeAnimatedDye(id)
        loadFields()
    }

    private var focusedField: EditBox? = null
    override fun removed() {
        if (focusedField === modelField) applyModel()
        super.removed()
    }

    private fun focusField(f: EditBox?) {
        if (focusedField === modelField && f !== modelField) applyModel()
        focusedField?.isFocused = false
        focusedField = f
        focusedField?.isFocused = true
    }

    private fun insertIntoName(code: String) {
        val t = nameField.value
        val cur = min(nameField.cursorPosition, t.length)
        val nt = t.substring(0, cur) + code + t.substring(cur)
        if (nt.length > 128) return
        nameField.setValue(nt)
        nameField.moveCursorTo(cur + code.length, false)
        focusField(nameField)
        applyName()
    }

    private fun select(idx: Int) {
        focusField(null)
        selectedIndex = idx
        loadFields()
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(ctx, mouseX, mouseY, delta)
        if (!ready || minecraft?.player == null) return
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        val hover = slotAt(mx, my)

        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)

        ScreenTheme.panel(ctx, panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 8, BG_PANEL, PANEL_BORDER)
        ctx.fill(panelX + 1, panelY + HEADER_H, panelX + PANEL_W - 1, panelY + HEADER_H + 1, PANEL_BORDER)
        ctx.fill(panelX + 1, footY - 7, panelX + PANEL_W - 1, footY - 6, PANEL_BORDER)
        ctx.fill(lx + LEFT_W + 8, panelY + HEADER_H + 1, lx + LEFT_W + 9, footY - 7, PANEL_BORDER)

        mcBox(ctx, lx, armBoxY, 4 * ARM_STEP + 12, CELL + 6)
        mcBox(ctx, lx, invBoxY, 9 * CELL + 8, 4 * CELL + 12)
        for (r in 0 until 4) slotBg(ctx, armSlotX(r), armSlotY(), ARMOR_SLOTS[r] == hover)
        for (i in 0 until mainCount()) slotBg(ctx, invSlotX(i), invSlotY(i), i == hover)

        val selIdx = ARMOR_SLOTS.indexOf(selectedIndex)
        if (selIdx >= 0) selRing(ctx, armSlotX(selIdx), armSlotY())
        else if (selectedIndex < mainCount()) selRing(ctx, invSlotX(selectedIndex), invSlotY(selectedIndex))

        for (r in 0 until 4) {
            val s = ARMOR_SLOTS[r]
            if (s < inv().containerSize) {
                val a = inv().getItem(s)
                if (!a.isEmpty) ctx.item(a, armSlotX(r) + 1, armSlotY() + 1)
            }
        }
        for (i in 0 until mainCount()) {
            val st = inv().getItem(i)
            if (!st.isEmpty) ctx.item(st, invSlotX(i) + 1, invSlotY(i) + 1)
        }

        ScreenTheme.roundedRect(ctx, lx, pvY, LEFT_W, 56, 6, BG_SECTION)
        val sel = selected()
        if (!sel.isEmpty) {
            ctx.pose().pushMatrix()
            ctx.pose().translate((lx + 10).toFloat(), (pvY + 12).toFloat())
            ctx.pose().scale(2f, 2f)
            ctx.item(sel, 0, 0)
            ctx.pose().popMatrix()
        }

        ctx.pose().popMatrix()
    }

    private fun mcBox(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        ctx.fill(x, y, x + w, y + h, MC_BG)
        ctx.fill(x, y, x + w - 1, y + 1, MC_EDGE)
        ctx.fill(x, y, x + 1, y + h - 1, MC_EDGE)
        ctx.fill(x + 1, y + h - 1, x + w, y + h, MC_EDGE)
        ctx.fill(x + w - 1, y + 1, x + w, y + h, MC_EDGE)
    }

    private fun slotBg(ctx: GuiGraphicsExtractor, x: Int, y: Int, hover: Boolean) {
        ctx.fill(x, y, x + CELL, y + CELL, MC_SLOT_EDGE)
        ctx.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, if (hover) MC_SLOT_HOVER else MC_SLOT)
    }

    private fun selRing(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        ctx.fill(x - 1, y - 1, x + CELL + 1, y + 1, ACCENT)
        ctx.fill(x - 1, y + CELL - 1, x + CELL + 1, y + CELL + 1, ACCENT)
        ctx.fill(x - 1, y - 1, x + 1, y + CELL + 1, ACCENT)
        ctx.fill(x + CELL - 1, y - 1, x + CELL + 1, y + CELL + 1, ACCENT)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = UiScale.vx(mouseX)
        val mouseY = UiScale.vx(mouseY)
        UiRecorder.clear()
        if (!ready || minecraft?.player == null) { super.extractRenderState(ctx, mouseX, mouseY, delta); return }
        drawChrome(mouseX, mouseY)
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        if (tab == Tab.TRIM && trimUsable(selected()) == null) {
            trimMatDropdown.renderOpen(mouseX, mouseY)
            trimPatDropdown.renderOpen(mouseX, mouseY)
        }
    }

    private fun trimUsable(sel: ItemStack): String? = when {
        uuidOf(sel) == null -> "This item has no Hypixel item id, so it can't be customised."
        trimMaterials.isEmpty() || trimPatterns.isEmpty() -> "Trim registries load once you are in a world."
        !isArmour(sel) -> "Trims only apply to armour. Pick an armour piece."
        else -> null
    }

    private fun drawChrome(mouseX: Int, mouseY: Int) {
        val sel = selected()
        val id = uuidOf(sel)

        ScreenTheme.nRoundedRect(panelX + 12, panelY + 8, 16, 16, 4, 0x3324B6B0)
        ScreenTheme.nst("Aa", panelX + 14, panelY + 12, ACCENT, 0.62f)
        ScreenTheme.nst("Item Customize", panelX + 34, panelY + 7, TEXT_PRIM, 0.85f)
        ScreenTheme.nst("Client-side only · only you see it", panelX + 34, panelY + 19, TEXT_HINT, 0.58f)
        drawTabs(mouseX, mouseY)

        ScreenTheme.nst("PICK ITEM", lx, pickY, ACCENT, 0.65f)
        val hov = slotAt(mouseX, mouseY)
        val hint = if (hov >= 0 && !inv().getItem(hov).isEmpty) clip(inv().getItem(hov).hoverName.string, LEFT_W - 50, 0.55f)
            else "click armour or a slot"
        ScreenTheme.nst(hint, lx + 48, pickY + 1, TEXT_HINT, 0.55f)
        ScreenTheme.nst("Armour", lx, armBoxY - 8, TEXT_HINT, 0.5f)
        ScreenTheme.nst("Inventory", lx, invBoxY - 8, TEXT_HINT, 0.5f)
        for (r in 0 until 4) ScreenTheme.nst(ARMOR_TAGS[r], armSlotX(r) - 8, armSlotY() + 5, MC_TAG, 0.6f)

        for (r in 0 until 4) {
            val s = ARMOR_SLOTS[r]
            if (s < inv().containerSize && hasCustom(inv().getItem(s))) marker(armSlotX(r), armSlotY())
        }
        for (i in 0 until mainCount()) if (hasCustom(inv().getItem(i))) marker(invSlotX(i), invSlotY(i))

        val px = lx + 52
        val custom = if (id != null) ItemCustomizationStore.getItemName(id) else null
        if (sel.isEmpty) {
            ScreenTheme.nst("Empty slot", px, pvY + 18, TEXT_HINT, 0.7f)
        } else {
            val raw = if (focusedField === nameField || !custom.isNullOrBlank()) nameField.value.ifBlank { null } else null
            if (raw != null) drawRuns(raw, px, pvY + 14, 0.75f, LEFT_W - 58)
            else ScreenTheme.nst(clip(sel.hoverName.string, LEFT_W - 58, 0.75f), px, pvY + 14, TEXT_PRIM, 0.75f)
            val tags = ArrayList<String>()
            if (id != null) {
                if (ItemCustomizationStore.getItemName(id) != null) tags.add("name")
                if (ItemCustomizationStore.getModelId(id) != null) tags.add("model")
                if (ItemCustomizationStore.getDyeColor(id) != null || ItemCustomizationStore.getAnimatedDye(id) != null) tags.add("dye")
                if (ItemCustomizationStore.getArmorTrim(id) != null) tags.add("trim")
            }
            val sub = when {
                id == null -> "Can't be customised"
                tags.isEmpty() -> "Not customised"
                else -> "Custom: " + tags.joinToString(" · ")
            }
            ScreenTheme.nst(sub, px, pvY + 30, if (tags.isEmpty()) TEXT_HINT else ACCENT, 0.55f)
        }

        ScreenTheme.nst("Editing", rx, pickY, TEXT_HINT, 0.6f)
        ScreenTheme.nst(clip(if (sel.isEmpty) "Nothing" else sel.hoverName.string, rw - 40, 0.7f), rx + 34, pickY - 1, TEXT_PRIM, 0.7f)
        if (!sel.isEmpty && id == null) ScreenTheme.nst("No Hypixel item id, so this item can't be customised.", rx, pickY + 11, WARN, 0.55f)
        else if (id != null) ScreenTheme.nst(clip(id, rw, 0.5f), rx, pickY + 11, TEXT_HINT, 0.5f)

        when (tab) {
            Tab.NAME -> drawNameTab(mouseX, mouseY)
            Tab.MODEL -> drawModelTab(mouseX, mouseY)
            Tab.DYE -> drawDyeTab(mouseX, mouseY)
            Tab.TRIM -> drawTrimTab(mouseX, mouseY)
        }

        if (id != null) drawButton(resetRect(), "Reset item", mouseX, mouseY, DANGER, filled = false)
        val applied = System.currentTimeMillis() - appliedAt < 900
        drawButton(applyRect(), if (applied) "Applied" else "Apply", mouseX, mouseY, ACCENT, filled = false)
        drawButton(doneRect(), "Done", mouseX, mouseY, ACCENT, filled = true)
    }

    private fun marker(x: Int, y: Int) {
        UiRecorder.disc((x + CELL - 4).toFloat(), (y + 4).toFloat(), 2.6f, 0xFF0E1016.toInt())
        UiRecorder.disc((x + CELL - 4).toFloat(), (y + 4).toFloat(), 1.9f, ACCENT)
    }

    private fun drawTabs(mouseX: Int, mouseY: Int) {
        tabRects.clear()
        val tw = 46; val th = 18
        val tabs = Tab.values()
        var x = panelX + PANEL_W - 14 - tabs.size * tw - 4
        val y = panelY + 7
        ScreenTheme.nRoundedRectRing(x, y, tabs.size * tw + 4, th, th / 2, 1, FIELD_BG, FIELD_BORDER)
        x += 2
        for (t in tabs) {
            val active = t == tab
            val hov = inBox(mouseX, mouseY, x, y, tw, th)
            if (active) ScreenTheme.nRoundedRect(x, y + 2, tw, th - 4, (th - 4) / 2, ACCENT)
            else if (hov) ScreenTheme.nRoundedRect(x, y + 2, tw, th - 4, (th - 4) / 2, ROW_HOVER)
            val lw = ScreenTheme.nstw(t.label, 0.65f)
            ScreenTheme.nst(t.label, x + (tw - lw) / 2, y + 6, if (active) ACCENT_INK else if (hov) TEXT_PRIM else TEXT_HINT, 0.65f)
            tabRects.add(intArrayOf(x, y, tw, th, t.ordinal))
            x += tw
        }
    }

    private fun drawNameTab(mouseX: Int, mouseY: Int) {
        keyRects.clear(); keyCodes.clear()
        val y0 = contentY
        ScreenTheme.nst("Custom name", rx, y0, TEXT_HINT, 0.6f)
        ScreenTheme.nTextField(nameField, focusedField === nameField, rx, y0 + 10, rw, 20, 9f)
        if (nameField.value.isEmpty() && focusedField !== nameField)
            ScreenTheme.nst(clip(selected().hoverName.string, rw - 8, 0.7f), rx + 4, y0 + 16, 0xFF5A6470.toInt(), 0.7f)

        val cw = 22; val ch = 14; val gap = 3
        var x = rx; var y = y0 + 38
        fun chip(code: String, label: String, textCol: Int, ring: Int, fill: Int) {
            if (x + cw > rx + rw) { x = rx; y += ch + gap }
            val hov = inBox(mouseX, mouseY, x, y, cw, ch)
            ScreenTheme.nRoundedRectRing(x, y, cw, ch, 3, 1, if (hov) ROW_HOVER else fill, if (hov) ACCENT else ring)
            val lw = ScreenTheme.nstw(label, 0.6f)
            ScreenTheme.nst(label, x + (cw - lw) / 2, y + 3, textCol, 0.6f)
            keyRects.add(intArrayOf(x, y, cw, ch)); keyCodes.add(code)
            x += cw + gap
        }
        for (c in CODE_COLORS) {
            val code = c[0].toChar(); val rgb = c[1]
            val col = 0xFF000000.toInt() or rgb
            if (brightness(rgb) < 60) chip("&$code", "&$code", 0xFFFFFFFF.toInt(), col, col)
            else chip("&$code", "&$code", col, (0x66 shl 24) or rgb, FIELD_BG)
        }
        for (f in CODE_FORMATS) chip("&$f", "&$f", TEXT_PRIM, FIELD_BORDER, FIELD_BG)

        val pvy = y + ch + 10
        ScreenTheme.nst("Preview", rx, pvy, TEXT_HINT, 0.6f)
        ScreenTheme.nRoundedRectRing(rx, pvy + 10, rw, 22, 4, 1, LIST_BG, FIELD_BORDER)
        if (nameField.value.isEmpty()) ScreenTheme.nst(clip(selected().hoverName.string, rw - 12, 0.8f), rx + 6, pvy + 16, TEXT_HINT, 0.8f)
        else drawRuns(nameField.value, rx + 6, pvy + 16, 0.8f, rw - 12)
        ScreenTheme.nst("Click a code to insert it at the cursor. &l bold, &o italic, &n underline,", rx, pvy + 38, TEXT_HINT, 0.55f)
        ScreenTheme.nst("&m strike, &k magic, &r reset, &* a star in the colour before it.", rx, pvy + 47, TEXT_HINT, 0.55f)
    }

    private fun drawModelTab(mouseX: Int, mouseY: Int) {
        val y0 = contentY
        val def = defaultModel(selected())
        ScreenTheme.nst("Model", rx, y0, TEXT_HINT, 0.6f)
        ScreenTheme.nTextField(modelField, focusedField === modelField, rx, y0 + 10, rw, 20, 9f)
        ScreenTheme.nst("Any item id, like minecraft:diamond_sword.", rx, y0 + 36, TEXT_HINT, 0.55f)
        ScreenTheme.nst(clip("Leave it as $def to keep the normal look.", rw, 0.55f), rx, y0 + 45, TEXT_HINT, 0.55f)
        val v = modelField.value.trim()
        if (v.isNotEmpty() && net.minecraft.resources.Identifier.tryParse(v) == null)
            ScreenTheme.nst("Not a valid id, so the default model is kept.", rx, y0 + 80, WARN, 0.55f)
        if (def.isNotEmpty() && v != def) drawButton(modelDefaultRect(), "Use default", mouseX, mouseY, ACCENT, filled = false)
    }

    private fun drawDyeTab(mouseX: Int, mouseY: Int) {
        val y0 = contentY
        val sel = selected()
        ScreenTheme.nst("Dye", rx, y0, TEXT_HINT, 0.6f)
        if (uuidOf(sel) == null) { note("This item has no Hypixel item id, so it can't be dyed.", y0 + 12); return }
        if (!dyeAllowed(sel)) {
            note("${clip(sel.hoverName.string, rw - 80, 0.6f)} can't be dyed.", y0 + 12)
            note("Pick leather armour or another dyeable piece.", y0 + 24)
            return
        }
        val cur = dyeField.value.trim().uppercase()
        val cols = dyeCols()
        var hovered = -1
        for (i in DYES.indices) {
            val x = rx + (i % cols) * (SWATCH + SWATCH_GAP)
            val y = y0 + 10 + (i / cols) * (SWATCH + SWATCH_GAP)
            val hov = inBox(mouseX, mouseY, x, y, SWATCH, SWATCH)
            if (hov) hovered = i
            val col = 0xFF000000.toInt() or DYE_RGB[i]
            val isSel = cur == DYES[i][1]
            if (isSel) ScreenTheme.nRoundedRectRing(x, y, SWATCH, SWATCH, 4, 2, col, 0xFFFFFFFF.toInt())
            else ScreenTheme.nRoundedRectRing(x, y, SWATCH, SWATCH, 4, 1, col, if (hov) ACCENT else 0x33FFFFFF)
        }
        val gridBot = y0 + 10 + dyeRows() * (SWATCH + SWATCH_GAP)
        val curIdx = if (cur.length == 6) DYES.indexOfFirst { it[1] == cur } else -1
        val label = when {
            hovered >= 0 -> DYES[hovered][0]
            curIdx >= 0 -> DYES[curIdx][0]
            cur.length == 6 -> "Custom colour"
            else -> "No dye set"
        }
        ScreenTheme.nst(label, rx, gridBot + 1, if (hovered >= 0) TEXT_PRIM else TEXT_HINT, 0.6f)

        val hy = dyeHexY()
        val dot = if (cur.length == 6) cur.toLongOrNull(16)?.toInt() else null
        if (dot != null) ScreenTheme.nRoundedRectRing(rx, hy + 2, 14, 14, 7, 1, 0xFF000000.toInt() or dot, 0x55FFFFFF)
        else ScreenTheme.nRoundedRectRing(rx, hy + 2, 14, 14, 7, 1, FIELD_BG, FIELD_BORDER)
        ScreenTheme.nst("#", rx + 19, hy + 5, TEXT_HINT, 0.7f)
        ScreenTheme.nTextField(dyeField, focusedField === dyeField, rx + 28, hy, 70, 18, 9f)
        ScreenTheme.nst("Hex, like E7413C", rx + 106, hy + 5, TEXT_HINT, 0.55f)
        if (cur.isNotEmpty()) drawButton(dyeClearRect(), "Clear dye", mouseX, mouseY, DANGER, filled = false)
    }

    private fun drawTrimTab(mouseX: Int, mouseY: Int) {
        val y0 = contentY
        val reason = trimUsable(selected())
        if (reason != null) {
            ScreenTheme.nst("Trim", rx, y0, TEXT_HINT, 0.6f)
            note(reason, y0 + 12)
            return
        }
        ScreenTheme.nst("Material", trimMatDropdown.x, y0, TEXT_HINT, 0.6f)
        ScreenTheme.nst("Pattern", trimPatDropdown.x, y0, TEXT_HINT, 0.6f)
        trimMatDropdown.renderClosed(mouseX, mouseY)
        trimPatDropdown.renderClosed(mouseX, mouseY)
        val set = trimMatDropdown.selected >= 0 && trimPatDropdown.selected >= 0
        if (set) {
            if (!trimMatDropdown.open && !trimPatDropdown.open) drawButton(trimClearRect(), "Clear trim", mouseX, mouseY, DANGER, filled = false)
        } else {
            ScreenTheme.nst("Pick a material and a pattern to apply a trim.", rx, y0 + 38, TEXT_HINT, 0.55f)
        }
    }

    private fun note(s: String, y: Int) {
        ScreenTheme.nst(s, rx, y, TEXT_HINT, 0.6f)
    }

    private fun drawRuns(raw: String, x: Int, y: Int, scale: Float, maxW: Int) {
        UiRecorder.pushScissor(x.toFloat(), (y - 2).toFloat(), maxW.toFloat(), 14f)
        var px = x
        for ((text, color) in LegacyFormatting.previewRuns(raw)) {
            ScreenTheme.nst(text, px, y, color, scale)
            px += ScreenTheme.nstw(text, scale)
        }
        UiRecorder.popScissor()
    }

    private fun drawButton(r: ClickRect, label: String, mx: Int, my: Int, accent: Int, filled: Boolean) {
        val hov = r.hit(mx, my)
        val hoverAccent = if (accent == DANGER) DANGER_HOVER else ACCENT_HOVER
        val tw = ScreenTheme.nstw(label, 0.7f)
        val ty = r.y + (r.h - 7) / 2
        if (filled) {
            ScreenTheme.nRoundedRect(r.x, r.y, r.w, r.h, r.h / 2, if (hov) hoverAccent else accent)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, ty, ACCENT_INK, 0.7f)
        } else {
            ScreenTheme.nRoundedRectRing(r.x, r.y, r.w, r.h, r.h / 2 - 1, 1, if (hov) BG_SECTION else FIELD_BG, if (hov) hoverAccent else accent)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, ty, if (hov) hoverAccent else TEXT_PRIM, 0.7f)
        }
    }

    private fun clip(s: String, maxW: Int, scale: Float): String {
        if (ScreenTheme.nstw(s, scale) <= maxW) return s
        var out = s
        while (out.length > 1 && ScreenTheme.nstw("$out...", scale) > maxW) out = out.substring(0, out.length - 1)
        return "$out..."
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        if (!ready || minecraft?.player == null) return super.mouseClicked(click, bl)
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        val sel = selected()

        if (tab == Tab.TRIM && trimUsable(sel) == null) {
            if (trimMatDropdown.click(mx, my)) { closeOthers(trimMatDropdown); focusField(null); return true }
            if (trimPatDropdown.click(mx, my)) { closeOthers(trimPatDropdown); focusField(null); return true }
            if (trimMatDropdown.selected >= 0 && trimPatDropdown.selected >= 0 && trimClearRect().hit(mx, my)) {
                trimMatDropdown.selected = -1; trimPatDropdown.selected = -1; applyTrim(); return true
            }
        }

        for (r in tabRects) {
            if (inBox(mx, my, r[0], r[1], r[2], r[3])) {
                tab = Tab.values()[r[4]]
                trimMatDropdown.close(); trimPatDropdown.close()
                focusField(if (tab == Tab.NAME) nameField else null)
                return true
            }
        }

        val slot = slotAt(mx, my)
        if (slot >= 0) {
            if (!inv().getItem(slot).isEmpty) select(slot)
            return true
        }

        when (tab) {
            Tab.NAME -> {
                for (i in keyRects.indices) {
                    val r = keyRects[i]
                    if (inBox(mx, my, r[0], r[1], r[2], r[3])) { insertIntoName(keyCodes[i]); return true }
                }
                if (inBox(mx, my, rx, contentY + 10, rw, 20)) { focusField(nameField); return true }
            }
            Tab.MODEL -> {
                if (inBox(mx, my, rx, contentY + 10, rw, 20)) { focusField(modelField); return true }
                val def = defaultModel(sel)
                if (def.isNotEmpty() && modelField.value.trim() != def && modelDefaultRect().hit(mx, my)) {
                    modelField.setValue(def); applyModel(); focusField(null); return true
                }
            }
            Tab.DYE -> if (uuidOf(sel) != null && dyeAllowed(sel)) {
                val cols = dyeCols()
                for (i in DYES.indices) {
                    val x = rx + (i % cols) * (SWATCH + SWATCH_GAP)
                    val y = contentY + 10 + (i / cols) * (SWATCH + SWATCH_GAP)
                    if (inBox(mx, my, x, y, SWATCH, SWATCH)) { dyeField.setValue(DYES[i][1]); applyDye(); focusField(null); return true }
                }
                if (inBox(mx, my, rx + 28, dyeHexY(), 70, 18)) { focusField(dyeField); return true }
                if (dyeField.value.isNotBlank() && dyeClearRect().hit(mx, my)) { dyeField.setValue(""); applyDye(); focusField(null); return true }
            }
            Tab.TRIM -> {}
        }

        if (uuidOf(sel) != null && resetRect().hit(mx, my)) { focusField(null); reset(); return true }
        if (applyRect().hit(mx, my)) { focusField(null); applyAll(); appliedAt = System.currentTimeMillis(); return true }
        if (doneRect().hit(mx, my)) { onClose(); return true }

        focusField(null)
        return super.mouseClicked(click, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!ready) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        val mx = UiScale.vx(mouseX); val my = UiScale.vx(mouseY)
        if (trimMatDropdown.scrolled(mx, my, verticalAmount)) return true
        if (trimPatDropdown.scrolled(mx, my, verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val f = focusedField
        if (f != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) { focusField(null); return true }
            f.keyPressed(input)
            if (f === dyeField) applyDye()
            if (f === nameField) applyName()
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        val f = focusedField
        if (f != null) {
            f.charTyped(input)
            if (f === dyeField) applyDye()
            if (f === nameField) applyName()
            return true
        }
        return super.charTyped(input)
    }

    private fun closeOthers(keep: Dropdown) {
        if (trimMatDropdown !== keep) trimMatDropdown.close()
        if (trimPatDropdown !== keep) trimPatDropdown.close()
    }

    override fun isPauseScreen(): Boolean = false

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, UiScale.factor())
    }

    private inner class Dropdown(val placeholder: String, val x: Int, val y: Int, val w: Int) {
        val boxH = 20
        val rowH = 17
        val maxVisible = 7
        val labels = ArrayList<String>()
        var selected = -1
        var open = false
        var scroll = 0
        var onChange: Runnable? = null

        fun close() { open = false; scroll = 0 }
        private fun rowsShown(): Int = min(maxVisible, labels.size)

        fun renderClosed(mx: Int, my: Int) {
            val hov = inBox(mx, my, x, y, w, boxH)
            ScreenTheme.nRoundedRectRing(x, y, w, boxH, 4, 1, FIELD_BG, if (open) ACCENT else if (hov) ACCENT_HOVER else FIELD_BORDER)
            val label = if (selected in labels.indices) labels[selected] else placeholder
            val col = if (selected in labels.indices) TEXT_PRIM else TEXT_HINT
            ScreenTheme.nst(clip(label, w - 18, 0.65f), x + 6, y + 6, col, 0.65f)
            UiRecorder.chevron((x + w - 12).toFloat(), (y + boxH / 2).toFloat(), true, TEXT_HINT)
        }

        fun renderOpen(mx: Int, my: Int) {
            if (!open) return
            val rows = rowsShown()
            val ly = y + boxH + 2
            val lh = rows * rowH
            UiRecorder.dropShadow(x.toFloat(), ly.toFloat(), w.toFloat(), lh.toFloat(), 4f, 6f, 0x66000000)
            ScreenTheme.nRoundedRectRing(x, ly, w, lh, 4, 1, LIST_BG, FIELD_BORDER)
            for (r in 0 until rows) {
                val idx = scroll + r
                if (idx >= labels.size) break
                val ry = ly + r * rowH
                if (inBox(mx, my, x, ry, w, rowH)) ScreenTheme.nRect(x + 1, ry, w - 2, rowH, ROW_HOVER)
                val col = if (idx == selected) ACCENT else TEXT_PRIM
                ScreenTheme.nst(clip(labels[idx], w - 10, 0.62f), x + 6, ry + 5, col, 0.62f)
            }
        }

        fun click(mx: Int, my: Int): Boolean {
            if (inBox(mx, my, x, y, w, boxH)) { open = !open; if (open) scroll = 0; return true }
            if (open) {
                val rows = rowsShown()
                val ly = y + boxH + 2
                if (inBox(mx, my, x, ly, w, rows * rowH)) {
                    val idx = scroll + (my - ly) / rowH
                    if (idx in labels.indices) { selected = idx; open = false; onChange?.run() }
                    return true
                }
                open = false
            }
            return false
        }

        fun scrolled(mx: Int, my: Int, amount: Double): Boolean {
            if (!open) return false
            val rows = rowsShown()
            val ly = y + boxH + 2
            if (inBox(mx, my, x, ly, w, rows * rowH)) {
                val max = max(0, labels.size - maxVisible)
                scroll = max(0, min(max, scroll - Math.signum(amount).toInt()))
                return true
            }
            return false
        }
    }
}
