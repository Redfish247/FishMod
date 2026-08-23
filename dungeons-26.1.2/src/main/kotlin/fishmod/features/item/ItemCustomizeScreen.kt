package fishmod.features.item

import fishmod.features.HasNvgOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.data.ItemUtil
import fishmod.utils.data.LegacyFormatting
import fishmod.utils.rendering.NvgContext
import fishmod.utils.rendering.NvgGlStateGuard
import fishmod.utils.rendering.NvgRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NanoVG
import kotlin.math.max
import kotlin.math.min

/**
 * /fm customize — a clearer, friendlier item customizer (restored flat layout, replacing the
 * tabbed/animated-dye design). PICK an item (worn armor or inventory slot), then edit its Name,
 * Dye and armor Trim. Backed by [ItemCustomizationStore] (client-only, keyed by the item's
 * Hypixel instance uuid) — the same persistence used by [DyedItemColorMixin]/[ItemTrimMixin]/
 * [ItemStackMixin], so item-model and head-skin overrides from the original pre-port screen are
 * intentionally out of scope (no persistence/render path for them anymore).
 *
 * Painted entirely through [NvgRecorder] following [fishmod.features.FishModScreen]'s pattern —
 * widget interaction (EditBox focus, grid/legend/dropdown hit-testing) stays plain Screen code.
 */
class ItemCustomizeScreen : Screen(Component.literal("Item Customize")), HasNvgOverlay {

