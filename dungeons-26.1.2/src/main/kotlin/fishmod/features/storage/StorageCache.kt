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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.TreeMap

/**
 * Captures each SkyBlock storage page's contents while you page through `/storage` and persists
 * them per-account, so [StorageViewerScreen] can show every page at once without re-opening each.
 * Read-only — this never writes to your storage.
 */
object StorageCache {

    private val dir: Path = Paths.get(FolderUtility.CONFIG_PATH + "storage")

    @Volatile
    private var pages: TreeMap<Int, NBTInventory> = TreeMap()
    private var loadedFor: String? = null
    private var dirty = false
    private var lastSnapshot = 0L

    @JvmStatic
    fun view(): Map<Int, NBTInventory> = Collections.unmodifiableMap(pages)

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
    }

    private fun uuid(): String? = Minecraft.getInstance().player?.gameProfile?.id?.toString()

    private fun tick(mc: Minecraft) {
        if (!FishSettings.storageOverlayEnabled) return
        val id = uuid() ?: return
        if (id != loadedFor) { load(id); loadedFor = id }

        val screen = mc.screen as? AbstractContainerScreen<*> ?: run { flush(); return }
        val page = StoragePage.fromTitle(screen.title.string.replace(Regex("§."), "")) ?: run { flush(); return }

        val now = System.currentTimeMillis()
        if (now - lastSnapshot < 400) return
        lastSnapshot = now

        val menu = screen.menu
        val rows = (menu as? ChestMenu)?.rowCount ?: ((menu.slots.size - 36) / 9)
        if (rows <= 1) return
        val items = menu.slots.subList(9, rows * 9).map { it.item.copy() }
        if (items.all { it.isEmpty }) return
        pages[page.index] = NBTInventory(items)
        dirty = true
    }

    private fun flush() {
        if (dirty) { save(); dirty = false }
    }

    private fun load(id: String) {
        pages = TreeMap()
        val file = dir.resolve("$id.nbt")
        if (!Files.exists(file)) return
        try {
            val root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
            for (i in 0 until 27) {
                val key = "${i}_inv"
                if (!root.contains(key)) continue
                NBTInventory.decode(root.getString(key).orElse(""))?.let { pages[i] = it }
            }
        } catch (ignored: IOException) {}
    }

    private fun save() {
        val id = loadedFor ?: return
        try {
            Files.createDirectories(dir)
            val root = CompoundTag()
            for ((idx, inv) in pages) root.putString("${idx}_inv", inv.encode())
            val tmp = dir.resolve("$id.nbt.tmp")
            NbtIo.writeCompressed(root, tmp)
            Files.move(tmp, dir.resolve("$id.nbt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (ignored: IOException) {}
    }
}
