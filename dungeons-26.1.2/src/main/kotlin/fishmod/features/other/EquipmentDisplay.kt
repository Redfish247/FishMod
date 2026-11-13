package fishmod.features.other

import fishmod.features.storage.NBTInventory
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.InvButtonSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object EquipmentDisplay {
    private val TYPES = listOf(listOf("NECKLACE"), listOf("CLOAK"), listOf("BELT"), listOf("GLOVES", "BRACELET"))
    private val cached = arrayOfNulls<ItemStack>(4)
    private val SWAP = Regex("^You equipped (.+)!(?: \\(\\d+\\))?$")
    private var currentLoadout: String? = null

    @JvmStatic
    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*> || screen is InventoryScreen) return@register
            if (!screen.title.string.contains("Equipment")) return@register
            ScreenEvents.afterTick(screen).register { s ->
                FishDiag.guard("EquipmentDisplay.1", "equipment scan failed") { scan(s as AbstractContainerScreen<*>) }
            }
        }
        FishEvents.chat.register { text ->
            val name = text?.string?.trim()?.let { SWAP.matchEntire(it) }?.groupValues?.get(1)
            if (name != null) Minecraft.getInstance().execute {
                FishDiag.guard("EquipmentDisplay.3", "loadout swap update failed for '$name'") { onSwap(name) }
            }
            false
        }
    }

    private fun storedSets(): JsonObject =
        runCatching { JsonParser.parseString(InvButtonSettings.equipmentByLoadout).asJsonObject }.getOrElse { JsonObject() }

    private fun onSwap(name: String) {
        currentLoadout = name
        val encoded = storedSets().get(name)?.asString ?: return
        val stacks = NBTInventory.decode(encoded)?.stacks ?: return
        ensureLoaded()
        for (i in 0..3) cached[i] = stacks.getOrNull(i) ?: ItemStack.EMPTY
        loaded = true
        InvButtonSettings.equipmentCache = encoded
        save()
    }

    private fun save() {
        runCatching { FishConfig.manager.save() }.onFailure { FishDiag.fail("EquipmentDisplay.2", "saving equipment cache failed", it) }
    }

    private fun typeOf(st: ItemStack): Int {
        val lines = st.get(DataComponents.LORE)?.lines() ?: return -1
        val last = lines.asReversed().map { it.string.replace(Regex("§."), "").trim() }.firstOrNull { it.isNotEmpty() } ?: return -1
        val words = last.split(' ')
        return TYPES.indexOfFirst { t -> words.any { it in t } }
    }

    private fun scan(s: AbstractContainerScreen<*>) {
        val found = arrayOfNulls<ItemStack>(4)
        val menuSize = s.menu.slots.size - 36
        for (i in 0 until menuSize.coerceAtLeast(0)) {
            val st = s.menu.slots[i].item
            if (st.isEmpty) continue
            val t = typeOf(st)
            if (t < 0) continue
            if (found[t] != null) return
            found[t] = st.copy()
        }
        if (found.none { it != null }) return
        val next = List(4) { found[it] ?: ItemStack.EMPTY }
        if (loaded && (0..3).all { ItemStack.matches(cached[it] ?: ItemStack.EMPTY, next[it]) }) return
        for (i in 0..3) cached[i] = next[i]
        loaded = true
        val encoded = NBTInventory(next).encode()
        InvButtonSettings.equipmentCache = encoded
        currentLoadout?.let { name ->
            InvButtonSettings.equipmentByLoadout = storedSets().apply { addProperty(name, encoded) }.toString()
        }
        save()
    }

    private var loaded = false

    private fun ensureLoaded() {
        if (loaded || InvButtonSettings.equipmentCache.isEmpty() || Minecraft.getInstance().level == null) return
        loaded = true
        NBTInventory.decode(InvButtonSettings.equipmentCache)?.stacks?.forEachIndexed { i, st -> if (i < 4) cached[i] = st }
    }

    private const val X = -19

    @JvmStatic
    fun click(mouseX: Double, mouseY: Double): Boolean {
        if (mouseX < X - 1 || mouseX >= X + 17 || mouseY < 7 || mouseY >= 8 + 72) return false
        fishmod.utils.FishUtil.executeCommand("stats")
        return true
    }

    private var tipStack: net.minecraft.world.item.ItemStack? = null
    private var tipX = 0
    private var tipY = 0

    @JvmStatic
    fun flushTooltip(ctx: GuiGraphicsExtractor) {
        val st = tipStack ?: return
        tipStack = null
        ctx.setTooltipForNextFrame(Minecraft.getInstance().font, st, tipX, tipY)
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, left: Int, top: Int) {
        tipStack = null
        ensureLoaded()
        val x = X
        for (i in 0..3) {
            val y = 8 + i * 18
            ctx.fill(x - 1, y - 1, x + 17, y + 17, 0x90000000.toInt())
            val st = cached[i]
            if (st == null) {
                ctx.text(Minecraft.getInstance().font, "?", x + 5, y + 4, 0xffaaaaaa.toInt(), true)
                continue
            }
            if (st.isEmpty) continue
            ctx.item(st, x, y)
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) { tipStack = st; tipX = mouseX + left; tipY = mouseY + top }
        }
        if (cached.all { it == null } && mouseX >= x && mouseX < x + 16 && mouseY >= 8 && mouseY < 8 + 72)
            ctx.text(Minecraft.getInstance().font, "Open /equipment once", x - 40, 8 + 74, 0xffaaaaaa.toInt(), true)
    }
}
