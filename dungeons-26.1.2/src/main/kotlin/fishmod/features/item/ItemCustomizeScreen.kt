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
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NanoVG
import kotlin.math.max
import kotlin.math.min

/**
 * /fm customize — local-only cosmetic item editor (solid/animated dye, armor trim, rename),
 * keyed by an item's Hypixel instance uuid. Heavily modeled on Skyblocker's CustomizeScreen +
 * ColorSelectionWidget + AnimatedDyeTimelineWidget + ItemSelectPopup (github.com/SkyblockerMod/
 * Skyblocker, MIT), restyled onto FishMod's own NanoVG pill/dropdown visual language instead of
 * vanilla widgets. Item texture/model overrides are intentionally out of scope (see project notes).
 */
class ItemCustomizeScreen : Screen(Component.literal("Customize Item")), HasNvgOverlay {

    private companion object {
        val ACCENT = ScreenTheme.ACCENT
        val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        val TEXT_COLOR = ScreenTheme.TEXT_COLOR
        val SUBTEXT_COLOR = ScreenTheme.SUBTEXT_COLOR
        val DANGER = ScreenTheme.DANGER
        val DANGER_HOVER = ScreenTheme.DANGER_HOVER
        const val BG_PANEL = 0xF20E1016.toInt()
        const val BG_SECTION = 0xFF171A22.toInt()
        const val BORDER = 0xFF2A2D38.toInt()
        val FIELD_BG = 0xFF1A1E26.toInt()
        val FIELD_BORDER = 0xFF2E333D.toInt()

        val PRESETS = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFF5555.toInt(), 0xFFFF9F40.toInt(), 0xFFFFD34D.toInt(),
            0xFF6BE36B.toInt(), 0xFF40C4FF.toInt(), 0xFF5C7CFF.toInt(), 0xFFB25CFF.toInt(),
            0xFFFF5CD3.toInt(), 0xFF2B2B2B.toInt()
        )