    private companion object {
        const val CELL = 22

        val BG_PANEL = 0xF20E1016.toInt()
        val BG_SECTION = 0xFF171A22.toInt()
        val PANEL_BORDER = 0xFF2A2D38.toInt()
        val FIELD_BG = 0xFF1B1E27.toInt()
        val FIELD_BORDER = 0xFF2E333D.toInt()
        val ACCENT = ScreenTheme.ACCENT
        val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        val TEXT_PRIM = ScreenTheme.TEXT_COLOR
        val TEXT_HINT = ScreenTheme.SUBTEXT_COLOR
        val SLOT_BG = 0xFF2A2D38.toInt()
        val SLOT_SEL = 0xFF55FF55.toInt()
        val ROW_HOVER = 0xFF2A2D38.toInt()
        val LIST_BG = 0xFF14161D.toInt()
        val DANGER = ScreenTheme.DANGER
        val DANGER_HOVER = ScreenTheme.DANGER_HOVER

        // &-code → RGB for the clickable color key (matches the main /fm legend).
        val CODE_COLORS: Array<IntArray> = arrayOf(
            intArrayOf('0'.code, 0x000000), intArrayOf('1'.code, 0x0000AA), intArrayOf('2'.code, 0x00AA00), intArrayOf('3'.code, 0x00AAAA),
            intArrayOf('4'.code, 0xAA0000), intArrayOf('5'.code, 0xAA00AA), intArrayOf('6'.code, 0xFFAA00), intArrayOf('7'.code, 0xAAAAAA),
            intArrayOf('8'.code, 0x555555), intArrayOf('9'.code, 0x5555FF), intArrayOf('a'.code, 0x55FF55), intArrayOf('b'.code, 0x55FFFF),
            intArrayOf('c'.code, 0xFF5555), intArrayOf('d'.code, 0xFF55FF), intArrayOf('e'.code, 0xFFFF55), intArrayOf('f'.code, 0xFFFFFF)
        )
        val CODE_FORMATS: Array<Array<String>> = arrayOf(
            arrayOf("l", "B"), arrayOf("o", "I"), arrayOf("n", "U"), arrayOf("m", "S"), arrayOf("k", "K"), arrayOf("r", "R")
        )

        // Hypixel SkyBlock dyes (name, RRGGBB). Not exhaustive — the hex box covers anything missing.
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

        fun dyeIndex(rgbIn: Int): Int {
            val rgb = rgbIn and 0xFFFFFF
            for (i in DYE_RGB.indices) if ((DYE_RGB[i] and 0xFFFFFF) == rgb) return i
            return -1
        }

        fun cap(s: String): String = if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

        fun inBox(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
            mx >= x && mx <= x + w && my >= y && my <= y + h

        fun brightness(rgb: Int): Int {
            val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
    }

    private var panelX = 0
    private var panelY = 0
    private val panelW = 440
    private val panelH = 434
    private var gridX = 0
    private var gridY = 0
    private var armorX = 0
    private var armorY = 0
    private var legendX = 0
    private var legendY = 0
    private var selectedIndex = 0
    private lateinit var nameField: EditBox
    private lateinit var modelField: EditBox
    private lateinit var dyeField: EditBox
    private lateinit var dyeDropdown: Dropdown
    private lateinit var trimMatDropdown: Dropdown
    private lateinit var trimPatDropdown: Dropdown

    private val trimMaterials by lazy { ArmorTrimCache.materials() }
    private val trimPatterns by lazy { ArmorTrimCache.patterns() }

    // &-code key hit-boxes, rebuilt each frame, consumed by mouseClicked.
    private val keyRects = ArrayList<IntArray>()
    private val keyCodes = ArrayList<String>()

    private fun inv(): Inventory = minecraft!!.player!!.inventory
    private fun mainCount(): Int = min(36, inv().containerSize)

    override fun init() {
        if (minecraft == null || minecraft!!.player == null) return

        val vw = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
        val vh = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
        panelX = (vw - panelW) / 2
        panelY = max(8, (vh - panelH) / 2)

        val held = minecraft!!.player!!.mainHandItem
        for (i in 0 until mainCount()) if (inv().getItem(i) === held) { selectedIndex = i; break }

        val p = panelX + 14
        val contentW = panelW - 28

        val pickY = panelY + 58
        armorX = p
        armorY = pickY + 16
        gridX = p + 4 * CELL + 12
        gridY = armorY

        legendX = p
        legendY = panelY + 192

        val labelW = 46
        val fx = p + labelW
        val fw = contentW - labelW

        val nameY = legendY + 58
        val dyeY = nameY + 28
        val trimY = dyeY + 28
        val modelY = trimY + 28

        // Kept only for value/cursor state — never added as a Screen widget (its own
        // extractRenderState() would flush before the NanoVG overlay and be invisible under it).
        nameField = EditBox(this.font, fx, nameY, fw, 18, Component.literal("Name"))
        nameField.setMaxLength(128)
        nameField.setBordered(false)

        modelField = EditBox(this.font, fx, modelY, fw, 18, Component.literal("Model"))
        modelField.setMaxLength(64)
        modelField.setBordered(false)

        dyeField = EditBox(this.font, fx + 190, dyeY, fw - 190, 18, Component.literal("Hex"))
        dyeField.setMaxLength(6)
        dyeField.setBordered(false)

        dyeDropdown = Dropdown("Pick a dye...", fx, dyeY, 184)
        for (d in DYES) dyeDropdown.labels.add(d[0])
        dyeDropdown.swatches = DYE_RGB
        dyeDropdown.onChange = Runnable {
            if (dyeDropdown.selected >= 0) { dyeField.setValue(DYES[dyeDropdown.selected][1]); applyDye() }
        }

        val half = (fw - 6) / 2
        trimMatDropdown = Dropdown("Material", fx, trimY, half)
        for (s in trimMaterials) trimMatDropdown.labels.add(cap(s.substringAfterLast(':')))
        trimMatDropdown.onChange = Runnable { applyTrim() }
        trimPatDropdown = Dropdown("Pattern", fx + half + 6, trimY, half)
        for (s in trimPatterns) trimPatDropdown.labels.add(cap(s.substringAfterLast(':')))
        trimPatDropdown.onChange = Runnable { applyTrim() }

        val btnY = panelY + panelH - 30
        val btnW = 90
        val btnX = panelX + (panelW - btnW * 3 - 12) / 2
        resetRect = ClickRect(btnX, btnY, btnW, 20) { reset() }
        doneRect = ClickRect(btnX + btnW + 6, btnY, btnW, 20) { onClose() }
        applyRect = ClickRect(btnX + (btnW + 6) * 2, btnY, btnW, 20) { applyAll() }

        loadFields()
    }

    private class ClickRect(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun hit(mx: Int, my: Int) = inBox(mx, my, x, y, w, h)
    }

    private var resetRect: ClickRect? = null
    private var doneRect: ClickRect? = null
    private var applyRect: ClickRect? = null

    // ── load / apply / reset ───────────────────────────────────────────────────

    private fun uuidOf(st: ItemStack): String? = if (st.isEmpty) null else ItemUtil.getUuid(st)

    private fun loadFields() {
        val sel = inv().getItem(selectedIndex)
        val id = uuidOf(sel)

        nameField.setValue(if (id != null) ItemCustomizationStore.getItemName(id) ?: "" else "")

        val defaultModel = if (!sel.isEmpty) net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sel.item).toString() else ""
        modelField.setValue(if (id != null) ItemCustomizationStore.getModelId(id) ?: defaultModel else defaultModel)

        val dye = if (id != null) ItemCustomizationStore.getDyeColor(id) else null
        dyeField.setValue(if (dye != null) String.format("%06X", dye and 0xFFFFFF) else "")
        dyeDropdown.selected = if (dye != null) dyeIndex(dye) else -1

        val trim = if (id != null) ItemCustomizationStore.getArmorTrim(id) else null
        trimMatDropdown.selected = if (trim != null) trimMaterials.indexOf(trim.material) else -1
        trimPatDropdown.selected = if (trim != null) trimPatterns.indexOf(trim.pattern) else -1

        dyeDropdown.close(); trimMatDropdown.close(); trimPatDropdown.close()
    }

    private fun dyeAllowed(st: ItemStack?): Boolean {
        if (st == null || st.isEmpty) return false
        return try { st.has(net.minecraft.core.component.DataComponents.DYED_COLOR) } catch (e: Exception) { false }
    }

    private fun applyName() {
        val id = uuidOf(inv().getItem(selectedIndex)) ?: return
        val v = nameField.value
        if (v.isBlank()) ItemCustomizationStore.removeItemName(id) else ItemCustomizationStore.setItemName(id, v)
    }

    private fun applyModel() {
        val sel = inv().getItem(selectedIndex)
        val id = uuidOf(sel) ?: return
        val v = modelField.value.trim()
        val defaultModel = if (!sel.isEmpty) net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sel.item).toString() else ""
        if (v.isBlank() || v == defaultModel || net.minecraft.resources.Identifier.tryParse(v) == null) {
            ItemCustomizationStore.removeModelId(id)
        } else {
            ItemCustomizationStore.setModelId(id, v)
        }
    }

