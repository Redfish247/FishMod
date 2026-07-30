package fishmod.features.croesus

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import java.util.regex.Pattern

/** Passively auto-populates [LootTrackerStore] from real Croesus chest claims, no typing required. */
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
            ScreenEvents.afterRender(screen).register(ScreenEvents.AfterRender { _, _, _, _, _ -> scanRunGuiPreviews() })
            return
        }

        val m = CHEST_SCREEN_PATTERN.matcher(title)
        if (m.matches()) logPending(m.group(1))
    }

    private fun scanRunGuiPreviews() {
        if (!FishSettings.lootTrackerEnabled) return
        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return
        val menu = player.currentScreenHandler
        if (menu === player.playerScreenHandler) return

        val slotCount = minOf(27, menu.slots.size)
        for (i in 0 until slotCount) {
            val stack = getSlot(menu, i)
            if (stack.isEmpty) continue
            val name = strip(stack.name.string)
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

    private fun onTick(mc: MinecraftClient) {
        val title = if (mc.currentScreen == null) "" else strip(mc.currentScreen!!.title.string)
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

    private fun getSlot(menu: ScreenHandler, index: Int): ItemStack =
        if (index >= 0 && index < menu.slots.size) menu.slots[index].stack else ItemStack.EMPTY

    private fun getTooltip(stack: ItemStack): MutableList<String> {
        val tooltip = ArrayList<String>()
        tooltip.add(stack.name.string)
        val lore = stack.get(DataComponentTypes.LORE)
        if (lore != null) {
            for (c in lore.lines()) tooltip.add(c.string)
        }
        return tooltip
    }
}
