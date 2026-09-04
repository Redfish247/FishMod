package fishmod.features.croesus

import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import java.util.regex.Pattern

/**
 * Passively populates [LootTrackerStore] from Croesus chest claims via tooltip-reading.
 * All 6 tiers are previewed in the run-selection GUI before the player picks one, so previews
 * are cached by chest name and only logged once the chosen tier's confirmation screen opens
 * (matched by title alone) — otherwise unclaimed tiers would be counted as loot.
 */
object CroesusLootDetector {
    private val CHEST_SCREEN_PATTERN: Pattern = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?$")
    private val CHEST_ITEM_PATTERN: Pattern = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)$")
    private val RUN_GUI_PATTERN: Pattern = Pattern.compile("^(?:Master )?Catacombs - .+$")

    /** Chest-tier name (e.g. "Wood") -> its parsed preview, cached while the run-selection GUI is open. */
    private val pendingChests = HashMap<String, CroesusRewardParser.ChestInfo>()

    private var runGuiOpenPrev = false
    private var loggedThisVisit = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> onTick(mc) })
    }

    @JvmStatic
    fun onScreenInit(screen: Screen?) {
        if (screen == null || !FishSettings.lootTrackerEnabled) return
        val title = strip(screen.title.string)

        if (RUN_GUI_PATTERN.matcher(title).matches()) {
            // Hypixel sends slot contents in a follow-up packet, so retry each frame until cached.
            ScreenEvents.afterExtract(screen).register { _, _, _, _, _ -> scanRunGuiPreviews() }
            return
        }

        val m = CHEST_SCREEN_PATTERN.matcher(title)
        if (m.matches()) logPending(m.group(1))
    }

    private fun scanRunGuiPreviews() {
        if (!FishSettings.lootTrackerEnabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val menu = player.containerMenu
        if (menu === player.inventoryMenu) return

        val slotCount = minOf(27, menu.slots.size)
        for (i in 0 until slotCount) {
            val stack = getSlot(menu, i)
            if (stack.isEmpty) continue
            val name = strip(stack.hoverName.string)
            if (!CHEST_ITEM_PATTERN.matcher(name).matches()) continue
            if (pendingChests.containsKey(name)) continue

            val info = CroesusRewardParser.parseRewards(getTooltip(stack), arrayOf<String?>(null))
            if (info != null) pendingChests[name] = info
        }
    }

    private fun logPending(chestName: String) {
        val info = pendingChests.remove(chestName) ?: return

        for (ri in info.items) {
            LootTrackerStore.addOrIncrement(ri.displayName, ri.id, ri.qty)
        }
        if (!loggedThisVisit) {
            loggedThisVisit = true
            LootTrackerStore.setRuns(LootTrackerStore.runs() + 1)
        }
    }

    private fun onTick(mc: Minecraft) {
        val title = if (mc.screen == null) "" else strip(mc.screen!!.title.string)
        val runGuiOpenNow = RUN_GUI_PATTERN.matcher(title).matches()
        if (runGuiOpenNow && !runGuiOpenPrev) {
            pendingChests.clear()
            loggedThisVisit = false
        }
        runGuiOpenPrev = runGuiOpenNow
    }

    private fun strip(s: String): String = HypixelApi.STRIP_COLOR.matcher(s).replaceAll("")

    private fun getSlot(menu: AbstractContainerMenu, index: Int): ItemStack =
        if (index >= 0 && index < menu.slots.size) menu.slots[index].item else ItemStack.EMPTY

    private fun getTooltip(stack: ItemStack): MutableList<String> {
        val tooltip = ArrayList<String>()
        tooltip.add(stack.hoverName.string)
        val lore = stack.get(DataComponents.LORE)
        if (lore != null) {
            for (c in lore.lines()) tooltip.add(c.string)
        }
        return tooltip
    }
}