    private fun applyDye() {
        val sel = inv().getItem(selectedIndex)
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
        val id = uuidOf(inv().getItem(selectedIndex)) ?: return
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
        val id = uuidOf(inv().getItem(selectedIndex)) ?: return
        ItemCustomizationStore.removeItemName(id)
        ItemCustomizationStore.removeModelId(id)
        ItemCustomizationStore.removeDyeColor(id)
        ItemCustomizationStore.removeArmorTrim(id)
        loadFields()
    }

    /** Manually-tracked field focus — the fields are never added to the Screen's widget list. */
    private var focusedField: EditBox? = null
    private fun focusField(f: EditBox?) {
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

    // ── render ─────────────────────────────────────────────────────────────────

    /** Item icons are real 3D-rendered models (immediate GL) — NanoVG can't reproduce them, so the
     *  panel backdrop + slot grid (the only area actual item icons sit on top of) stays on the
     *  normal immediate GuiGraphics path via extractBackground, drawn before the icons. Everything
     *  else (header/labels/legend/dropdowns/fields/buttons) is a NanoVG overlay drawn after, which
     *  only ever paints thin chrome (borders, text) that doesn't need to cover the item pixels. */
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(ctx, mouseX, mouseY, delta)
        if (minecraft?.player == null) return

        // panelX/panelY etc. are in virtual (pre-shrink) space; scale the pose so this immediate
        // GL path (item icons can't go through NvgRecorder) lines up with the NanoVG chrome.
        val scale = fishmod.utils.rendering.UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)