        val NAME_COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFF0000AA.toInt(), 0xFF00AA00.toInt(), 0xFF00AAAA.toInt(),
            0xFFAA0000.toInt(), 0xFFAA00AA.toInt(), 0xFFFFAA00.toInt(), 0xFFAAAAAA.toInt(),
            0xFF555555.toInt(), 0xFF5555FF.toInt(), 0xFF55FF55.toInt(), 0xFF55FFFF.toInt(),
            0xFFFF5555.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFF55.toInt(), 0xFFFFFFFF.toInt()
        )
        val NAME_CODES = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    }

    private class ClickRect(var x: Int, var y: Int, var w: Int, var h: Int, val action: () -> Unit) {
        fun hit(mx: Int, my: Int) = mx in x..(x + w) && my in y..(y + h)
    }

    private class SliderInfo { var x = 0; var y = 0; var w = 0; var h = 8; var min = 0f; var max = 1f }

    private var uuid: String? = null
    private var currentItem: ItemStack = ItemStack.EMPTY
    private var panelX = 0
    private var panelY = 0
    private val panelW = 480
    private val panelH = 430

    private var mode = "dye" // dye | trim | name
    private val modeTabs = ArrayList<Pair<String, ClickRect>>()
    private var changeItemBtn: ClickRect? = null

    // ----- item picker -----
    private var pickerOpen = false
    private val pickerRects = ArrayList<Pair<ItemStack, ClickRect>>()
    private var pickerCancelBtn: ClickRect? = null

    // ----- dye -----
    private var animated = false
    private var solidColor = 0xFFFFFFFF.toInt()
    private val presetRects = ArrayList<Pair<Int, ClickRect>>()
    private var animatedToggle: ClickRect? = null
    private var cycleBackToggle: ClickRect? = null
    private var hexField: EditBox? = null
    private var hexRect = ClickRect(0, 0, 0, 0) {}

    private class KeyframeRow(var color: Int, var time: Float, val locked: Boolean)
    private val keyframes = ArrayList<KeyframeRow>()
    private var selectedFrame = 0
    private var draggingFrame = -1
    private val frameMarkerRects = ArrayList<Pair<Int, ClickRect>>()
    private var cycleBack = true
    private var delay = 0f
    private var duration = 1f
    private var timelineRect = ClickRect(0, 0, 0, 0) {}
    private val delaySlider = SliderInfo()
    private val durationSlider = SliderInfo()
    private var draggingSlider = 0 // 0 none, 1 delay, 2 duration

    // ----- trim -----
    private val trimMaterials by lazy { ArmorTrimCache.materials() }
    private val trimPatterns by lazy { ArmorTrimCache.patterns() }
    private var trimMaterialIdx = 0
    private var trimPatternIdx = 0
    private val materialRects = ArrayList<Pair<Int, ClickRect>>()
    private val patternRects = ArrayList<Pair<Int, ClickRect>>()

    // ----- rename -----
    private var nameField: EditBox? = null
    private var nameFieldRect = ClickRect(0, 0, 0, 0) {}
    private val colorRects = ArrayList<Pair<Char, ClickRect>>()
    private val formatRects = ArrayList<Pair<Char, ClickRect>>()
    private var resetNameBtn: ClickRect? = null

    private var applyBtn: ClickRect? = null
    private var clearBtn: ClickRect? = null
    private var doneBtn: ClickRect? = null

    private var focusedField: EditBox? = null

    override fun init() {
        panelX = (this.width - panelW) / 2
        panelY = max(8, (this.height - panelH) / 2)

        val held = Minecraft.getInstance().player?.mainHandItem
        if (held != null && !held.isEmpty && ItemUtil.getUuid(held) != null) {
            currentItem = held
            uuid = ItemUtil.getUuid(held)
            loadFromStore()
        } else {
            pickerOpen = true
        }
        buildStaticLayout()
    }

    private fun buildStaticLayout() {
        modeTabs.clear()
        val tabs = listOf("dye" to "Dye", "trim" to "Trim", "name" to "Rename")
        val tabW = (panelW - 28) / tabs.size
        val tabY = panelY + 58
        for ((i, m) in tabs.withIndex()) {
            val x = panelX + 14 + i * tabW
            modeTabs.add(m.first to ClickRect(x, tabY, tabW - 4, 20) { mode = m.first; focusField(null) })
        }
        changeItemBtn = ClickRect(panelX + panelW - 14 - 100, panelY + 30, 100, 20) { openPicker() }

        val btnY = panelY + panelH - 32
        applyBtn = ClickRect(panelX + 14, btnY, 90, 20) { apply() }
        clearBtn = ClickRect(panelX + 110, btnY, 90, 20) { clear() }
        doneBtn = ClickRect(panelX + panelW - 14 - 70, btnY, 70, 20) { onClose() }
    }

    private fun openPicker() { focusField(null); pickerOpen = true }

    private fun selectItem(item: ItemStack) {
        currentItem = item
        uuid = ItemUtil.getUuid(item)
        pickerOpen = false
        loadFromStore()
    }

    private fun loadFromStore() {
        val id = uuid ?: return
        animated = false
        solidColor = 0xFFFFFFFF.toInt()
        keyframes.clear()
        cycleBack = true; delay = 0f; duration = 1f
        selectedFrame = 0

        ItemCustomizationStore.getDyeColor(id)?.let { solidColor = it }
        val dye = ItemCustomizationStore.getAnimatedDye(id)
        if (dye != null) {
            animated = true
            dye.keyframes.forEachIndexed { i, kf -> keyframes.add(KeyframeRow(kf.color, kf.time, i == 0 || i == dye.keyframes.size - 1)) }
            cycleBack = dye.cycleBack; delay = dye.delay; duration = dye.duration
        }
        if (keyframes.isEmpty()) {
            keyframes.add(KeyframeRow(0xFFFF0000.toInt(), 0f, true))
            keyframes.add(KeyframeRow(0xFF0000FF.toInt(), 1f, true))
        }

        trimMaterialIdx = 0; trimPatternIdx = 0
        ItemCustomizationStore.getArmorTrim(id)?.let { t ->
            trimMaterialIdx = trimMaterials.indexOf(t.material).coerceAtLeast(0)
            trimPatternIdx = trimPatterns.indexOf(t.pattern).coerceAtLeast(0)
        }

        hexField = EditBox(this.font, 0, 0, 90, 20, Component.literal("Hex")).also {
            it.setMaxLength(6)
            it.setValue(String.format("%06X", (if (animated) keyframes[selectedFrame].color else solidColor) and 0xFFFFFF))
        }
        nameField = EditBox(this.font, 0, 0, panelW - 28, 20, Component.literal("Name")).also {
            it.setMaxLength(256)
            it.setValue(ItemCustomizationStore.getItemName(id) ?: "")
        }
    }

    // ----- apply/clear -----

    private fun applySolid() { val id = uuid ?: return; ItemCustomizationStore.setDyeColor(id, solidColor) }
    private fun applyAnimated() {
        val id = uuid ?: return
        ItemCustomizationStore.setAnimatedDye(
            id,
            ItemCustomizationStore.AnimatedDye(keyframes.map { ItemCustomizationStore.Keyframe(it.color, it.time) }, cycleBack, delay, duration)
        )
    }
    private fun applyTrim() {
        val id = uuid ?: return
        if (trimMaterials.isNotEmpty() && trimPatterns.isNotEmpty()) {
            ItemCustomizationStore.setArmorTrim(id, ItemCustomizationStore.ArmorTrimId(trimMaterials[trimMaterialIdx], trimPatterns[trimPatternIdx]))
        }
    }
    private fun applyName() { val id = uuid ?: return; nameField?.value?.let { if (it.isNotBlank()) ItemCustomizationStore.setItemName(id, it) } }

    private fun apply() {
        when (mode) {
            "dye" -> if (animated) applyAnimated() else applySolid()
            "trim" -> applyTrim()
            "name" -> applyName()
        }
    }

    private fun clear() {
        val id = uuid ?: return
        when (mode) {
            "dye" -> {
                ItemCustomizationStore.removeDyeColor(id)
                ItemCustomizationStore.removeAnimatedDye(id)
                animated = false; solidColor = 0xFFFFFFFF.toInt()
                hexField?.setValue("FFFFFF")
            }
            "trim" -> ItemCustomizationStore.removeArmorTrim(id)
            "name" -> { ItemCustomizationStore.removeItemName(id); nameField?.setValue("") }
        }
    }

    private fun onColorPick(c: Int) {
        if (animated) keyframes.getOrNull(selectedFrame)?.let { it.color = c; applyAnimated() }
        else { solidColor = c; applySolid() }
        hexField?.setValue(String.format("%06X", c and 0xFFFFFF))
    }

    private fun toggleAnimated() {
        animated = !animated
        if (animated) applyAnimated() else applySolid()
        val c = if (animated) keyframes.getOrNull(selectedFrame)?.color ?: solidColor else solidColor
        hexField?.setValue(String.format("%06X", c and 0xFFFFFF))
    }

    private fun onHexChanged() {
        val v = hexField?.value ?: return
        if (v.length == 6 && v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            val c = (0xFF shl 24) or v.toInt(16)
            if (animated) keyframes.getOrNull(selectedFrame)?.let { it.color = c; applyAnimated() }
            else { solidColor = c; applySolid() }
        }
    }

    private fun insertNameCode(code: Char) {
        val f = nameField ?: return
        f.insertText("§$code")
    }

    private fun focusField(f: EditBox?) {
        focusedField?.isFocused = false
        focusedField = f
        focusedField?.isFocused = true
    }

    // ----- rendering -----

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        NvgRecorder.clear()
        ScreenTheme.panel(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 8, BG_PANEL, BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION)
        ScreenTheme.nst("Customize Item", panelX + 14, panelY + 7, TEXT_COLOR)

        if (pickerOpen) {
            drawPicker(ctx, mouseX, mouseY)
            super.extractRenderState(ctx, mouseX, mouseY, delta)
            return
        }

        ctx.item(currentItem, panelX + 14, panelY + 28)
        ScreenTheme.nst(currentItem.hoverName.string, panelX + 40, panelY + 34, TEXT_COLOR)
        changeItemBtn?.let { r ->
            val hov = r.hit(mouseX, mouseY)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (hov) FIELD_BORDER else FIELD_BG)
            val label = "Change Item"
            ScreenTheme.nst(label, r.x + (r.w - ScreenTheme.nstw(label, 0.55f)) / 2, r.y + 6, TEXT_COLOR, 0.55f)
        }

        for ((m, r) in modeTabs) {
            val active = m == mode
            val hov = r.hit(mouseX, mouseY)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (active) ACCENT else if (hov) FIELD_BORDER else FIELD_BG)
            val label = when (m) { "dye" -> "Dye"; "trim" -> "Trim"; else -> "Rename" }
            val tw = ScreenTheme.nstw(label)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, r.y + 6, if (active) 0xFF06302F.toInt() else TEXT_COLOR)
        }

        val contentY = panelY + 128
        when (mode) {
            "dye" -> drawDyeTab(ctx, mouseX, mouseY, contentY)
            "trim" -> drawTrimTab(ctx, mouseX, mouseY, contentY)
            "name" -> drawNameTab(ctx, mouseX, mouseY, contentY)
        }

        drawFooterButtons(ctx, mouseX, mouseY)
        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun drawPicker(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        pickerRects.clear()
        val inv = Minecraft.getInstance().player?.inventory
        ScreenTheme.nst("Select an item to customize:", panelX + 14, panelY + 34, SUBTEXT_COLOR)
        ScreenTheme.nst("Items with a Hypixel instance UUID are enabled; others are dimmed.", panelX + 14, panelY + 48, SUBTEXT_COLOR, 0.55f)

        if (inv != null) {
            val cols = 9
            val cell = 40
            val gap = 4
            val gridX = panelX + 14
            val gridY = panelY + 64
            for (i in 0 until inv.containerSize) {
                val stack = inv.getItem(i)
                if (stack.isEmpty) continue
                val col = i % cols
                val row = i / cols
                val x = gridX + col * (cell + gap)
                val y = gridY + row * (cell + gap)
                if (y > panelY + panelH - 40) continue
                val eligible = ItemUtil.getUuid(stack) != null
                val hov = mouseX in x..(x + cell) && mouseY in y..(y + cell)
                ScreenTheme.roundedRectRing(ctx, x, y, cell, cell, 5, 1, FIELD_BG, if (!eligible) BORDER else if (hov) ACCENT else FIELD_BORDER)
                ctx.item(stack, x + (cell - 16) / 2, y + (cell - 16) / 2)
                if (!eligible) ctx.fill(x, y, x + cell, y + cell, 0x80000000.toInt())
                else pickerRects.add(stack to ClickRect(x, y, cell, cell) { selectItem(stack) })
            }
        }

        val label = if (uuid != null) "Cancel" else "Close"
        val r = ClickRect(panelX + panelW - 14 - 80, panelY + panelH - 32, 80, 20) { if (uuid != null) pickerOpen = false else onClose() }
        pickerCancelBtn = r
        val hov = r.hit(mouseX, mouseY)
        ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (hov) FIELD_BORDER else FIELD_BG)
        ScreenTheme.nst(label, r.x + (r.w - ScreenTheme.nstw(label)) / 2, r.y + 6, TEXT_COLOR)
    }

    private fun drawDyeTab(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, top: Int) {
        presetRects.clear()
        ScreenTheme.nst("Solid dye color:", panelX + 14, top - 16, SUBTEXT_COLOR)
        val swatch = 26; val gap = 8
        val activeColor = if (animated) keyframes.getOrNull(selectedFrame)?.color else solidColor
        for ((i, c) in PRESETS.withIndex()) {
            val x = panelX + 14 + i * (swatch + gap)
            val r = ClickRect(x, top, swatch, swatch) { onColorPick(c) }
            presetRects.add(c to r)
            val hov = r.hit(mouseX, mouseY)
            val selected = c == activeColor
            ScreenTheme.roundedRectRing(ctx, r.x, r.y, r.w, r.h, 6, if (selected) 2 else 1, c, if (selected) ACCENT_HOVER else if (hov) ACCENT else FIELD_BORDER)
        }

        val hexY = top + swatch + 16
        ScreenTheme.nst("Hex:", panelX + 14, hexY + 6, SUBTEXT_COLOR)
        hexRect = ClickRect(panelX + 48, hexY, 90, 20) {}
        val hexFocused = focusedField === hexField
        ScreenTheme.roundedRectRing(ctx, hexRect.x, hexRect.y, hexRect.w, hexRect.h, 4, 1, FIELD_BG, if (hexFocused) ACCENT else FIELD_BORDER)
        drawEditBoxText(hexField, hexRect.x + 6, hexRect.y + 10)

        val toggleY = hexY + 30
        animatedToggle = ClickRect(panelX + 14, toggleY, 18, 18) { toggleAnimated() }
        drawCheckbox(ctx, animatedToggle!!, animated)
        ScreenTheme.nst("Animated dye", panelX + 38, toggleY + 5, TEXT_COLOR)

        if (!animated) return

        val tY = toggleY + 34
        drawTimeline(ctx, mouseX, mouseY, tY)

        val belowTimeline = tY + 44
        cycleBackToggle = ClickRect(panelX + 14, belowTimeline, 18, 18) { cycleBack = !cycleBack; applyAnimated() }
        drawCheckbox(ctx, cycleBackToggle!!, cycleBack)
        ScreenTheme.nst("Cycle back", panelX + 38, belowTimeline + 5, TEXT_COLOR)

        val sliderY = belowTimeline + 32
        drawSlider(ctx, panelX + 14, sliderY, 200, "Delay", delay, 0f, 2f, delaySlider)
        drawSlider(ctx, panelX + 14, sliderY + 32, 200, "Duration", duration, 0.1f, 10f, durationSlider)
    }

    private fun drawTimeline(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, top: Int) {
        frameMarkerRects.clear()
        val w = panelW - 28
        val h = 22
        timelineRect = ClickRect(panelX + 14, top, w, h) {}
        ScreenTheme.roundedRectRing(ctx, timelineRect.x, timelineRect.y, w, h, 4, 1, FIELD_BG, FIELD_BORDER)

        val sorted = keyframes.sortedBy { it.time }
        val innerX0 = timelineRect.x + 2
        val innerW = max(1, w - 4)
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            val x0 = (innerX0 + a.time * innerW).toInt()
            val x1 = (innerX0 + b.time * innerW).toInt().coerceAtLeast(x0 + 1)
            for (x in x0 until x1) {
                val t = (x - x0).toFloat() / (x1 - x0).toFloat()
                ctx.fill(x, timelineRect.y + 2, x + 1, timelineRect.y + h - 2, lerpColor(a.color, b.color, t))
            }
        }

        for ((idx, kf) in keyframes.withIndex()) {
            val mx = (innerX0 + kf.time * innerW).toInt()
            val selected = idx == selectedFrame
            val r = ClickRect(mx - 4, timelineRect.y - 4, 8, h + 8) {}
            frameMarkerRects.add(idx to r)
            ScreenTheme.roundedRectRing(ctx, r.x, r.y, r.w, r.h, 2, if (selected) 2 else 1, kf.color, if (selected) ACCENT_HOVER else FIELD_BORDER)
        }
        ScreenTheme.nst("Click bar to add, drag to move, right-click to delete", panelX + 14, top + h + 6, SUBTEXT_COLOR, 0.5f)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun drawCheckbox(ctx: GuiGraphicsExtractor, r: ClickRect, checked: Boolean) {
        ScreenTheme.roundedRectRing(ctx, r.x, r.y, r.w, r.h, 4, 1, if (checked) ACCENT else FIELD_BG, FIELD_BORDER)
        if (checked) ScreenTheme.roundedRect(ctx, r.x + 4, r.y + 4, r.w - 8, r.h - 8, 2, TEXT_COLOR)
    }

    private fun drawSlider(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, label: String, value: Float, min: Float, max: Float, info: SliderInfo) {
        info.x = x; info.y = y; info.w = w; info.h = 8; info.min = min; info.max = max
        ScreenTheme.nst("$label: ${"%.2f".format(value)}s", x, y - 14, SUBTEXT_COLOR, 0.55f)
        ScreenTheme.roundedRect(ctx, x, y, w, 8, 4, FIELD_BG)
        val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
        ScreenTheme.roundedRect(ctx, x, y, max((w * frac).toInt(), 4), 8, 4, ACCENT)
    }

    private fun sliderHit(info: SliderInfo, mx: Int, my: Int) = mx in info.x..(info.x + info.w) && my in (info.y - 6)..(info.y + info.h + 6)
    private fun sliderValueAt(mx: Int, info: SliderInfo): Float {
        val t = ((mx - info.x).toFloat() / max(1, info.w).toFloat()).coerceIn(0f, 1f)
        return info.min + t * (info.max - info.min)
    }

    private fun drawTrimTab(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, top: Int) {
        materialRects.clear(); patternRects.clear()
        if (trimMaterials.isEmpty() || trimPatterns.isEmpty()) {
            ScreenTheme.nst("Trim registries unavailable (must be in-world).", panelX + 14, top, SUBTEXT_COLOR)
            return
        }
        val maxX = panelX + panelW - 14
        val cell = 62; val h = 20; val gap = 6

        ScreenTheme.nst("Material:", panelX + 14, top - 16, SUBTEXT_COLOR)
        var x = panelX + 14; var y = top
        for ((i, m) in trimMaterials.withIndex()) {
            val label = m.substringAfterLast(':').take(10)
            val w = max(cell, ScreenTheme.nstw(label, 0.5f) + 16)
            if (x + w > maxX) { x = panelX + 14; y += h + gap }
            val r = ClickRect(x, y, w, h) { trimMaterialIdx = i; applyTrim() }
            materialRects.add(i to r)
            val selected = i == trimMaterialIdx
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (selected) ACCENT else if (r.hit(mouseX, mouseY)) FIELD_BORDER else FIELD_BG)
            ScreenTheme.nst(label, r.x + 8, r.y + 6, if (selected) 0xFF06302F.toInt() else TEXT_COLOR, 0.5f)
            x += w + gap
        }

        y += h + gap + 18
        ScreenTheme.nst("Pattern:", panelX + 14, y - 16, SUBTEXT_COLOR)
        x = panelX + 14
        for ((i, p) in trimPatterns.withIndex()) {
            val label = p.substringAfterLast(':').take(10)
            val w = max(cell, ScreenTheme.nstw(label, 0.5f) + 16)
            if (x + w > maxX) { x = panelX + 14; y += h + gap }
            val r = ClickRect(x, y, w, h) { trimPatternIdx = i; applyTrim() }
            patternRects.add(i to r)
            val selected = i == trimPatternIdx
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (selected) ACCENT else if (r.hit(mouseX, mouseY)) FIELD_BORDER else FIELD_BG)
            ScreenTheme.nst(label, r.x + 8, r.y + 6, if (selected) 0xFF06302F.toInt() else TEXT_COLOR, 0.5f)
            x += w + gap
        }
    }

    private fun drawNameTab(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, top: Int) {
        colorRects.clear(); formatRects.clear()
        ScreenTheme.nst("Custom display name:", panelX + 14, top - 16, SUBTEXT_COLOR)
        nameFieldRect = ClickRect(panelX + 14, top, panelW - 28, 20) {}
        val focused = focusedField === nameField
        ScreenTheme.roundedRectRing(ctx, nameFieldRect.x, nameFieldRect.y, nameFieldRect.w, nameFieldRect.h, 4, 1, FIELD_BG, if (focused) ACCENT else FIELD_BORDER)
        drawEditBoxText(nameField, nameFieldRect.x + 6, nameFieldRect.y + 10)

        val previewY = top + 34
        ScreenTheme.nst("Preview:", panelX + 14, previewY - 14, SUBTEXT_COLOR)
        var px = panelX + 14
        for ((text, color) in LegacyFormatting.previewRuns(nameField?.value ?: "")) {
            ScreenTheme.nst(text, px, previewY, color)
            px += ScreenTheme.nstw(text)
        }

        val colorY = previewY + 24
        ScreenTheme.nst("Color:", panelX + 14, colorY - 14, SUBTEXT_COLOR)
        for ((i, c) in NAME_COLORS.withIndex()) {
            val x = panelX + 14 + i * 20
            val r = ClickRect(x, colorY, 16, 16) { insertNameCode(NAME_CODES[i]) }
            colorRects.add(NAME_CODES[i] to r)
            ScreenTheme.roundedRectRing(ctx, x, colorY, 16, 16, 4, 1, c, if (r.hit(mouseX, mouseY)) ACCENT else FIELD_BORDER)
        }

        val fmtY = colorY + 28
        ScreenTheme.nst("Format:", panelX + 14, fmtY - 14, SUBTEXT_COLOR)
        val fmts = listOf('l' to "B", 'o' to "I", 'n' to "U", 'm' to "S", 'k' to "K")
        for ((i, pair) in fmts.withIndex()) {
            val (code, label) = pair
            val x = panelX + 14 + i * 30
            val r = ClickRect(x, fmtY, 24, 18) { insertNameCode(code) }
            formatRects.add(code to r)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (r.hit(mouseX, mouseY)) FIELD_BORDER else FIELD_BG)
            ScreenTheme.nst(label, r.x + 8, r.y + 5, TEXT_COLOR, 0.5f)
        }

        val resetY = fmtY + 26
        val r = ClickRect(panelX + 14, resetY, 60, 18) { insertNameCode('r') }
        resetNameBtn = r
        ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (r.hit(mouseX, mouseY)) FIELD_BORDER else FIELD_BG)
        ScreenTheme.nst("Reset", r.x + 10, r.y + 5, TEXT_COLOR, 0.5f)
    }

    private fun drawEditBoxText(field: EditBox?, x: Int, y: Int) {
        field ?: return
        val text = field.value
        ScreenTheme.nst(text, x, y, TEXT_COLOR, 0.6f)
        if (field.isFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cursor = field.cursorPosition.coerceIn(0, text.length)
            val cx = x + ScreenTheme.nstw(text.substring(0, cursor), 0.6f)
            ScreenTheme.nst("|", cx - 2, y, TEXT_COLOR, 0.6f)
        }
    }

    private fun drawFooterButtons(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        applyBtn?.let { r ->
            val hov = r.hit(mouseX, mouseY)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (hov) ACCENT_HOVER else ACCENT)
            val label = "Apply"
            ScreenTheme.nst(label, r.x + (r.w - ScreenTheme.nstw(label)) / 2, r.y + 6, 0xFF06302F.toInt())
        }
        clearBtn?.let { r ->
            val hov = r.hit(mouseX, mouseY)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (hov) DANGER_HOVER else DANGER)
            val label = "Clear"
            ScreenTheme.nst(label, r.x + (r.w - ScreenTheme.nstw(label)) / 2, r.y + 6, 0xFF2A0808.toInt())
        }
        doneBtn?.let { r ->
            val hov = r.hit(mouseX, mouseY)
            ScreenTheme.roundedRectRing(ctx, r.x, r.y, r.w, r.h, r.h / 2, 1, 0xFF14181D.toInt(), if (hov) ACCENT_HOVER else ACCENT)
            val label = "Done"
            ScreenTheme.nst(label, r.x + (r.w - ScreenTheme.nstw(label)) / 2, r.y + 6, if (hov) ACCENT_HOVER else TEXT_COLOR)
        }
    }

    // ----- NanoVG overlay -----

    private val nvgGlState = NvgGlStateGuard()
    private var nvgFailureLogged = false

    override fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = NvgContext.get()
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay()
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

    // ----- input -----

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()

        if (pickerOpen) {
            for ((_, r) in pickerRects) if (r.hit(mx, my)) { r.action(); return true }
            pickerCancelBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
            return super.mouseClicked(click, doubled)
        }

        changeItemBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        for ((_, r) in modeTabs) if (r.hit(mx, my)) { r.action(); return true }

        if (mode == "dye") {
            if (hexRect.hit(mx, my)) { focusField(hexField); return true }
            for ((_, r) in presetRects) if (r.hit(mx, my)) { focusField(null); r.action(); return true }
            animatedToggle?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
            if (animated) {
                for ((idx, r) in frameMarkerRects) {
                    if (r.hit(mx, my)) {
                        focusField(null)
                        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                            if (!keyframes[idx].locked && keyframes.size > 2) { keyframes.removeAt(idx); selectedFrame = 0; applyAnimated() }
                        } else {
                            selectedFrame = idx
                            draggingFrame = if (keyframes[idx].locked) -1 else idx
                        }
                        return true
                    }
                }
                if (timelineRect.hit(mx, my)) {
                    focusField(null)
                    val t = ((mx - timelineRect.x - 2).toFloat() / max(1, timelineRect.w - 4).toFloat()).coerceIn(0f, 1f)
                    keyframes.add(KeyframeRow(0xFFFF0000.toInt(), t, false))
                    selectedFrame = keyframes.size - 1
                    applyAnimated()
                    return true
                }
                cycleBackToggle?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
                if (sliderHit(delaySlider, mx, my)) { focusField(null); draggingSlider = 1; delay = sliderValueAt(mx, delaySlider); return true }
                if (sliderHit(durationSlider, mx, my)) { focusField(null); draggingSlider = 2; duration = sliderValueAt(mx, durationSlider).coerceAtLeast(0.1f); return true }
            }
        }
        if (mode == "trim") {
            for ((_, r) in materialRects) if (r.hit(mx, my)) { focusField(null); r.action(); return true }
            for ((_, r) in patternRects) if (r.hit(mx, my)) { focusField(null); r.action(); return true }
        }
        if (mode == "name") {
            if (nameFieldRect.hit(mx, my)) { focusField(nameField); return true }
            for ((_, r) in colorRects) if (r.hit(mx, my)) { r.action(); return true }
            for ((_, r) in formatRects) if (r.hit(mx, my)) { r.action(); return true }
            resetNameBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        }

        applyBtn?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
        clearBtn?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
        doneBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }

        focusField(null)
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mx = click.x().toInt()
        if (draggingFrame in keyframes.indices) {
            val t = ((mx - timelineRect.x - 2).toFloat() / max(1, timelineRect.w - 4).toFloat()).coerceIn(0f, 1f)
            keyframes[draggingFrame].time = t
            return true
        }
        if (draggingSlider == 1) { delay = sliderValueAt(mx, delaySlider); return true }
        if (draggingSlider == 2) { duration = sliderValueAt(mx, durationSlider).coerceAtLeast(0.1f); return true }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        if (draggingFrame in keyframes.indices) {
            val moved = keyframes[draggingFrame]
            keyframes.sortBy { it.time }
            selectedFrame = keyframes.indexOf(moved)
            draggingFrame = -1
            applyAnimated()
        }
        if (draggingSlider != 0) { draggingSlider = 0; applyAnimated() }
        return super.mouseReleased(click)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (pickerOpen) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
            return super.keyPressed(input)
        }
        focusedField?.let { it.keyPressed(input); if (it === hexField) onHexChanged(); return true }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        focusedField?.let {
            it.charTyped(input)
            if (it === hexField) onHexChanged()
            return true
        }
        return super.charTyped(input)
    }

    override fun isPauseScreen(): Boolean = false
}
