package fishmod.features.item

import fishmod.compat.GLFW
import fishmod.compat.showScreen
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.features.croesus.CroesusPrices
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.NeuRepo
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

// JEI-style side panel of NEU Skyblock items; cached icons, only visible rows drawn, filter only on query change.
object SkyItemList {

    private const val CELL = 20
    private const val PAD = 6
    private const val HEAD_H = 26
    private const val FOOT_H = 12
    private const val GAP = 8
    private const val SEARCH_H = 14

    private var ids: Array<String> = emptyArray()
    private var hay: Array<String> = emptyArray()
    private var rar: IntArray = IntArray(0)
    private var shown: IntArray = IntArray(0)

    private val RARITY_RE = Regex("(VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|SUPREME)")
    private fun rarityRgb(name: String) = when (name) {
        "COMMON" -> 0xFFFFFF; "UNCOMMON" -> 0x55FF55; "RARE" -> 0x5555FF; "EPIC" -> 0xAA00AA
        "LEGENDARY" -> 0xFFAA00; "MYTHIC" -> 0xFF55FF; "DIVINE" -> 0x55FFFF
        "ULTIMATE", "SUPREME" -> 0xAA0000; else -> 0xFF5555
    }

    // Rarity is the keyword in the last NEU lore lines; 0 = unknown
    private fun rarityOf(id: String): Int {
        val lore = NeuRepo.get(id)?.getAsJsonArray("lore") ?: return 0
        for (k in lore.size() - 1 downTo maxOf(0, lore.size() - 2)) {
            val m = RARITY_RE.find(TextStrip.plain(lore[k].asString).trim().uppercase()) ?: continue
            return 0xFF000000.toInt() or rarityRgb(m.value)
        }
        return 0
    }
    private var shownCount = 0
    private var built = false

    private var query = ""
    private var selectAll = false
    private var focused = false
    private var scroll = 0
    private var favOnly = false

    private val favFile get() = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("FishMod").resolve("item-list-favorites.txt")
    private val favs: MutableSet<String> = java.util.LinkedHashSet<String>().also { s ->
        runCatching { java.nio.file.Files.readAllLines(favFile).filter { it.isNotBlank() }.forEach(s::add) }
    }

    private fun saveFavs() {
        runCatching {
            java.nio.file.Files.createDirectories(favFile.parent)
            java.nio.file.Files.write(favFile, favs.toList())
        }.onFailure { FishDiag.fail("SkyItemList.2", "favorites save failed", it) }
    }

    // layout, rewritten by layout() (ints only, no allocation)
    private var px = 0; private var py = 0; private var pw = 0; private var ph = 0
    private var cols = 1; private var rowsVis = 1

