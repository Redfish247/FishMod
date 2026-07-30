package fishmod.features

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData

/**
 * /fm customize — a clearer, friendlier item customizer.
 *
 * PICK an item (worn armor or inventory slot), then CUSTOMIZE it:
 *   - Name & Model are pre-filled with the item's real values so you edit a base, not a blank.
 *     Reset returns to base instead of wiping.
 *   - A clickable &-code key inserts colors/formats into the name at the cursor. Type `&*` to add
 *     a ✪ star in whatever color precedes it (no more star counter).
 *   - Dye is a Hypixel-dye dropdown (with a free hex box) — only for leather armor.
 *   - Trim material + pattern are dropdowns.
 */
class ItemCustomizeScreen : Screen(Component.literal("Item Customize")) {

    private var panelX = 0
    private var panelY = 0
    private val panelW = 360
    private val panelH = 432
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
    private lateinit var skinField: EditBox
    private lateinit var dyeDropdown: Dropdown
    private lateinit var trimMatDropdown: Dropdown
    private lateinit var trimPatDropdown: Dropdown

    // &-code key hit-boxes, rebuilt each frame, consumed by mouseClicked. {x,y,w,h} + parallel code.
    private val keyRects = ArrayList<IntArray>()
    private val keyCodes = ArrayList<String>()

    private fun inv(): Inventory = minecraft!!.player!!.inventory
    private fun mainCount(): Int = Math.min(36, inv().containerSize)

    override fun init() {
        if (minecraft == null || minecraft!!.player == null) return

        panelX = (this.width - panelW) / 2
        panelY = Math.max(8, (this.height - panelH) / 2)

        // Default selection = currently held item.
        val held = minecraft!!.player!!.mainHandItem
        for (i in 0 until mainCount()) if (inv().getItem(i) === held) { selectedIndex = i; break }

        val p = panelX + 14
        val contentW = panelW - 28

        // PICK ITEM
        val pickY = panelY + 54
        armorX = p
        armorY = pickY + 14
        gridX = p + 4 * CELL + 12
        gridY = armorY

        // CUSTOMIZE
        legendX = p
        legendY = panelY + 172

        val labelW = 52
        val fx = p + labelW
        val fw = contentW - labelW

        val nameY = legendY + 50
        val modelY = nameY + 24
        val dyeY = modelY + 24
        val trimY = dyeY + 24
        val skinY = trimY + 24

        nameField = EditBox(this.font, fx, nameY, fw, 14, Component.literal("Name"))
        nameField.setMaxLength(128)
        addRenderableWidget(nameField)

        modelField = EditBox(this.font, fx, modelY, fw, 14, Component.literal("Model"))
        modelField.setMaxLength(64)
        addRenderableWidget(modelField)

        // Dye: hex box on the right, dropdown on the left (built below).
        dyeField = EditBox(this.font, fx + 156, dyeY, fw - 156, 14, Component.literal("Hex"))
        dyeField.setMaxLength(6)
        addRenderableWidget(dyeField)

        dyeDropdown = Dropdown("Pick a dye…", fx, dyeY, 150)
        for (d in DYES) dyeDropdown.labels.add(d[0])
        dyeDropdown.swatches = DYE_RGB
        dyeDropdown.onChange = Runnable {
            if (dyeDropdown.selected >= 0) dyeField.setValue(DYES[dyeDropdown.selected][1])
        }

        val half = (fw - 6) / 2
        trimMatDropdown = Dropdown("Material", fx, trimY, half)
        for (s in TRIM_MATERIALS) trimMatDropdown.labels.add(cap(s))
        trimPatDropdown = Dropdown("Pattern", fx + half + 6, trimY, half)
        for (s in TRIM_PATTERNS) trimPatDropdown.labels.add(cap(s))

        // Skin: a head texture for player_head items (e.g. apply a Hypixel pet/cosmetic skin). Accepts
        // a texture hash, a textures.minecraft.net URL, or a raw base64 textures value.
        skinField = EditBox(this.font, fx, skinY, fw, 14, Component.literal("Skin"))
        skinField.setMaxLength(2048)
        addRenderableWidget(skinField)

        // Bottom buttons
        val btnY = panelY + panelH - 26
        val btnW = 70
        val btnX = panelX + (panelW - btnW * 3 - 12) / 2
        addRenderableWidget(
            Button.builder(Component.literal("Apply")) { _ -> apply() }
                .bounds(btnX, btnY, btnW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("§eReset")) { _ -> reset() }
                .bounds(btnX + btnW + 6, btnY, btnW, 20)
                .tooltip(Tooltip.create(Component.literal("Restore this item to its original look and re-fill the fields with its base values.")))
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { _ -> onClose() }
                .bounds(btnX + (btnW + 6) * 2, btnY, btnW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("§cClear All")) { _ -> ItemCustomizer.clearAll(); loadFields() }
                .bounds(panelX + panelW - 60 - 8, btnY, 60, 20)
                .tooltip(Tooltip.create(Component.literal("Wipes every saved customization on every item.")))
                .build()
        )

        loadFields()
    }

