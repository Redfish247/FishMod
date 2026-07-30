package fishmod.features.croesus

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
 * Passively auto-populates [LootTrackerStore] from real Croesus chest claims — no typing
 * required, and it works the same whether the chest was opened by hand or by another mod automating
 * the clicks (this is pure passive tooltip-reading triggered by screens opening).
 *
 * Two-phase, mirroring how `fishmodaddons.croesus.CroesusClaimer` actually reads this UI:
 * 1. Hypixel's run-selection GUI (title matching [RUN_GUI_PATTERN], e.g.
 *    "Catacombs - Floor 7") shows up to 6 chest-tier icons (items literally named
 *    "Wood"/"Gold"/.../"Bedrock"), each with a lore tooltip listing every reward followed by a
 *    "Cost" line — a *preview* of what each tier currently contains. All 6 are visible at
 *    once, before the player has chosen one, so these get cached by chest-type name rather than
 *    logged immediately (logging all 6 would count loot the player never actually claimed).
 * 2. Once the player clicks a tier, a confirmation screen opens whose *title* alone
 *    (color-stripped) is that tier's name (matching [CHEST_SCREEN_PATTERN]) — that title
 *    is enough to know which cached preview to log and clear; its own contents aren't read.
 *
 * Also auto-increments [LootTrackerStore.runs], once per run-selection GUI visit (not once
 * per chest — a Chest Key can open a second chest on the same run): armed fresh every time that GUI
 * transitions from closed to open, and the actual bump happens on the first reward logged during
 * that visit (rather than on the GUI closing, which fires before the chosen chest's screen opens).
 */
object CroesusLootDetector {
    private val COLOR_STRIP: Pattern = Pattern.compile("§.")
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

    /** Call once from `ScreenEvents.AFTER_INIT` for every newly opened container screen. */
    @JvmStatic
    fun onScreenInit(screen: Screen?) {
        if (screen == null || !FishSettings.lootTrackerEnabled) return
        val title = strip(screen.title.string)

        if (RUN_GUI_PATTERN.matcher(title).matches()) {
            // Contents may not have arrived the instant the screen is constructed (Hypixel sends
            // them in a follow-up packet) — retry every render frame; scanRunGuiPreviews() itself
            // skips chest types already cached, so this is cheap and self-dedupes.
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
            if (pendingChests.containsKey(name)) continue // already cached this visit

            val info = CroesusRewardParser.parseRewards(getTooltip(stack), arrayOf<String?>(null))
            if (info != null) pendingChests[name] = info
        }
    }

    private fun logPending(chestName: String) {
        val info = pendingChests.remove(chestName) ?: return // no cached preview for this tier (e.g. detection hadn't caught up) — nothing to log

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
            // Fresh visit to the run-selection GUI — stale previews from a previous visit (or a
            // previous floor) must not leak into this one, and the once-per-visit runs++ rearms.
            pendingChests.clear()
            loggedThisVisit = false
        }
        runGuiOpenPrev = runGuiOpenNow
    }

    private fun strip(s: String): String = COLOR_STRIP.matcher(s).replaceAll("")

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
