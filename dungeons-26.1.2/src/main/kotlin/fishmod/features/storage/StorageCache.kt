package fishmod.features.storage

import fishmod.utils.config.FolderUtility
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections
import java.util.TreeMap

/**
 * Captures each SkyBlock storage page's contents as you page through `/storage` and persists them
 * per-account. Also learns which pages you actually own by scanning the "Storage" overview, so the
 * overlay can hide backpack slots you haven't bought.
 */
object StorageCache {

    private val dir: Path = Paths.get(FolderUtility.CONFIG_PATH + "storage")
    private val EMPTY_MARKERS = setOf(
        Blocks.RED_STAINED_GLASS_PANE.asItem(),
        Blocks.BROWN_STAINED_GLASS_PANE.asItem(),
        Items.GRAY_DYE,
    )

    @Volatile private var pages: TreeMap<Int, NBTInventory> = TreeMap()
    @Volatile private var known: MutableSet<Int> = sortedSetOf()
    /** pageIndex -> expected content rows, learned from the API layout fetch (in-memory only). */
    private val expectedRows = HashMap<Int, Int>()
    private var loadedFor: String? = null
    private var dirty = false
    private var lastSnapshot = 0L

    @JvmStatic fun view(): Map<Int, NBTInventory> = Collections.unmodifiableMap(pages)
    @JvmStatic fun knownPages(): Set<Int> = Collections.unmodifiableSet(known)
    @JvmStatic fun expectedRows(idx: Int): Int? = expectedRows[idx]

    /** Register which pages exist (+ their row counts) from the API layout fetch, without items. */
    @JvmStatic
    fun registerLayout(rows: Map<Int, Int>) {
        ensureLoaded()
        var changed = false
        for ((idx, r) in rows) {
            expectedRows[idx] = r
            if (known.add(idx)) changed = true
        }
        if (changed) { dirty = true; forceSave() }
    }

    /** Load this player's on-disk cache if it isn't loaded yet (for callers outside [tick]). */
    @JvmStatic
    fun ensureLoaded() {
        val id = uuid() ?: return
        if (id != loadedFor) { load(id); loadedFor = id }
    }

    /** Persist the cache right now (used by [StorageAutoLoader] after a bulk API load). */
    @JvmStatic
    fun forceSave() {
        if (loadedFor == null) loadedFor = uuid()
        if (loadedFor != null) { save(); dirty = false }
    }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
    }

    private fun uuid(): String? = Minecraft.getInstance().player?.gameProfile?.id?.toString()

    /** Store a page's contents right now (called every frame by the overlay for the open page). */
    @JvmStatic
    fun put(idx: Int, stacks: List<ItemStack>) {
        if (stacks.isEmpty() || stacks.all { it.isEmpty }) return
        pages[idx] = NBTInventory(stacks.map { it.copy() })
        known.add(idx)
        dirty = true
    }

    private fun tick(mc: Minecraft) {
        if (!FishSettings.storageOverlayEnabled) return
        val id = uuid() ?: return
        if (id != loadedFor) { load(id); loadedFor = id }

        val screen = mc.screen as? AbstractContainerScreen<*> ?: run { flush(); return }
        val plainTitle = screen.title.string.replace(Regex("§."), "")

        if (plainTitle == "Storage") { scanOverview(screen); return }
        val page = StoragePage.fromTitle(plainTitle) ?: run { flush(); return }

        val now = System.currentTimeMillis()
        if (now - lastSnapshot < 100) return
        lastSnapshot = now

        val menu = screen.menu as? ChestMenu ?: return   // storage pages are always chest menus
        val rows = menu.rowCount
        if (rows < 2) return                              // row 0 is the nav row; need >= 1 content row
        val contentRows = rows - 1
        // If the API told us the page's real size, only snapshot once the window matches it — a
        // partially-synced container is what produced stunted pages.
        val exp = expectedRows[page.index]
        if (exp != null && contentRows != exp) return

        val items = menu.slots.subList(9, rows * 9).map { it.item.copy() }
        pages[page.index] = NBTInventory(items)
        known.add(page.index)
        dirty = true
    }

    /** Reads the overview to learn which ender-chest / backpack pages exist. */
    private fun scanOverview(screen: AbstractContainerScreen<*>) {
        val menu = screen.menu
        val size = menu.slots.size - 36
        var changed = false
        for (i in 0 until size) {
            val slot = StoragePage.overviewIndex(i) ?: continue
            val stack = menu.slots[i].item
            if (stack.isEmpty) continue
            val owned = stack.item !in EMPTY_MARKERS
            if (owned && known.add(slot)) changed = true
            else if (!owned && known.remove(slot)) { pages.remove(slot); changed = true }
        }
        if (changed) dirty = true
    }

    private fun flush() { if (dirty) { save(); dirty = false } }

    private fun load(id: String) {
        pages = TreeMap(); known = sortedSetOf()
        val file = dir.resolve("$id.nbt")
        if (!Files.exists(file)) return
        try {
            val root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
            for (i in 0 until 27) {
                if (!root.contains("${i}_inv")) continue
                NBTInventory.decode(root.getString("${i}_inv").orElse(""))?.let { pages[i] = it; known.add(i) }
            }
            root.getString("known").orElse("").split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { known.add(it) }
        } catch (ignored: IOException) {}
    }

    private fun save() {
        val id = loadedFor ?: return
        try {
            Files.createDirectories(dir)
            val root = CompoundTag()
            for ((idx, inv) in pages) root.putString("${idx}_inv", inv.encode())
            root.putString("known", known.joinToString(","))
            val tmp = dir.resolve("$id.nbt.tmp")
            NbtIo.writeCompressed(root, tmp)
            Files.move(tmp, dir.resolve("$id.nbt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (ignored: IOException) {}
    }
}