    // ── load / apply / reset ───────────────────────────────────────────────────

    private fun loadFields() {
        val sel = inv().getItem(selectedIndex)
        val c = ItemCustomizer.get(sel)

        // Name + Model pre-fill with the item's BASE values so the user edits, not starts blank.
        nameField.setValue(if (c != null && !c.name().isNullOrEmpty()) c.name()!! else sel.hoverName.string)
        val baseModel = ItemCustomizer.vanillaId(sel)
        modelField.setValue(if (c != null && !c.modelId().isNullOrEmpty()) c.modelId()!! else (baseModel ?: ""))

        val dye = c?.dye() ?: -1
        dyeField.setValue(if (dye >= 0) String.format("%06X", dye and 0xFFFFFF) else "")
        dyeDropdown.selected = if (dye >= 0) dyeIndex(dye) else -1

        trimMatDropdown.selected = if (c != null && c.trimMat() != null) indexOf(TRIM_MATERIALS, c.trimMat()) else -1
        trimPatDropdown.selected = if (c != null && c.trimPat() != null) indexOf(TRIM_PATTERNS, c.trimPat()) else -1

        skinField.setValue(c?.skin() ?: "")
        skinField.visible = isHead(sel)

        dyeDropdown.close(); trimMatDropdown.close(); trimPatDropdown.close()
        dyeField.visible = dyeAllowed(sel)
    }

    private fun isHead(st: ItemStack?): Boolean {
        return st != null && !st.isEmpty && st.`is`(Items.PLAYER_HEAD)
    }

    private fun apply() {
        val sel = inv().getItem(selectedIndex)

        // "equals base" → store empty so the item keeps its original (colored) name / model.
        var name = nameField.value
        if (name == sel.hoverName.string) name = ""
        val baseModel = ItemCustomizer.vanillaId(sel)
        var model = modelField.value.trim()
        if (baseModel != null && model == baseModel) model = ""

        var dye = -1
        if (dyeAllowed(sel)) {
            val d = dyeField.value.trim()
            if (d.length == 6) try { dye = d.toLong(16).toInt() } catch (ignored: NumberFormatException) {}
        }

        val mat = if (trimMatDropdown.selected >= 0) TRIM_MATERIALS[trimMatDropdown.selected] else ""
        val pat = if (trimPatDropdown.selected >= 0) TRIM_PATTERNS[trimPatDropdown.selected] else ""

        val skin = if (isHead(sel)) skinField.value.trim() else ""

        // Stars are now encoded as "&*" inside the name, so the stored star count is always 0.
        ItemCustomizer.set(sel, name, model, 0, dye, mat, pat, skin)
    }

