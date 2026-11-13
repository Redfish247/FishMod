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
    private const val FOOT_H = 2
    private const val GAP = 8
    private const val SEARCH_H = 14
    private const val TAB_COLS = 1
    private const val TAB_CELL = 18
    private const val TAB_W = TAB_COLS * TAB_CELL
    private const val TAB_H = TAB_CELL

    private var ids: Array<String> = emptyArray()
    private var hay: Array<String> = emptyArray()
    private var rar: IntArray = IntArray(0)
    private var shown: IntArray = IntArray(0)
    private var cat: IntArray = IntArray(0)
    private var allIdx: IntArray = IntArray(0)
    // Custom in-category order (Shards, Gems & Ores, Garden): sub-group, family, tier, then name
    private val catOrder = arrayOfNulls<IntArray>(23)
    private var selCat = 0

    private val CAT_NAMES = arrayOf(
        "All", "Weapons", "Armor", "Accessories", "Tools", "Pets", "Books", "Other", "Favorites",
        "Minions", "Cosmetics", "Dyes", "Shards", "Trophy Fish & Frogs", "Fishing", "Reforges", "Gems & Ores",
        "Garden", "Dungeon", "Consumables", "NPCs", "Travel Scrolls", "Vanilla"
    )
    // Tab order top to bottom, two columns (Favorites first); values index CAT_NAMES
    private val TAB_ORDER = intArrayOf(8, 0, 1, 2, 3, 4, 14, 5, 6, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 7)
    private val CAT_ICONS: Array<net.minecraft.world.item.Item> = arrayOf(
        net.minecraft.world.item.Items.BOOKSHELF,
        net.minecraft.world.item.Items.DIAMOND_SWORD,
        net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
        net.minecraft.world.item.Items.ENDER_EYE,
        net.minecraft.world.item.Items.DIAMOND_PICKAXE,
        net.minecraft.world.item.Items.BONE,
        net.minecraft.world.item.Items.ENCHANTED_BOOK,
        net.minecraft.world.item.Items.CHEST,
        net.minecraft.world.item.Items.NETHER_STAR,
        net.minecraft.world.item.Items.ARMOR_STAND,
        net.minecraft.world.item.Items.PAINTING,
        net.minecraft.world.item.Items.INK_SAC,
        net.minecraft.world.item.Items.PRISMARINE_SHARD,
        net.minecraft.world.item.Items.TROPICAL_FISH,
        net.minecraft.world.item.Items.FISHING_ROD,
        net.minecraft.world.item.Items.ANVIL,
        net.minecraft.world.item.Items.EMERALD,
        net.minecraft.world.item.Items.WHEAT,
        net.minecraft.world.item.Items.SKELETON_SKULL,
        net.minecraft.world.item.Items.POTION,
        net.minecraft.world.item.Items.PLAYER_HEAD,
        net.minecraft.world.item.Items.ENDER_PEARL,
        net.minecraft.world.item.Items.GRASS_BLOCK
    )

    private val GEM_PREFIX = Regex("^(ENCHANTED|REFINED|ROUGH|FLAWED|FINE|FLAWLESS|PERFECT)_")
    private val GEM_SUFFIX = Regex("_(BLOCK|ORE|GEM)$")
    private val GEM_TIERS = listOf("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT")

    private fun kindOf(id: String): String {
        val lore = NeuRepo.get(id)?.getAsJsonArray("lore") ?: return ""
        for (k in lore.size() - 1 downTo maxOf(0, lore.size() - 2)) {
            val line = TextStrip.plain(lore[k].asString).trim().uppercase()
            val m = RARITY_RE.find(line) ?: continue
            return line.substring(m.range.last + 1).trim()
        }
        return ""
    }

    // Sort keys for the categories that get a custom order: (sub-group, family, tier)
    private fun sortKey(c: Int, id: String): Triple<Int, String, Int> = when (c) {
        17 -> {
            val k = kindOf(id)
            Triple(when { k == "FARMING TOOL" -> 0; k == "MUTATION" -> 1; k == "GARDEN CHIP" -> 2; k == "WATERING CAN" -> 3; k == "VACUUM" -> 4; else -> 5 }, "", 0)
        }
        16 -> {
            val pre = GEM_PREFIX.find(id)?.groupValues?.get(1)
            val fam = GEM_SUFFIX.replace(GEM_PREFIX.replace(id, ""), "")
            val tier = when {
                pre == null -> if (id.endsWith("_BLOCK")) 3 else 0
                pre == "ENCHANTED" -> if (id.endsWith("_BLOCK")) 2 else 1
                pre == "REFINED" -> 2
                else -> GEM_TIERS.indexOf(pre).coerceAtLeast(0)
            }
            Triple(0, fam, tier)
        }
        3 -> {
            val k = kindOf(id)
            val fam = ACC_SUFFIX.replace(ACC_SUFFIX.replace(id, ""), "")
            val rank = RARITY_RANK.indexOfFirst { rarityNameOf(id) == it }.coerceAtLeast(0)
            Triple(if (k.startsWith("DUNGEON")) 1 else 0, fam, rank)
        }
        4 -> {
            val w = kindOf(id).split(' ').toSet()
            Triple(when {
                w.any { it == "PICKAXE" || it == "DRILL" || it == "GAUNTLET" || it == "CHISEL" } -> 0
                "AXE" in w -> 1
                "SHOVEL" in w -> 2
                "HOE" in w -> 3
                "SHEARS" in w -> 4
                else -> 5
            }, "", 0)
        }
        12 -> {
            val lore = NeuRepo.get(id)?.getAsJsonArray("lore")
            var fam = ""
            if (lore != null) for (k in lore.size() - 1 downTo maxOf(0, lore.size() - 3)) {
                val line = TextStrip.plain(lore[k].asString).trim().uppercase()
                if (line.contains(" SHARD")) { fam = line.substringBefore(' '); break }
            }
            Triple(0, fam, 0)
        }
        else -> Triple(0, "", 0)
    }

    private val MINION_RE = Regex("_GENERATOR_[0-9]+$")

    // Category from the item type words after the rarity in the last NEU lore lines ("LEGENDARY DUNGEON SWORD"), then id/name hints
    private val MOB_RE = Regex("[(](Monster|Boss|Miniboss|Animal|Sea Creature|Pest|Mythological Creature|Mayor|Retired Mayor)[)]$", RegexOption.IGNORE_CASE)

    // NEU lists mobs, bosses and mayors as items; they are not items, so they are left out of the list
    private fun isMob(id: String): Boolean = MOB_RE.containsMatchIn(TextStrip.plain(NeuRepo.get(id)?.get("displayname")?.asString ?: "").trim())

    private fun isNpc(id: String): Boolean = id.endsWith("_NPC") || TextStrip.plain(NeuRepo.get(id)?.get("displayname")?.asString ?: "").trim().endsWith("(NPC)")

    // Plain Minecraft items: id is the vanilla item name (or NAME:data) and NEU gives them no item type
    private fun isVanilla(id: String, itemId: String?): Boolean {
        if (itemId == null || !itemId.startsWith("minecraft:") || id.any { it.isLowerCase() } || id.contains(';')) return false
        return id.contains(':') || itemId.removePrefix("minecraft:").uppercase() == id
    }

    private val ACC_SUFFIX = Regex("_(TALISMAN|RING|ARTIFACT|RELIC|CHARM|[0-9]+)$")
    private val RARITY_RANK = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "SUPREME")

    private fun catOf(id: String): Int {
        val o = NeuRepo.get(id) ?: return 7
        val itemId = o.get("itemid")?.asString
        if (itemId == "minecraft:enchanted_book") return 6
        if (PET_ID.matches(id)) return 5
        if (MINION_RE.containsMatchIn(id)) return 9
        if (id.startsWith("ATTRIBUTE_SHARD_")) return 12
        val name = TextStrip.plain(o.get("displayname")?.asString ?: "")
        if (isNpc(id)) return 20
        val lore = o.getAsJsonArray("lore")
        if (lore != null) for (k in lore.size() - 1 downTo maxOf(0, lore.size() - 2)) {
            val line = TextStrip.plain(lore[k].asString).trim().uppercase()
            val m = RARITY_RE.find(line) ?: continue
            val kind = line.substring(m.range.last + 1).trim()
            val w = kind.split(' ').toSet()
            val r = when {
                w.any { it == "HELMET" || it == "CHESTPLATE" || it == "LEGGINGS" || it == "BOOTS" } -> 2
                w.any { it == "ACCESSORY" || it == "HATCESSORY" || it == "BELT" || it == "NECKLACE" || it == "CLOAK" || it == "GLOVES" || it == "BRACELET" } -> 3
                "COSMETIC" in w -> 10
                "DYE" in w -> 11
                "SHARD" in w -> 12
                "TROPHY" in w -> 13
                kind == "REFORGE STONE" -> 15
                "PET" in w -> 5
                kind == "FISHING ROD" || "BAIT" in w || kind == "ROD PART" || kind == "FISHING NET" -> 14
                kind == "FARMING TOOL" || "MUTATION" in w || kind == "GARDEN CHIP" || kind == "WATERING CAN" || "VACUUM" in w -> 17
                w.any { it == "PICKAXE" || it == "AXE" || it == "SHOVEL" || it == "HOE" || it == "DRILL" || it == "GAUNTLET" || it == "SHEARS" || it == "CHISEL" } -> 4
                w.any { it == "SWORD" || it == "LONGSWORD" || it == "BOW" || it == "SHORTBOW" || it == "WAND" || it == "WEAPON" } -> 1
                "GEMSTONE" in w || "ORE" in w || kind == "DWARVEN METAL" -> 16
                kind.startsWith("DUNGEON") -> 18
                kind == "TRAVEL SCROLL" -> 21
                "BOOSTER" in w || "SALT" in w || "DEPLOYABLE" in w || "CAPSULE" in w -> 19
                else -> -1
            }
            if (r >= 0) return r
            break
        }
        return when {
            itemId == "minecraft:potion" || id.startsWith("POTION_") -> 7
            name.endsWith(" Shard", true) && itemId != "minecraft:prismarine_shard" -> 12
            id.endsWith("TRAVEL_SCROLL") -> 21
            name.contains("Potion", true) -> 7
            isVanilla(id, itemId) -> 22
            else -> 7
        }
    }

    private val RARITY_RE = Regex("(VERY SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|SUPREME)")
    private fun rarityRgb(name: String) = when (name) {
        "COMMON" -> 0xFFFFFF; "UNCOMMON" -> 0x55FF55; "RARE" -> 0x5555FF; "EPIC" -> 0xAA00AA
        "LEGENDARY" -> 0xFFAA00; "MYTHIC" -> 0xFF55FF; "DIVINE" -> 0x55FFFF
        "ULTIMATE", "SUPREME" -> 0xAA0000; else -> 0xFF5555
    }

    private fun rarityNameOf(id: String): String {
        val lore = NeuRepo.get(id)?.getAsJsonArray("lore") ?: return ""
        for (k in lore.size() - 1 downTo maxOf(0, lore.size() - 2)) {
            val m = RARITY_RE.find(TextStrip.plain(lore[k].asString).trim().uppercase()) ?: continue
            return m.value
        }
        return ""
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
        NpcTravel.init()
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
        val all = NeuRepo.ids().filter { !isMob(it) }.toTypedArray()
        val names = Array(all.size) { i ->
            TextStrip.plain(NeuRepo.get(all[i])?.get("displayname")?.asString ?: all[i]).filter { it.code !in 0xE000..0xF8FF }.trim().lowercase()
        }
        val order = all.indices.sortedWith(compareBy({ names[it] }, { all[it] }))
        ids = Array(all.size) { all[order[it]] }
        hay = Array(all.size) { names[order[it]] + " " + all[order[it]].lowercase() }
        rar = IntArray(all.size) { rarityOf(ids[it]) }
        cat = IntArray(all.size) { catOf(ids[it]) }
        allIdx = IntArray(all.size) { it }
        for (c in intArrayOf(3, 4, 12, 16, 17)) {
            val keys = HashMap<Int, Triple<Int, String, Int>>()
            val idxs = allIdx.filter { cat[it] == c }
            for (i in idxs) keys[i] = sortKey(c, ids[i])
            catOrder[c] = idxs.sortedWith(compareBy({ keys[it]!!.first }, { keys[it]!!.second }, { keys[it]!!.third }, { it })).toIntArray()
        }
        shown = IntArray(all.size)
        built = true
        refilter()
        return true
    }

    private fun refilter() {
        val q = query.trim().lowercase()
        var n = 0
        favOnly = selCat == 8
        val src = catOrder[selCat] ?: allIdx
        for (i in src) if (favs.contains(ids[i]) && (selCat == 0 || selCat == 8 || cat[i] == selCat) && (q.isEmpty() || hay[i].contains(q))) shown[n++] = i
        if (!favOnly) for (i in src) if (!favs.contains(ids[i]) && (selCat == 0 || cat[i] == selCat) && (q.isEmpty() || hay[i].contains(q))) shown[n++] = i
        shownCount = n
        scroll = 0
    }

    private fun layout(screen: AbstractContainerScreen<*>): Boolean {
        val mc = Minecraft.getInstance()
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val acc = screen as HandledScreenAccessor
        val avail = (sw * 3 / 5 - PAD * 2 - TAB_W) / CELL
        cols = minOf(FishSettings.skyItemListColumns, avail)
        if (cols < 2) return false
        pw = PAD * 2 + cols * CELL + TAB_W
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
        val sx = px + PAD; val sy = py + 6; val sw = pw - PAD * 2 - TAB_W
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
            ctx.fill(gridRight() - 4, barY, gridRight() - 2, barY + barH, accent)
        }
        val tabHover = drawTabs(ctx, mx, my, accent)
        // afterExtract runs after the screen's deferred pass, so flush the tooltip ourselves
        if (tabHover >= 0) {
            ctx.setTooltipForNextFrame(font, Component.literal(CAT_NAMES[tabHover]), mx, my)
            ctx.extractDeferredElements(mx, my, 0f)
        } else if (hover >= 0) {
            val id = ids[shown[hover]]
            val stack = RecipeViewer.stackFor(id)
            ctx.setTooltipForNextFrame(font, tooltipLines(id, stack), stack.tooltipImage, mx, my)
            ctx.extractDeferredElements(mx, my, 0f)
        }
    }

    private fun gridRight() = px + pw - TAB_W

    // One tab column: scrolls with the wheel when the screen is too short for every tab
    private var tabScroll = 0
    private fun tabRows() = ((ph - HEAD_H - 2) / TAB_H).coerceAtLeast(1)
    private fun tabMaxScroll() = (TAB_ORDER.size - tabRows()).coerceAtLeast(0)

    private fun tabAt(mx: Double, my: Double): Int {
        if (mx < gridRight() || mx >= px + pw) return -1
        if (my < py + HEAD_H) return -1
        val row = ((my - py - HEAD_H) / TAB_H).toInt()
        val slot = row + tabScroll
        return if (row in 0 until tabRows() && slot in TAB_ORDER.indices) TAB_ORDER[slot] else -1
    }

    // Category tabs down the far right edge; returns the hovered tab or -1
    private fun drawTabs(ctx: GuiGraphicsExtractor, mx: Int, my: Int, accent: Int): Int {
        val hov = tabAt(mx.toDouble(), my.toDouble())
        val x0 = gridRight()
        tabScroll = tabScroll.coerceIn(0, tabMaxScroll())
        for ((slot, i) in TAB_ORDER.withIndex()) {
            val row = slot - tabScroll
            if (row < 0 || row >= tabRows()) continue
            val tx = x0
            val y0 = py + HEAD_H + row * TAB_H
            val sel = i == selCat
            ctx.fill(tx, y0, tx + TAB_CELL - 1, y0 + TAB_H - 1, if (sel) argb(FishSettings.skyItemListAccentColor, 40) else if (i == hov) 0x40FFFFFF else 0x30000000)
            if (sel) ctx.fill(tx, y0 + TAB_H - 2, tx + TAB_CELL - 1, y0 + TAB_H - 1, accent)
            fishmod.features.ItemRarityHotbar.suppress = true
            try { ctx.item(net.minecraft.world.item.ItemStack(CAT_ICONS[i]), tx + 1, y0 + 1) } finally { fishmod.features.ItemRarityHotbar.suppress = false }
        }
        return hov
    }

    private val PET_ID = Regex("^([A-Z0-9_]+);([0-5])$")

    // Item tooltip + price lines, plus the yellow click key (mirrors onClick) when that setting is on
    private fun tooltipLines(id: String, stack: ItemStack): List<Component> {
        // The list's icon stacks carry no Skyblock id; tag a copy so the normal price lines (Value, 3 Day Avg, Low BIN, NPC Sell) are added to it
        val tagged = stack.copy()
        val petTier = PET_ID.matchEntire(id)?.takeIf { NeuRepo.get(id)?.get("displayname")?.asString?.contains("[Lvl") == true }
        val tag = net.minecraft.nbt.CompoundTag()
        if (petTier != null) {
            // List pets are shown at max level: price them as a PET stack with enough xp
            val tier = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")[petTier.groupValues[2].toInt()]
            tag.putString("id", "PET")
            tag.putString("petInfo", "{\"type\":\"${petTier.groupValues[1]}\",\"tier\":\"$tier\",\"exp\":1.0E9}")
        } else tag.putString("id", id)
        tagged.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
        val lines = ArrayList(Screen.getTooltipFromItem(Minecraft.getInstance(), tagged))
        lines.addAll(bazaarLines(id))
        if (!FishSettings.skyItemListClickKey) return lines
        lines.add(Component.empty())
        val npc = isNpc(id)
        if (npc) lines.add(Component.literal("§eLeft Click: Waypoint to NPC"))
        else if (RecipeViewer.recipesFor(id).isNotEmpty()) lines.add(Component.literal("§eLeft Click: View Recipe"))
        lines.add(Component.literal(if (favs.contains(id)) "§eRight Click: Remove Favorite" else "§eRight Click: Add Favorite"))
        if (!npc) lines.add(Component.literal(if (onBazaar(id)) "§eShift-Left Click: Open in Bazaar" else "§eShift-Left Click: Open in Auction House"))
        return lines
    }

    // Bazaar items also get their sell offer / insta sell price (the normal lines only cover Value, 3 Day Avg, Low BIN, NPC)
    private fun bazaarLines(id: String): List<Component> {
        if (!FishSettings.itemTooltipPrices) return emptyList()
        val key = bazaarKey(id)
        val out = ArrayList<Component>(2)
        CroesusPrices.bazaarPrice(key, true)?.takeIf { it > 0 }?.let { out.add(Component.literal("§eBazaar Sell Offer: §6${fishmod.utils.FishUtil.abbr(it)}")) }
        CroesusPrices.bazaarPrice(key, false)?.takeIf { it > 0 }?.let { out.add(Component.literal("§eBazaar Insta Sell: §6${fishmod.utils.FishUtil.abbr(it)}")) }
        return out
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
        if (x >= sx && x < sx + pw - PAD * 2 - TAB_W && y >= sy && y < sy + SEARCH_H) {
            focused = true; selectAll = false
            if (GLFW.btn(click) == 1 && query.isNotEmpty()) { query = ""; if (built) refilter() }
            return true
        }
        focused = false; selectAll = false
        if (!built) return true
        val t = tabAt(x, y)
        if (t >= 0) {
            val n = if (t == selCat) 0 else t
            if (GLFW.btn(click) == 0 && n != selCat) { selCat = n; refilter() }
            else if (GLFW.btn(click) == 0) { selCat = 0; refilter() }
            return true
        }
        if (GLFW.btn(click) == 0 && maxScroll() > 0 && x >= gridRight() - 8 && x < gridRight() && y >= py + HEAD_H && y < py + HEAD_H + rowsVis * CELL) {
            sbDrag = true
            sbSet(y)
            return true
        }
        val c = cellAt(x, y)
        if (c < 0) return true
        val id = ids[shown[c]]
        when (GLFW.btn(click)) {
            0 -> if (isNpc(id)) NpcTravel.go(id)
            else if (Minecraft.getInstance().hasShiftDown()) openMarket(id)
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
        if (mx >= gridRight()) { tabScroll = (tabScroll + if (v > 0) -1 else 1).coerceIn(0, tabMaxScroll()); return true }
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
                    val clip = kb.clipboard.filter { !Character.isISOControl(it) }
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
