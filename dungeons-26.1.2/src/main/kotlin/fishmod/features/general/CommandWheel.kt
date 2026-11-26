package fishmod.features.general

import com.mojang.blaze3d.platform.InputConstants
import fishmod.compat.GLFW
import fishmod.compat.curScreen
import fishmod.compat.showScreen
import fishmod.utils.config.FishPaths
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object CommandWheel {

    class Slot(var label: String, var item: String, var command: String, var color: Int)

    const val MAX_SLOTS = 12
    const val DEFAULT_COLOR = 0xFF4DA3FF.toInt()

    private val FILE: Path = Paths.get(FishPaths.CONFIG_PATH + "command_wheel.txt")
    private val slots: MutableList<Slot> = ArrayList()
    private var key: InputConstants.Key = InputConstants.UNKNOWN
    private var loaded = false
    private var wasDown = false
    private val stacks = HashMap<String, ItemStack>()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try {
                tick(mc)
            } catch (e: Exception) {
                FishDiag.fail("CommandWheel.4", "command wheel tick failed", e)
            }
        })
    }

    @JvmStatic
    fun slots(): List<Slot> {
        ensureLoaded()
        return slots
    }

    @JvmStatic
    fun key(): InputConstants.Key {
        ensureLoaded()
        return key
    }

    @JvmStatic
    fun replaceAll(newKey: InputConstants.Key, newSlots: List<Slot>) {
        ensureLoaded()
        key = newKey
        slots.clear()
        slots.addAll(newSlots.take(MAX_SLOTS))
        save()
    }

    fun isDown(mc: Minecraft, k: InputConstants.Key = key()): Boolean {
        if (k == InputConstants.UNKNOWN) return false
        return if (k.type == InputConstants.Type.MOUSE)
            GLFW.glfwGetMouseButton(mc.window.handle(), k.value) == GLFW.GLFW_PRESS
        else
            GLFW.keyDown(mc.window, k.value)
    }

    fun stackFor(id: String): ItemStack? {
        if (id.isBlank()) return null
        stacks[id]?.let { return it.takeIf { s -> !s.isEmpty } }
        val ident = Identifier.tryParse(if (id.contains(':')) id.trim() else "minecraft:" + id.trim().lowercase())
        val item = ident?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
        val st = if (item != null) ItemStack(item) else ItemStack.EMPTY
        stacks[id] = st
        return st.takeIf { !it.isEmpty }
    }

    fun run(slot: Slot) {
        if (slot.command.isNotBlank()) fishmod.utils.FishUtil.executeCommand(slot.command)
    }

    private fun tick(mc: Minecraft) {
        ensureLoaded()
        val k = key
        if (k == InputConstants.UNKNOWN || slots.isEmpty()) { wasDown = false; return }
        if (mc.player == null) { wasDown = false; return }
        val down = isDown(mc, k)
        if (down && !wasDown && mc.curScreen == null) mc.showScreen(CommandWheelScreen())
        wasDown = down
    }

    private fun ensureLoaded() {
        if (!loaded) { load(); loaded = true }
    }

    private fun defaults() {
        slots.clear()
        slots.add(Slot("Hub", "minecraft:nether_star", "hub", 0xFF55FFFF.toInt()))
        slots.add(Slot("Island", "minecraft:grass_block", "is", 0xFF55FF55.toInt()))
        slots.add(Slot("Dungeon Hub", "minecraft:wither_skeleton_skull", "warp dh", 0xFFAA66FF.toInt()))
        slots.add(Slot("Pets", "minecraft:bone", "pets", 0xFFFFAA00.toInt()))
        slots.add(Slot("Wardrobe", "minecraft:leather_chestplate", "wd", 0xFFFF77AA.toInt()))
        slots.add(Slot("Storage", "minecraft:chest", "storage", 0xFFFFD84D.toInt()))
        slots.add(Slot("Equipment", "minecraft:diamond_chestplate", "equipment", 0xFF4DA3FF.toInt()))
        slots.add(Slot("Skyblock Menu", "minecraft:compass", "sbmenu", 0xFFFF5555.toInt()))
    }

    private fun load() {
        slots.clear()
        key = InputConstants.UNKNOWN
        if (!Files.exists(FILE)) { defaults(); return }
        try {
            for (line in Files.readAllLines(FILE)) {
                if (line.isBlank()) continue
                val p = line.split("\t", limit = 4)
                if (p[0] == "#key") {
                    if (p.size >= 2) key = InputConstants.getKey(p[1].trim())
                    continue
                }
                if (p.size < 4) continue
                val color = p[2].trim().removePrefix("#").toLongOrNull(16)?.toInt() ?: DEFAULT_COLOR
                slots.add(Slot(p[0], p[1].trim(), p[3].trim(), color or 0xFF000000.toInt()))
            }
        } catch (e: Exception) {
            FishDiag.fail("CommandWheel.1", "failed to read $FILE", e)
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            val sb = StringBuilder()
            sb.append("#key\t").append(key.name).append('\n')
            for (s in slots) {
                sb.append(s.label.replace('\t', ' ')).append('\t').append(s.item.trim()).append('\t')
                    .append(String.format("%06X", s.color and 0xFFFFFF)).append('\t').append(s.command.replace('\t', ' ')).append('\n')
            }
            Files.writeString(FILE, sb.toString())
        } catch (e: IOException) {
            FishDiag.fail("CommandWheel.2", "failed to write $FILE (${slots.size} slots)", e)
        }
    }
}
