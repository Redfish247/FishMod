package fishmod.features.other

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import fishmod.utils.Keybinds
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.io.File
import java.util.function.Predicate

// Binds keys to specific pets (by UUID), so it works no matter which page/slot the pet sits in.
object PetKeybinds {

    const val COUNT = 9
    private const val PLAYER_INV_SLOTS = 36
    private const val FILE_PATH = "config/fishmod-pet-keybinds.json"
    private const val TIMEOUT_MS = 4000L

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val LEVEL_PREFIX = Regex("^\\[Lvl\\s*\\d+]\\s*")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    data class Entry(var uuid: String? = null, var name: String? = null)

    private var entries: MutableList<Entry> = MutableList(COUNT) { Entry() }

    private var target: Entry? = null
    private var targetStartedAt = 0L
    private var waitTicks = 0
    private var openedByUs = false

    init { load() }

    @JvmStatic fun assignedName(i: Int): String = entries.getOrNull(i)?.name ?: "Unassigned"

    @JvmStatic fun clear(i: Int) {
        if (i !in 0 until COUNT) return
        entries[i] = Entry()
        save()
    }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
    }

    private fun tick(mc: Minecraft) {
        val binds = Keybinds.petKeybinds ?: return
        if (FishSettings.petKeybindsEnabled && mc.screen == null) {
            for (i in binds.indices) {
                while (binds[i].consumeClick()) startTarget(i, openMenu = true)
            }
        }

        val t = target ?: return
        if (System.currentTimeMillis() - targetStartedAt > TIMEOUT_MS) {
            msg("§cCouldn't find §f${t.name}§c in your pets menu.")
            target = null
            return
        }
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if (!isPetsMenu(screen)) return
        if (waitTicks > 0) { waitTicks--; return }
        step(mc, screen, t)
    }

    private fun startTarget(i: Int, openMenu: Boolean) {
        val e = entries.getOrNull(i)
        if (e?.uuid == null && e?.name == null) {
            msg("§cPet Keybind ${i + 1} has no pet. Open /pets, hover a pet and press Shift + the key to assign it.")
            return
        }
        target = e
        targetStartedAt = System.currentTimeMillis()
        waitTicks = if (openMenu) 3 else 0
        openedByUs = openMenu
        if (openMenu) Misc.executeCommand("pets")
    }

    private fun step(mc: Minecraft, screen: AbstractContainerScreen<*>, t: Entry) {
        val menu = screen.menu
        val size = menu.slots.size - PLAYER_INV_SLOTS
        if (size <= 0) return
        // Wait for the server to fill the menu.
        if ((0 until size).none { !menu.slots[it].item.isEmpty }) return

        val slot = (0 until size).map { menu.slots[it] }.firstOrNull { matches(it.item, t) }
        if (slot != null) {
            target = null
            if (ItemUtil.containsLore(slot.item, "Click to despawn") && FishSettings.petKeybindsNoDespawn) {
                msg("§e${t.name}§7 is already summoned.")
                if (openedByUs && FishSettings.petKeybindsAutoClose) screen.onClose()
                return
            }
            click(mc, menu.containerId, slot.index)
            if (FishSettings.petKeybindsAutoClose && mc.screen === screen) screen.onClose()
            return
        }

        val next = (0 until size).map { menu.slots[it] }.firstOrNull {
            !it.item.isEmpty && it.item.hoverName.string.replace(COLOR, "").trim().equals("Next Page", true)
        }
        if (next == null) {
            msg("§cCouldn't find §f${t.name}§c in your pets menu.")
            target = null
            return
        }
        click(mc, menu.containerId, next.index)
        waitTicks = 4
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent, screen: AbstractContainerScreen<*>): Boolean =
        handleInput(screen) { it.matches(input) }

    @JvmStatic
    fun mouseClicked(click: MouseButtonEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (click.button() == 0 || click.button() == 1) return false
        return handleInput(screen) { it.matchesMouse(click) }
    }

    private fun handleInput(screen: AbstractContainerScreen<*>, matches: Predicate<KeyMapping>): Boolean {
        if (!FishSettings.petKeybindsEnabled || !isPetsMenu(screen)) return false
        val binds = Keybinds.petKeybinds ?: return false
        val i = binds.indexOfFirst { !it.isUnbound && matches.test(it) }
        if (i < 0) return false

        if (Minecraft.getInstance().hasShiftDown()) {
            val hovered = (screen as fishmod.mixin.accessors.HandledScreenAccessor).`fishmod$getHoveredSlot`()
            assign(i, hovered)
            return true
        }
        startTarget(i, openMenu = false)
        return true
    }

    private fun assign(i: Int, slot: Slot?) {
        val stack = slot?.item
        val name = stack?.let { petName(it) }
        if (stack == null || stack.isEmpty || name == null) {
            msg("§cHover a pet to assign it to Pet Keybind ${i + 1}.")
            return
        }
        entries[i] = Entry(petUuid(stack), name)
        save()
        msg("§aPet Keybind ${i + 1} → §f$name")
    }

    private fun matches(stack: ItemStack, t: Entry): Boolean {
        if (stack.isEmpty) return false
        val uuid = petUuid(stack)
        if (t.uuid != null && uuid != null) return uuid == t.uuid
        return t.name != null && petName(stack) == t.name
    }

    private fun petName(stack: ItemStack): String? {
        val raw = stack.hoverName.string.replace(COLOR, "").trim()
        if (!LEVEL_PREFIX.containsMatchIn(raw)) return null
        return raw.replace(LEVEL_PREFIX, "").trim()
    }

    private fun petUuid(stack: ItemStack): String? {
        ItemUtil.getUuid(stack)?.takeIf { it.isNotEmpty() }?.let { return it }
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val info = tag.getStringOr("petInfo", "")
        if (info.isEmpty()) return null
        return try {
            val obj = JsonParser.parseString(info).asJsonObject
            if (obj.has("uuid")) obj.get("uuid").asString else null
        } catch (e: Exception) { null }
    }

    private fun isPetsMenu(screen: AbstractContainerScreen<*>): Boolean =
        screen.title.string.replace(COLOR, "").trim().startsWith("Pets")

    private fun click(mc: Minecraft, containerId: Int, slotId: Int) {
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, player)
    }

    private fun msg(s: String) = Misc.addChatMessage(Component.literal(s))

    private fun load() {
        val file = File(FILE_PATH)
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableList<Entry>>() {}.type
            val loaded: MutableList<Entry>? = file.reader().use { GSON.fromJson(it, type) }
            if (loaded != null) for (i in 0 until minOf(COUNT, loaded.size)) entries[i] = loaded[i]
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[PetKeybinds] load failed: {}", e.toString())
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            file.writer().use { GSON.toJson(entries, it) }
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[PetKeybinds] save failed: {}", e.toString())
        }
    }
}