    /** Restore the item to its original appearance, then re-fill the fields with its base values. */
    private fun reset() {
        ItemCustomizer.set(inv().getItem(selectedIndex), "", "", 0, -1, "", "", "")
        loadFields()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun dyeAllowed(st: ItemStack?): Boolean {
        if (st == null || st.isEmpty) return false
        val it = st.item
        if (it === Items.LEATHER_HELMET || it === Items.LEATHER_CHESTPLATE ||
            it === Items.LEATHER_LEGGINGS || it === Items.LEATHER_BOOTS
        ) return true
        return try { st.has(DataComponents.DYED_COLOR) } catch (e: Exception) { false }
    }

    private fun insertIntoName(code: String) {
        val t = nameField.value
        val cur = Math.min(nameField.cursorPosition, t.length)
        val nt = t.substring(0, cur) + code + t.substring(cur)
        if (nt.length > 128) return
        nameField.setValue(nt)
        nameField.moveCursorTo(cur + code.length, false)
        nameField.setFocused(true)
        setFocused(nameField)
    }

    private fun idOf(st: ItemStack): String {
        try {
            val cd = st.get(DataComponents.CUSTOM_DATA)
            if (cd != null) {
                val id = cd.copyTag().getStringOr("id", "")
                if (id.isNotEmpty()) return id
            }
        } catch (ignored: Exception) {
        }
        return BuiltInRegistries.ITEM.getKey(st.item).toString()
    }

    // ── render ─────────────────────────────────────────────────────────────────

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(ctx, mouseX, mouseY, delta)
        drawPanel(ctx, mouseX, mouseY)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, delta) // renderBackground (panel + fields) + buttons
        // Open dropdown lists float above everything else.
        val sel = inv().getItem(selectedIndex)
        if (dyeAllowed(sel)) dyeDropdown.renderOpen(ctx, mouseX, mouseY)
        trimMatDropdown.renderOpen(ctx, mouseX, mouseY)
        trimPatDropdown.renderOpen(ctx, mouseX, mouseY)
    }

    private fun drawPanel(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sel = inv().getItem(selectedIndex)
        val p = panelX + 14

        // Outer panel + title
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION)
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT)
        ctx.centeredText(this.font, "§b§lItem Customize", panelX + panelW / 2, panelY + 7, 0xFFFFFF)

        // Current readout
        val curY = panelY + 28
        ctx.text(this.font, "§7Editing:", p, curY, TEXT_HINT)
        ctx.text(this.font, sel.hoverName, p + 46, curY, TEXT_PRIM)
        ctx.text(this.font, "§8" + idOf(sel), p, curY + 11, TEXT_HINT)

        // PICK ITEM
        val pickY = panelY + 54
        ctx.text(this.font, "§b▍ §fPICK ITEM §8— click armor or a slot", p, pickY, ACCENT)

        val armorSlots = intArrayOf(39, 38, 37, 36)
        val armorTags = arrayOf("H", "C", "L", "B")
        for (r in 0 until 4) {
            val s = armorSlots[r]
            val x = armorX + r * CELL
            val y = armorY
            if (s == selectedIndex) ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SEL)
            ctx.fill(x, y, x + 16, y + 16, SLOT_BG)
            if (s < inv().containerSize) {
                val a = inv().getItem(s)
                if (!a.isEmpty) ctx.item(a, x, y)
            }
            ctx.text(this.font, "§8" + armorTags[r], x + 5, y + 18, TEXT_HINT)
        }
        for (i in 0 until mainCount()) {
            val col = i % 9
            val row = if (i < 9) 3 else (i - 9) / 9
            val x = gridX + col * CELL
            val y = gridY + row * CELL
            if (i == selectedIndex) ctx.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SEL)
            ctx.fill(x, y, x + 16, y + 16, SLOT_BG)
            val st = inv().getItem(i)
            if (!st.isEmpty) { ctx.item(st, x, y); ctx.itemDecorations(this.font, st, x, y) }
        }

        // CUSTOMIZE header + &-code key
        ctx.text(this.font, "§b▍ §fCUSTOMIZE", p, panelY + 150, ACCENT)
        drawColorKey(ctx, mouseX, mouseY)

        val labelW = 52
        val fx = p + labelW
        val fw = panelW - 28 - labelW
        val nameY = legendY + 50
        val modelY = nameY + 24
        val dyeY = modelY + 24
        val trimY = dyeY + 24
        val skinY = trimY + 24

        drawLabel(ctx, "Name", p, nameY)
        drawLabel(ctx, "Model", p, modelY)
        drawLabel(ctx, "Dye", p, dyeY)
        drawLabel(ctx, "Trim", p, trimY)
        drawLabel(ctx, "Skin", p, skinY)

        // Dye row: dropdown + hex, or a hint when the item can't be dyed.
        if (dyeAllowed(sel)) {
            dyeDropdown.renderClosed(ctx, mouseX, mouseY)
            ctx.text(this.font, "§8#", fx + 150, dyeY + 3, TEXT_HINT)
        } else {
            ctx.fill(fx, dyeY, fx + fw, dyeY + 14, BG_FIELD)
            ctx.text(this.font, "§8leather armor only", fx + 4, dyeY + 3, TEXT_HINT)
        }

        // Trim dropdowns
        trimMatDropdown.renderClosed(ctx, mouseX, mouseY)
        trimPatDropdown.renderClosed(ctx, mouseX, mouseY)

        // Skin row: an editable texture box for player heads, or a hint for everything else.
        if (isHead(sel)) {
            ctx.text(this.font, "§8hash / url / value", fx, skinY + 16, TEXT_HINT)
        } else {
            ctx.fill(fx, skinY, fx + fw, skinY + 14, BG_FIELD)
            ctx.text(this.font, "§8player heads only (pets)", fx + 4, skinY + 3, TEXT_HINT)
        }

        // Live preview (name with &* stars resolved)
        val prevY = skinY + 28
        val nameTxt = nameField.value
        ctx.text(this.font, "§7Preview:", p, prevY, TEXT_HINT)
        ctx.text(this.font, fishmod.cosmetic.NickState.parse(nameTxt), fx, prevY, 0xFFFFFFFF.toInt())
    }

    private fun drawLabel(ctx: GuiGraphicsExtractor, s: String, x: Int, y: Int) {
        ctx.text(this.font, "§f$s:", x, y + 3, TEXT_PRIM)
    }

    /** Clickable color/format key. Click a color → inserts its code into the name at the cursor;
     *  the ✪ button inserts "&*". Rebuilds the hit-boxes each frame. */
    private fun drawColorKey(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        keyRects.clear()
        keyCodes.clear()

        ctx.text(
            this.font,
            "§8&-codes (click to insert) — §7&* §8= ✪ star in the color before it", legendX, legendY - 11, TEXT_HINT
        )

        val sw = 16
        val sh = 11
        val gap = 2
        // 16 colors, 8 per row
        for (i in CODE_COLORS.indices) {
            val code = CODE_COLORS[i][0].toChar()
            val rgb = CODE_COLORS[i][1]
            val colX = legendX + (i % 8) * (sw + gap)
            val colY = legendY + (i / 8) * (sh + gap)
            val hov = inBox(mouseX, mouseY, colX, colY, sw, sh)
            ctx.fill(colX - 1, colY - 1, colX + sw + 1, colY + sh + 1, if (hov) ACCENT else BORDER_LT)
            ctx.fill(colX, colY, colX + sw, colY + sh, (0xFF000000.toInt()) or rgb)
            val textCol = if (brightness(rgb) > 140) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            ctx.centeredText(this.font, code.toString(), colX + sw / 2, colY + 2, textCol)
            keyRects.add(intArrayOf(colX, colY, sw, sh))
            keyCodes.add("&$code")
        }

        // format codes + star button, on the row to the right of the two color rows
        var fxr = legendX + 8 * (sw + gap) + 8
        val fyr = legendY
        for (f in CODE_FORMATS) {
            val sample = f[1]
            val w = this.font.width(sample) + 6
            val hov = inBox(mouseX, mouseY, fxr, fyr, w, sh)
            ctx.fill(fxr, fyr, fxr + w, fyr + sh, if (hov) ROW_HOVER else BG_FIELD)
            ctx.text(this.font, sample, fxr + 3, fyr + 2, 0xFFFFFFFF.toInt())
            keyRects.add(intArrayOf(fxr, fyr, w, sh))
            keyCodes.add("&" + f[0])
            fxr += w + gap
        }
        // ✪ star button on the second row under the format codes
        val starX = legendX + 8 * (sw + gap) + 8
        val starY = legendY + sh + gap
        val starW = this.font.width("&* ✪") + 8
        val hovStar = inBox(mouseX, mouseY, starX, starY, starW, sh)
        ctx.fill(starX, starY, starX + starW, starY + sh, if (hovStar) ROW_HOVER else BG_FIELD)
        ctx.text(this.font, "§7&* §6✪", starX + 4, starY + 2, 0xFFFFFFFF.toInt())
        keyRects.add(intArrayOf(starX, starY, starW, sh))
        keyCodes.add("&*")
    }

    // ── input ──────────────────────────────────────────────────────────────────

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        val sel = inv().getItem(selectedIndex)

        // Dropdowns get first crack (open lists sit on top of everything).
        if (dyeAllowed(sel) && dyeDropdown.click(mx, my)) { closeOthers(dyeDropdown); return true }
        if (trimMatDropdown.click(mx, my)) { closeOthers(trimMatDropdown); return true }
        if (trimPatDropdown.click(mx, my)) { closeOthers(trimPatDropdown); return true }

        // &-code key
        for (i in keyRects.indices) {
            val r = keyRects[i]
            if (inBox(mx, my, r[0], r[1], r[2], r[3])) { insertIntoName(keyCodes[i]); return true }
        }

        // Pick item — armor row
        val armorSlots = intArrayOf(39, 38, 37, 36)
        for (r in 0 until 4) {
            val x = armorX + r * CELL
            if (mx >= x && mx <= x + 16 && my >= armorY && my <= armorY + 16) {
                selectedIndex = armorSlots[r]; loadFields(); return true
            }
        }
        // Pick item — inventory grid
        for (i in 0 until mainCount()) {
            val col = i % 9
            val row = if (i < 9) 3 else (i - 9) / 9
            val x = gridX + col * CELL
            val y = gridY + row * CELL
            if (mx >= x && mx <= x + 16 && my >= y && my <= y + 16) {
                selectedIndex = i; loadFields(); return true
            }
        }
        return super.mouseClicked(click, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        if (dyeDropdown.scrolled(mx, my, verticalAmount)) return true
        if (trimMatDropdown.scrolled(mx, my, verticalAmount)) return true
        if (trimPatDropdown.scrolled(mx, my, verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun closeOthers(keep: Dropdown) {
        if (dyeDropdown !== keep) dyeDropdown.close()
        if (trimMatDropdown !== keep) trimMatDropdown.close()
        if (trimPatDropdown !== keep) trimPatDropdown.close()
    }

    override fun isPauseScreen(): Boolean = false

    // ── lightweight dropdown ─────────────────────────────────────────────────────

    private inner class Dropdown(val placeholder: String, val x: Int, val y: Int, val w: Int) {
        val boxH = 14
        val rowH = 13
        val maxVisible = 7
        val labels = ArrayList<String>()
        var swatches: IntArray? = null // optional per-row RGB; null = none
        var selected = -1
        var open = false
        var scroll = 0
        var onChange: Runnable? = null

        fun close() { open = false; scroll = 0 }

        private fun rowsShown(): Int = Math.min(maxVisible, labels.size)

        fun renderClosed(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
            val hov = inBox(mx, my, x, y, w, boxH)
            ctx.fill(x, y, x + w, y + boxH, if (hov) BORDER_LT else BORDER)
            ctx.fill(x + 1, y + 1, x + w - 1, y + boxH - 1, BG_FIELD)
            var tx = x + 4
            val sw = swatches
            if (sw != null && selected >= 0) {
                ctx.fill(x + 4, y + 3, x + 12, y + 11, 0xFF000000.toInt() or sw[selected])
                tx = x + 16
            }
            val label = if (selected >= 0) labels[selected] else placeholder
            val col = if (selected >= 0) TEXT_PRIM else TEXT_HINT
            ctx.text(font, clip(label, w - (tx - x) - 12), tx, y + 3, col)
            ctx.text(font, "§7▾", x + w - 9, y + 3, TEXT_HINT)
        }

        fun renderOpen(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
            if (!open) return
            val rows = rowsShown()
            val ly = y + boxH
            val lh = rows * rowH
            ctx.fill(x - 1, ly, x + w + 1, ly + lh + 1, BORDER_LT)
            ctx.fill(x, ly, x + w, ly + lh, LIST_BG)
            for (r in 0 until rows) {
                val idx = scroll + r
                if (idx >= labels.size) break
                val ry = ly + r * rowH
                if (inBox(mx, my, x, ry, w, rowH)) ctx.fill(x, ry, x + w, ry + rowH, ROW_HOVER)
                var tx = x + 4
                val sw = swatches
                if (sw != null) {
                    ctx.fill(x + 4, ry + 3, x + 12, ry + 11, 0xFF000000.toInt() or sw[idx])
                    tx = x + 16
                }
                val col = if (idx == selected) ACCENT else TEXT_PRIM
                ctx.text(font, clip(labels[idx], w - (tx - x) - 4), tx, ry + 3, col)
            }
            if (labels.size > maxVisible) {
                // simple scroll indicator
                val trackY = ly + 1
                val trackH = lh - 2
                val thumbH = Math.max(8, trackH * rows / labels.size)
                val max = labels.size - maxVisible
                val thumbY = trackY + (if (max == 0) 0 else (trackH - thumbH) * scroll / max)
                ctx.fill(x + w - 3, trackY, x + w - 1, trackY + trackH, 0xFF20242E.toInt())
                ctx.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, BORDER_LT)
            }
        }

        /** @return true if this dropdown consumed the click. */
        fun click(mx: Int, my: Int): Boolean {
            if (inBox(mx, my, x, y, w, boxH)) { open = !open; if (open) scroll = 0; return true }
            if (open) {
                val rows = rowsShown()
                val ly = y + boxH
                if (inBox(mx, my, x, ly, w, rows * rowH)) {
                    val idx = scroll + (my - ly) / rowH
                    if (idx >= 0 && idx < labels.size) {
                        selected = idx
                        open = false
                        onChange?.run()
                    }
                    return true
                }
                open = false // click outside → close (don't consume)
            }
            return false
        }

        fun scrolled(mx: Int, my: Int, amount: Double): Boolean {
            if (!open) return false
            val rows = rowsShown()
            val ly = y + boxH
            if (inBox(mx, my, x, ly, w, rows * rowH)) {
                val max = Math.max(0, labels.size - maxVisible)
                scroll = Math.max(0, Math.min(max, scroll - Math.signum(amount).toInt()))
                return true
            }
            return false
        }

        private fun clip(s: String, maxW: Int): String {
            if (font.width(s) <= maxW) return s
            var out = s
            while (out.length > 1 && font.width("$out…") > maxW) out = out.substring(0, out.length - 1)
            return "$out…"
        }
    }

    companion object {
        private const val CELL = 18

        // palette
        private val BG_PANEL = 0xF20E1016.toInt()
        private val BG_SECTION = 0xFF171A22.toInt()
        private val BG_FIELD = 0xFF1B1E27.toInt()
        private val BORDER = 0xFF2A2D38.toInt()
        private val BORDER_LT = 0xFF3A3E4A.toInt()
        private val ACCENT = 0xFF55FFFF.toInt()
        private val TEXT_PRIM = 0xFFE8ECF2.toInt()
        private val TEXT_HINT = 0xFF8A8F9C.toInt()
        private val SLOT_BG = 0xFF2A2D38.toInt()
        private val SLOT_SEL = 0xFF55FF55.toInt()
        private val ROW_HOVER = 0xFF2A2D38.toInt()
        private val LIST_BG = 0xFF14161D.toInt()

        // &-code → RGB for the clickable color key (matches the main /fm legend).
        private val CODE_COLORS: Array<IntArray> = arrayOf(
            intArrayOf('0'.code, 0x000000), intArrayOf('1'.code, 0x0000AA), intArrayOf('2'.code, 0x00AA00), intArrayOf('3'.code, 0x00AAAA),
            intArrayOf('4'.code, 0xAA0000), intArrayOf('5'.code, 0xAA00AA), intArrayOf('6'.code, 0xFFAA00), intArrayOf('7'.code, 0xAAAAAA),
            intArrayOf('8'.code, 0x555555), intArrayOf('9'.code, 0x5555FF), intArrayOf('a'.code, 0x55FF55), intArrayOf('b'.code, 0x55FFFF),
            intArrayOf('c'.code, 0xFF5555), intArrayOf('d'.code, 0xFF55FF), intArrayOf('e'.code, 0xFFFF55), intArrayOf('f'.code, 0xFFFFFF)
        )

        // Format codes shown as live styled samples (code char, sample text).
        private val CODE_FORMATS: Array<Array<String>> = arrayOf(
            arrayOf("l", "§lB"), arrayOf("o", "§oI"), arrayOf("n", "§nU"), arrayOf("m", "§mS"), arrayOf("k", "§kMM"), arrayOf("r", "§rR")
        )

        // Hypixel SkyBlock dyes (name, RRGGBB). Not exhaustive — the hex box covers anything missing.
        private val DYES: Array<Array<String>> = arrayOf(
            arrayOf("Pure White", "FFFFFF"), arrayOf("Pure Black", "000000"), arrayOf("Pure Yellow", "FFF700"), arrayOf("Pure Blue", "0013FF"),
            arrayOf("Aquamarine", "7FFFD4"), arrayOf("Bingo Blue", "002FA7"), arrayOf("Bone", "E3DAC9"), arrayOf("Brick Red", "CB4154"),
            arrayOf("Byzantium", "702963"), arrayOf("Carmine", "960018"), arrayOf("Celadon", "ACE1AF"), arrayOf("Celeste", "B2FFFF"),
            arrayOf("Cyclamen", "F56FA1"), arrayOf("Dark Purple", "301934"), arrayOf("Emerald", "50C878"), arrayOf("Flame", "E25822"),
            arrayOf("Holly", "3C6746"), arrayOf("Iceberg", "71A6D2"), arrayOf("Livid", "6699CC"), arrayOf("Mango", "FDBE02"),
            arrayOf("Midnight", "702670"), arrayOf("Nadeshiko", "F6ADC6"), arrayOf("Necron", "E7413C"), arrayOf("Nyanza", "E9FFDB"),
            arrayOf("Tentacle", "324D6C"), arrayOf("Wild Strawberry", "FF43A4"),
            // vanilla leather dyes
            arrayOf("White", "F9FFFE"), arrayOf("Light Gray", "999999"), arrayOf("Gray", "4C4C4C"), arrayOf("Ink Sac (Black)", "191919"),
            arrayOf("Rose Red", "993333"), arrayOf("Orange", "D87F33"), arrayOf("Dandelion Yellow", "E5E533"), arrayOf("Lime", "7FCC19"),
            arrayOf("Cactus Green", "667F33"), arrayOf("Light Blue", "6699D8"), arrayOf("Cyan", "4C7F99"), arrayOf("Lapis (Blue)", "334CB2"),
            arrayOf("Purple", "7F3FB2"), arrayOf("Magenta", "B24CD8"), arrayOf("Pink", "F27FA5"), arrayOf("Cocoa (Brown)", "664C33")
        )
        private val DYE_RGB: IntArray = IntArray(DYES.size) { i -> java.lang.Long.parseLong(DYES[i][1], 16).toInt() }

        // Vanilla armor trim registry ids.
        private val TRIM_MATERIALS = arrayOf(
            "quartz", "iron", "netherite", "redstone", "copper", "gold",
            "emerald", "diamond", "lapis", "amethyst", "resin"
        )
        private val TRIM_PATTERNS = arrayOf(
            "sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout",
            "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"
        )

        private fun dyeIndex(rgbIn: Int): Int {
            val rgb = rgbIn and 0xFFFFFF
            for (i in DYE_RGB.indices) if ((DYE_RGB[i] and 0xFFFFFF) == rgb) return i
            return -1
        }

        private fun indexOf(arr: Array<String>, v: String?): Int {
            if (v == null) return -1
            for (i in arr.indices) if (arr[i].equals(v, ignoreCase = true)) return i
            return -1
        }

        private fun cap(s: String): String {
            return if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)
        }

        private fun inBox(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
            return mx >= x && mx <= x + w && my >= y && my <= y + h
        }

        private fun brightness(rgb: Int): Int {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
    }
}