    @JvmStatic
    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                try { render(ctx, mx, my, screen) } catch (t: Throwable) { FishDiag.fail("SkyItemList.1", "item list render failed", t) }
            })
        })
    }

    private fun on() = FishSettings.skyItemList

    // Hidden over the storage/ender chest overlay
    private fun hiddenFor(screen: AbstractContainerScreen<*>) = fishmod.features.storage.StorageOverlay.isActive(screen) || fishmod.features.dungeon.LeapMenu.isActive(screen)

    @JvmStatic fun panelActive() = on() && System.currentTimeMillis() - fishmod.utils.rendering.FishHudLayer.panelSeenMs() < 150

    private fun ensureBuilt(): Boolean {
        if (built) return true
        CroesusPrices.refreshIfStale()
        NeuRepo.ensureLoaded()
        if (!NeuRepo.isLoaded()) return false
        val all = NeuRepo.ids().toTypedArray()
        val names = Array(all.size) { i ->
            TextStrip.plain(NeuRepo.get(all[i])?.get("displayname")?.asString ?: all[i]).lowercase()
        }
        val order = all.indices.sortedWith(compareBy({ names[it] }, { all[it] }))
        ids = Array(all.size) { all[order[it]] }
        hay = Array(all.size) { names[order[it]] + " " + all[order[it]].lowercase() }
        rar = IntArray(all.size) { rarityOf(ids[it]) }
        shown = IntArray(all.size)
        built = true
        refilter()
        return true
    }

    private fun refilter() {
        val q = query.trim().lowercase()
        var n = 0
        for (i in hay.indices) if (favs.contains(ids[i]) && (q.isEmpty() || hay[i].contains(q))) shown[n++] = i
        if (!favOnly) for (i in hay.indices) if (!favs.contains(ids[i]) && (q.isEmpty() || hay[i].contains(q))) shown[n++] = i
        shownCount = n
        scroll = 0
    }

    private fun layout(screen: AbstractContainerScreen<*>): Boolean {
        val mc = Minecraft.getInstance()
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val acc = screen as HandledScreenAccessor
        val avail = (sw * 3 / 5 - PAD * 2) / CELL
        cols = minOf(FishSettings.skyItemListColumns, avail)
        if (cols < 2) return false
        pw = PAD * 2 + cols * CELL
        px = sw - pw
        py = 0
        ph = sh
        rowsVis = ((ph - HEAD_H - FOOT_H) / CELL).coerceAtLeast(1)
        return true
    }

    private fun maxScroll(): Int = ((shownCount + cols - 1) / cols - rowsVis).coerceAtLeast(0)

    private fun inPanel(mx: Double, my: Double) = mx >= px && mx < px + pw && my >= py && my < py + ph

    private fun cellAt(mx: Double, my: Double): Int {
        val gx = (mx - px - PAD).toInt()
        val gy = (my - py - HEAD_H).toInt()
        if (gx < 0 || gy < 0 || gx >= cols * CELL || gy >= rowsVis * CELL) return -1
        val idx = (scroll + gy / CELL) * cols + gx / CELL
        return if (idx < shownCount) idx else -1
    }

    // Corner-chopped fills give a rounded look without textures
    private fun rarityBox(ctx: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        if (!FishSettings.skyItemListRarityRound) { ctx.fill(x0, y0, x1, y1, c); return }
        ctx.fill(x0 + 2, y0, x1 - 2, y1, c)
        ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, c)
        ctx.fill(x0, y0 + 2, x1, y1 - 2, c)
    }

    private fun argb(rgb: Int, alphaPct: Int) = ((alphaPct.coerceIn(0, 100) * 255 / 100) shl 24) or (rgb and 0xFFFFFF)

    private fun render(ctx: GuiGraphicsExtractor, mx: Int, my: Int, screen: AbstractContainerScreen<*>) {
        if (!on() || hiddenFor(screen) || !layout(screen)) return
        fishmod.utils.rendering.FishHudLayer.setPanel(px, py, px + pw, py + ph)
        val font = Minecraft.getInstance().font
        val bg = argb(FishSettings.skyItemListBgColor, FishSettings.skyItemListOpacity)
        val accent = FishSettings.skyItemListAccentColor or 0xFF000000.toInt()
        ctx.fill(px, py, px + pw, py + ph, bg)
        ctx.fill(px, py, px + pw, py + 1, accent)
        val sx = px + PAD; val sy = py + 6; val sw = pw - PAD * 2
        ctx.fill(sx, sy, sx + sw, sy + SEARCH_H, 0x80000000.toInt())
        if (focused) ctx.fill(sx, sy + SEARCH_H - 1, sx + sw, sy + SEARCH_H, accent)
        if (!built && !ensureBuilt()) {
            ctx.text(font, "Loading items...", sx + 3, sy + 3, 0xFF9AA4B0.toInt(), false)
            return
        }
        if (query.isEmpty() && !focused) ctx.text(font, "Search items...", sx + 3, sy + 3, 0xFF6B7480.toInt(), false)
        else {
            val shown = font.plainSubstrByWidth(query + if (focused && !selectAll) "_" else "", sw - 6, true)
            if (selectAll && focused) ctx.fill(sx + 2, sy + 2, sx + 4 + font.width(shown), sy + SEARCH_H - 2, 0x80336699.toInt())
            ctx.text(font, shown, sx + 3, sy + 3, 0xFFFFFFFF.toInt(), false)
        }

        scroll = scroll.coerceIn(0, maxScroll())
        var hover = -1
        val first = scroll * cols
        val last = minOf(shownCount, first + rowsVis * cols)
        val hoverIdx = cellAt(mx.toDouble(), my.toDouble())
        for (i in first until last) {
            val k = i - first
            val cx = px + PAD + (k % cols) * CELL
            val cy = py + HEAD_H + (k / cols) * CELL
            val rc = if (FishSettings.skyItemListRarityBg) rar[shown[i]] else 0
            if (rc != 0) rarityBox(ctx, cx, cy, cx + CELL - 1, cy + CELL - 1, argb(rc, FishSettings.skyItemListRarityOpacity))
            if (i == hoverIdx) { ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, argb(FishSettings.skyItemListAccentColor, 35)); hover = i }
            fishmod.features.ItemRarityHotbar.suppress = true
            try { ctx.item(RecipeViewer.stackFor(ids[shown[i]]), cx + 2, cy + 2) } finally { fishmod.features.ItemRarityHotbar.suppress = false }
            if (favs.contains(ids[shown[i]])) ctx.fill(cx + CELL - 6, cy + 1, cx + CELL - 2, cy + 5, 0xFFFFD83A.toInt())
        }
        val ms = maxScroll()
        if (ms > 0) {
            val trackH = rowsVis * CELL
            val barH = (trackH * rowsVis / (rowsVis + ms)).coerceAtLeast(10)
            val barY = py + HEAD_H + (trackH - barH) * scroll / ms
            ctx.fill(px + pw - 4, barY, px + pw - 2, barY + barH, accent)
        }
        val foot = if (hover >= 0) "L: recipe  R: favorite" else "$shownCount items"
        ctx.text(font, foot, px + PAD, py + ph - FOOT_H + 2, 0xFF6B7480.toInt(), false)
        val favLabel = if (favOnly) "[Favs: on]" else "[Favs: off]"
        ctx.text(font, favLabel, px + pw - PAD - font.width(favLabel), py + ph - FOOT_H + 2, if (favOnly) 0xFFFFD83A.toInt() else 0xFF9AA4B0.toInt(), false)
        // afterExtract runs after the screen's deferred pass, so flush the tooltip ourselves
        if (hover >= 0) {
            val id = ids[shown[hover]]
            val stack = RecipeViewer.stackFor(id)
            if (FishSettings.skyItemListClickKey) ctx.setTooltipForNextFrame(font, tooltipWithKey(id, stack), stack.tooltipImage, mx, my)
            else ctx.setTooltipForNextFrame(font, stack, mx, my)
            ctx.extractDeferredElements(mx, my, 0f)
        }
    }

    // Yellow click key appended under the item's own tooltip (mirrors onClick)
    private fun tooltipWithKey(id: String, stack: ItemStack): List<Component> {
        val lines = ArrayList(Screen.getTooltipFromItem(Minecraft.getInstance(), stack))
        lines.add(Component.empty())
        if (RecipeViewer.recipesFor(id).isNotEmpty()) lines.add(Component.literal("§eLeft Click: View Recipe"))
        lines.add(Component.literal(if (favs.contains(id)) "§eRight Click: Remove Favorite" else "§eRight Click: Add Favorite"))
        lines.add(Component.literal(if (onBazaar(id)) "§eShift-Left Click: Open in Bazaar" else "§eShift-Left Click: Open in Auction House"))
        return lines
    }

    private fun bazaarKey(id: String): String {
        val semi = id.indexOf(';')
        return if (semi > 0 && NeuRepo.get(id)?.get("itemid")?.asString == "minecraft:enchanted_book") "ENCHANTMENT_${id.substring(0, semi)}_${id.substring(semi + 1)}" else id
    }

    private fun onBazaar(id: String): Boolean {
        val key = bazaarKey(id)
        return CroesusPrices.bazaarPrice(key, true) != null || CroesusPrices.bazaarPrice(key, false) != null
    }

    private val LVL_RE = Regex("^\\[Lvl [^\\]]*]\\s*")

    // Search term: NEU display name, book enchant name from lore, pet level tag dropped
    private fun searchName(id: String): String {
        val o = NeuRepo.get(id)
        var name = TextStrip.plain(o?.get("displayname")?.asString ?: id).trim()
        if (name.equals("Enchanted Book", true)) o?.getAsJsonArray("lore")?.takeIf { it.size() > 0 }?.let { name = TextStrip.plain(it[0].asString).trim() }
        return LVL_RE.replace(name, "")
    }

    // One shift-click = one /bz or /ahs command
    private fun openMarket(id: String) {
        val conn = Minecraft.getInstance().connection ?: return
        val name = searchName(id)
        if (name.isEmpty()) return
        CroesusPrices.refreshIfStale()
        conn.sendCommand(if (onBazaar(id)) "bz $name" else "ahs $name")
    }

    @JvmStatic
    fun onClick(click: MouseButtonEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (!on() || hiddenFor(screen) || !layout(screen)) return false
        val x = click.x(); val y = click.y()
        if (!inPanel(x, y)) { focused = false; selectAll = false; return false }
        val sx = px + PAD; val sy = py + 6
        if (x >= sx && x < sx + pw - PAD * 2 && y >= sy && y < sy + SEARCH_H) {
            focused = true; selectAll = false
            if (GLFW.btn(click) == 1 && query.isNotEmpty()) { query = ""; if (built) refilter() }
            return true
        }
        focused = false; selectAll = false
        if (!built) return true
        if (y >= py + ph - FOOT_H && x >= px + pw / 2) {
            favOnly = !favOnly; refilter(); return true
        }
        if (GLFW.btn(click) == 0 && maxScroll() > 0 && x >= px + pw - 8 && y >= py + HEAD_H && y < py + HEAD_H + rowsVis * CELL) {
            sbDrag = true
            sbSet(y)
            return true
        }
        val c = cellAt(x, y)
        if (c < 0) return true
        val id = ids[shown[c]]
        when (GLFW.btn(click)) {
            0 -> if (Minecraft.getInstance().hasShiftDown()) openMarket(id)
            else if (RecipeViewer.recipesFor(id).isNotEmpty()) Minecraft.getInstance().let { mc -> mc.execute { mc.showScreen(RecipeScreen(id, screen)) } }
            1 -> { if (!favs.remove(id)) favs.add(id); saveFavs(); val keep = scroll; refilter(); scroll = keep }
        }
        return true
    }

    private var sbDrag = false

    private fun sbSet(y: Double) {
        val ms = maxScroll()
        val trackH = rowsVis * CELL
        val barH = (trackH * rowsVis / (rowsVis + ms)).coerceAtLeast(10)
        val room = (trackH - barH).coerceAtLeast(1)
        scroll = Math.round((y - py - HEAD_H - barH / 2.0) * ms / room).toInt().coerceIn(0, ms)
    }

    @JvmStatic
    fun onDrag(y: Double): Boolean {
        if (!sbDrag) return false
        sbSet(y)
        return true
    }

    // Screen closed: a stuck drag or focused search box would swallow input on the next container screen
    @JvmStatic
    fun onClosed() {
        sbDrag = false
        focused = false
    }

    @JvmStatic
    fun onRelease(): Boolean {
        if (!sbDrag) return false
        sbDrag = false
        return true
    }

    @JvmStatic
    fun onScroll(mx: Double, my: Double, v: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on() || hiddenFor(screen) || !built || !layout(screen) || !inPanel(mx, my)) return false
        scroll = (scroll + if (v > 0) -1 else 1).coerceIn(0, maxScroll())
        return true
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent): Boolean {
        if (!on() || !focused) return false
        val ctrl = input.modifiers() and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SUPER) != 0
        if (ctrl) {
            val kb = Minecraft.getInstance().keyboardHandler
            when (input.key()) {
                GLFW.GLFW_KEY_V -> {
                    val clip = kb.clipboard?.filter { !Character.isISOControl(it) }.orEmpty()
                    if (selectAll) query = ""
                    selectAll = false
                    query = (query + clip).take(40); if (built) refilter()
                }
                GLFW.GLFW_KEY_C -> kb.clipboard = query
                GLFW.GLFW_KEY_X -> { kb.clipboard = query; query = ""; selectAll = false; if (built) refilter() }
                GLFW.GLFW_KEY_A -> selectAll = query.isNotEmpty()
                GLFW.GLFW_KEY_BACKSPACE -> {
                    query = if (selectAll) "" else query.trimEnd().substringBeforeLast(' ', "")
                    selectAll = false; if (built) refilter()
                }
            }
            return true
        }
        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE -> { focused = false; selectAll = false; return true }
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectAll) query = "" else if (query.isNotEmpty()) query = query.dropLast(1)
                selectAll = false; if (built) refilter()
            }
        }
        return true
    }

    @JvmStatic
    fun charTyped(input: CharacterEvent): Boolean {
        if (!on() || !focused) return false
        val cp = input.codepoint()
        if (Character.isISOControl(cp)) return true
        if (selectAll) { query = ""; selectAll = false }
        if (query.length >= 40) return true
        query += String(Character.toChars(cp))
        if (built) refilter()
        return true
    }
}