        ScreenTheme.panel(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, BG_PANEL, PANEL_BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION)
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT)

        val armorSlots = intArrayOf(39, 38, 37, 36)
        for (r in 0 until 4) {
            val s = armorSlots[r]
            val x = armorX + r * CELL
            val y = armorY
            if (s == selectedIndex) ScreenTheme.roundedRectRing(ctx, x - 1, y - 1, 18, 18, 3, 1, SLOT_BG, SLOT_SEL)
            else ScreenTheme.roundedRect(ctx, x, y, 16, 16, 2, SLOT_BG)
            if (s < inv().containerSize) {
                val a = inv().getItem(s)
                if (!a.isEmpty) ctx.item(a, x, y)
            }
        }
        for (i in 0 until mainCount()) {
            val col = i % 9
            val row = if (i < 9) 3 else (i - 9) / 9
            val x = gridX + col * CELL
            val y = gridY + row * CELL
            if (i == selectedIndex) ScreenTheme.roundedRectRing(ctx, x - 1, y - 1, 18, 18, 3, 1, SLOT_BG, SLOT_SEL)
            else ScreenTheme.roundedRect(ctx, x, y, 16, 16, 2, SLOT_BG)
            val st = inv().getItem(i)
            if (!st.isEmpty) ctx.item(st, x, y)
        }

        ctx.pose().popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = fishmod.utils.rendering.UiScale.vx(mouseX)
        val mouseY = fishmod.utils.rendering.UiScale.vx(mouseY)
        NvgRecorder.clear()
        drawChrome(mouseX, mouseY)
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        // Open dropdown lists float above everything else.
        val sel = if (minecraft?.player != null) inv().getItem(selectedIndex) else ItemStack.EMPTY
        if (dyeAllowed(sel)) dyeDropdown.renderOpen(mouseX, mouseY)
        trimMatDropdown.renderOpen(mouseX, mouseY)
        trimPatDropdown.renderOpen(mouseX, mouseY)
    }

    private fun drawChrome(mouseX: Int, mouseY: Int) {
        if (minecraft?.player == null) return
        val sel = inv().getItem(selectedIndex)
        val p = panelX + 14

        ScreenTheme.nst("Item Customize", panelX + 14, panelY + 7, TEXT_PRIM)

        val curY = panelY + 28
        ScreenTheme.nst("Editing:", p, curY, TEXT_HINT)
        ScreenTheme.nst(sel.hoverName.string, p + 44, curY, TEXT_PRIM)
        val idText = ItemUtil.getUuid(sel) ?: "(no Hypixel instance uuid — can't be customized)"
        ScreenTheme.nst(idText, p, curY + 11, TEXT_HINT, 0.65f)

        val pickY = panelY + 58
        ScreenTheme.nst("PICK ITEM — click armor or a slot", p, pickY, ACCENT)

        val armorSlots = intArrayOf(39, 38, 37, 36)
        val armorTags = arrayOf("H", "C", "L", "B")
        for (r in 0 until 4) {
            val x = armorX + r * CELL
            val y = armorY
            ScreenTheme.nst(armorTags[r], x + 5, y + 18, TEXT_HINT, 0.65f)
        }

        ScreenTheme.nst("CUSTOMIZE", p, panelY + 168, ACCENT)
        drawColorKey(mouseX, mouseY)

        val labelW = 46
        val fx = p + labelW
        val fw = panelW - 28 - labelW
        val nameY = legendY + 58
        val dyeY = nameY + 28
        val trimY = dyeY + 28
        val modelY = trimY + 28

        drawLabel("Name", p, nameY)
        drawLabel("Dye", p, dyeY)
        drawLabel("Trim", p, trimY)
        drawLabel("Model", p, modelY)

        ScreenTheme.nTextField(nameField, focusedField === nameField, fx, nameY - 3, fw, 20, 9f)
        ScreenTheme.nTextField(modelField, focusedField === modelField, fx, modelY - 3, fw, 20, 9f)

        if (dyeAllowed(sel)) {
            dyeDropdown.renderClosed(mouseX, mouseY)
            ScreenTheme.nst("#", fx + 184, dyeY + 3, TEXT_HINT, 0.65f)
            ScreenTheme.nTextField(dyeField, focusedField === dyeField, fx + 190, dyeY - 3, fw - 190, 20, 9f)
        } else {
            ScreenTheme.nRect(fx, dyeY, fw, 14, FIELD_BG)
            ScreenTheme.nst("dyeable items only", fx + 4, dyeY + 3, TEXT_HINT, 0.65f)
        }

        if (trimMaterials.isEmpty() || trimPatterns.isEmpty()) {
            ScreenTheme.nRect(fx, trimY, fw, 14, FIELD_BG)
            ScreenTheme.nst("trim registries unavailable (must be in-world)", fx + 4, trimY + 3, TEXT_HINT, 0.62f)
        } else {
            trimMatDropdown.renderClosed(mouseX, mouseY)
            trimPatDropdown.renderClosed(mouseX, mouseY)
        }

        val prevY = modelY + 28
        ScreenTheme.nst("Preview:", p, prevY, TEXT_HINT)
        var px = fx
        for ((text, color) in fishmod.utils.data.LegacyFormatting.previewRuns(nameField.value)) {
            ScreenTheme.nst(text, px, prevY, color)
            px += ScreenTheme.nstw(text)
        }
    }

    private fun drawLabel(s: String, x: Int, y: Int) {
        ScreenTheme.nst("$s:", x, y + 3, TEXT_PRIM, 0.65f)
    }

    /** Clickable color/format key. Click a color → inserts its code into the name at the cursor. */
    private fun drawColorKey(mouseX: Int, mouseY: Int) {
        keyRects.clear()
        keyCodes.clear()

        ScreenTheme.nst("&-codes (click to insert) — &* = star in the color before it", legendX, legendY - 11, TEXT_HINT, 0.62f)

        val sw = 20; val sh = 14; val gap = 3
        for (i in CODE_COLORS.indices) {
            val code = CODE_COLORS[i][0].toChar()
            val rgb = CODE_COLORS[i][1]
            val colX = legendX + (i % 8) * (sw + gap)
            val colY = legendY + (i / 8) * (sh + gap)
            val hov = inBox(mouseX, mouseY, colX, colY, sw, sh)
            ScreenTheme.nRoundedRectRing(colX, colY, sw, sh, 2, 1, (0xFF000000.toInt()) or rgb, if (hov) ACCENT else FIELD_BORDER)
            val textCol = if (brightness(rgb) > 140) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val cw = ScreenTheme.nstw(code.toString(), 0.62f)
            ScreenTheme.nst(code.toString(), colX + (sw - cw) / 2, colY + 2, textCol, 0.62f)
            keyRects.add(intArrayOf(colX, colY, sw, sh))
            keyCodes.add("&$code")
        }

        var fxr = legendX + 8 * (sw + gap) + 8
        val fyr = legendY
        for (f in CODE_FORMATS) {
            val sample = f[1]
            val w = ScreenTheme.nstw(sample, 0.62f) + 6
            val hov = inBox(mouseX, mouseY, fxr, fyr, w, sh)
            ScreenTheme.nRect(fxr, fyr, w, sh, if (hov) ROW_HOVER else FIELD_BG)
            ScreenTheme.nst(sample, fxr + 3, fyr + 2, TEXT_PRIM, 0.62f)
            keyRects.add(intArrayOf(fxr, fyr, w, sh))
            keyCodes.add("&" + f[0])
            fxr += w + gap
        }

        // Star button, on the row under the format codes.
        val starX = legendX + 8 * (sw + gap) + 8
        val starY = legendY + sh + gap
        val starLabel = "&* *"
        val starW = ScreenTheme.nstw(starLabel, 0.62f) + 8
        val hovStar = inBox(mouseX, mouseY, starX, starY, starW, sh)
        ScreenTheme.nRect(starX, starY, starW, sh, if (hovStar) ROW_HOVER else FIELD_BG)
        ScreenTheme.nst(starLabel, starX + 4, starY + 2, TEXT_PRIM, 0.62f)
        keyRects.add(intArrayOf(starX, starY, starW, sh))
        keyCodes.add("&*")
    }

    // ── input ──────────────────────────────────────────────────────────────────

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        if (minecraft?.player == null) return super.mouseClicked(click, bl)
        val mx = fishmod.utils.rendering.UiScale.vx(click.x())
        val my = fishmod.utils.rendering.UiScale.vx(click.y())
        val sel = inv().getItem(selectedIndex)

        if (dyeAllowed(sel) && dyeDropdown.click(mx, my)) { closeOthers(dyeDropdown); focusField(null); return true }
        if (trimMatDropdown.click(mx, my)) { closeOthers(trimMatDropdown); focusField(null); return true }
        if (trimPatDropdown.click(mx, my)) { closeOthers(trimPatDropdown); focusField(null); return true }

        for (i in keyRects.indices) {
            val r = keyRects[i]
            if (inBox(mx, my, r[0], r[1], r[2], r[3])) { insertIntoName(keyCodes[i]); return true }
        }

        val p = panelX + 14
        val labelW = 46
        val fx = p + labelW
        val fw = panelW - 28 - labelW
        val nameY = legendY + 58
        val dyeY = nameY + 28
        val trimY = dyeY + 28
        val modelY = trimY + 28
        if (inBox(mx, my, fx, nameY - 3, fw, 20)) { focusField(nameField); return true }
        if (dyeAllowed(sel) && inBox(mx, my, fx + 190, dyeY - 3, fw - 190, 20)) { focusField(dyeField); return true }
        if (inBox(mx, my, fx, modelY - 3, fw, 20)) { focusField(modelField); return true }

        val armorSlots = intArrayOf(39, 38, 37, 36)
        for (r in 0 until 4) {
            val x = armorX + r * CELL
            if (mx in x..(x + 16) && my in armorY..(armorY + 16)) {
                selectedIndex = armorSlots[r]; loadFields(); focusField(null); return true
            }
        }
        for (i in 0 until mainCount()) {
            val col = i % 9
            val row = if (i < 9) 3 else (i - 9) / 9
            val x = gridX + col * CELL
            val y = gridY + row * CELL
            if (mx in x..(x + 16) && my in y..(y + 16)) {
                selectedIndex = i; loadFields(); focusField(null); return true
            }
        }

        resetRect?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
        doneRect?.let { if (it.hit(mx, my)) { it.action(); return true } }
        applyRect?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }

        focusField(null)
        return super.mouseClicked(click, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(mouseX); val my = fishmod.utils.rendering.UiScale.vx(mouseY)
        if (dyeDropdown.scrolled(mx, my, verticalAmount)) return true
        if (trimMatDropdown.scrolled(mx, my, verticalAmount)) return true
        if (trimPatDropdown.scrolled(mx, my, verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val f = focusedField
        if (f != null) {
            f.keyPressed(input)
            if (f === dyeField) applyDye()
            if (f === nameField) applyName()
            if (f === modelField) applyModel()
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
            if (f === modelField) applyModel()
            return true
        }
        return super.charTyped(input)
    }

    private fun closeOthers(keep: Dropdown) {
        if (dyeDropdown !== keep) dyeDropdown.close()
        if (trimMatDropdown !== keep) trimMatDropdown.close()
        if (trimPatDropdown !== keep) trimPatDropdown.close()
    }

    override fun isPauseScreen(): Boolean = false

    // ── NanoVG overlay ───────────────────────────────────────────────────────────

    private val nvgGlState = NvgGlStateGuard()
    private var nvgFailureLogged = false

    override fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = NvgContext.get()
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay(fishmod.utils.rendering.UiScale.factor())
            NanoVG.nvgEndFrame(ctx)
        } catch (t: Throwable) {
            if (!nvgFailureLogged) {
                nvgFailureLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] ItemCustomizeScreen paintNvgOverlay failed", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }

    // ── lightweight dropdown, painted via ScreenTheme's NanoVG helpers ─────────────

    private inner class Dropdown(val placeholder: String, val x: Int, val y: Int, val w: Int) {
        val boxH = 18
        val rowH = 17
        val maxVisible = 7
        val labels = ArrayList<String>()
        var swatches: IntArray? = null
        var selected = -1
        var open = false
        var scroll = 0
        var onChange: Runnable? = null

        fun close() { open = false; scroll = 0 }
        private fun rowsShown(): Int = min(maxVisible, labels.size)

        fun renderClosed(mx: Int, my: Int) {
            val hov = inBox(mx, my, x, y, w, boxH)
            ScreenTheme.nRoundedRectRing(x, y, w, boxH, 3, 1, FIELD_BG, if (hov) FIELD_BORDER else FIELD_BORDER)
            if (hov) ScreenTheme.nRoundedRectRing(x, y, w, boxH, 3, 1, FIELD_BG, ACCENT_HOVER)
            var tx = x + 4
            val sw = swatches
            if (sw != null && selected in labels.indices) {
                ScreenTheme.nRoundedRect(x + 4, y + 3, 8, 8, 1, 0xFF000000.toInt() or sw[selected])
                tx = x + 16
            }
            val label = if (selected in labels.indices) labels[selected] else placeholder
            val col = if (selected in labels.indices) TEXT_PRIM else TEXT_HINT
            ScreenTheme.nst(clip(label, w - (tx - x) - 12), tx, y + 3, col, 0.62f)
            ScreenTheme.nst("v", x + w - 9, y + 3, TEXT_HINT, 0.62f)
        }

        fun renderOpen(mx: Int, my: Int) {
            if (!open) return
            val rows = rowsShown()
            val ly = y + boxH
            val lh = rows * rowH
            ScreenTheme.nRoundedRectRing(x, ly, w, lh, 3, 1, LIST_BG, FIELD_BORDER)
            for (r in 0 until rows) {
                val idx = scroll + r
                if (idx >= labels.size) break
                val ry = ly + r * rowH
                if (inBox(mx, my, x, ry, w, rowH)) ScreenTheme.nRect(x, ry, w, rowH, ROW_HOVER)
                var tx = x + 4
                val sw = swatches
                if (sw != null) {
                    ScreenTheme.nRoundedRect(x + 4, ry + 3, 8, 8, 1, 0xFF000000.toInt() or sw[idx])
                    tx = x + 16
                }
                val col = if (idx == selected) ACCENT else TEXT_PRIM
                ScreenTheme.nst(clip(labels[idx], w - (tx - x) - 4), tx, ry + 3, col, 0.62f)
            }
        }

        fun click(mx: Int, my: Int): Boolean {
            if (inBox(mx, my, x, y, w, boxH)) { open = !open; if (open) scroll = 0; return true }
            if (open) {
                val rows = rowsShown()
                val ly = y + boxH
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
            val ly = y + boxH
            if (inBox(mx, my, x, ly, w, rows * rowH)) {
                val max = max(0, labels.size - maxVisible)
                scroll = max(0, min(max, scroll - Math.signum(amount).toInt()))
                return true
            }
            return false
        }

        private fun clip(s: String, maxW: Int): String {
            if (ScreenTheme.nstw(s, 0.62f) <= maxW) return s
            var out = s
            while (out.length > 1 && ScreenTheme.nstw("$out...", 0.62f) > maxW) out = out.substring(0, out.length - 1)
            return "$out..."
        }
    }
}
