package fishmod.features.inventory

import fishmod.compat.curScreen
import fishmod.features.extras.*

import fishmod.features.diana.DianaTracker
import fishmod.pv.data.MissingAccessories
import fishmod.pv.price.HypixelItemsCache
import fishmod.pv.price.ItemPricer
import fishmod.utils.FishMsg
import fishmod.utils.FishUtil
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// After browsing the Accessory Bag: chat list of the cheapest missing accessories per Magical Power
object AccessoryHelper {
    // from SkyHanni (AccessoryBagApi inventory name)
    private val TITLE = Regex("""^Accessory Bag(?: \(\d+/\d+\))?$""")
    private val owned = HashSet<String>()
    private var inBag = false
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.accessoryHelper || ++tick % 5 != 0) return@register
            try {
                val screen = mc.curScreen as? AbstractContainerScreen<*>
                if (screen != null && TITLE.matches(screen.title.string.trim())) {
                    if (!inBag) { owned.clear(); if (!ItemPricer.isReady()) ItemPricer.start(); HypixelItemsCache.requestIfEmpty() }
                    inBag = true
                    val bagSlots = screen.menu.slots.size - 36
                    for (i in 0 until bagSlots) ItemTools.getId(screen.menu.slots[i].item)?.let { owned += it }
                    mc.player?.inventory?.let { inv -> for (i in 0 until inv.containerSize) ItemTools.getId(inv.getItem(i))?.let { owned += it } }
                } else if (inBag && screen == null) {
                    inBag = false
                    report()
                } else if (screen == null) inBag = false
            } catch (t: Throwable) {
                FishDiag.fail("AccessoryHelper.1", "accessory helper failed", t)
            }
        }
    }

    private fun report() {
        if (owned.isEmpty()) return
        val res = MissingAccessories.compute(owned)
        val list = res.entries.filter { it.priced() && it.mpGain > 0 }.sortedBy { it.coinsPerMp() }.take(S.accessoryHelperCount)
        if (list.isEmpty()) { FishUtil.addChatMessage(Component.literal(FishMsg.prefix() + "§7Accessory prices still loading, open the bag again.")); return }
        FishUtil.addChatMessage(Component.literal(FishMsg.prefix() + "§dCheapest Magical Power upgrades:"))
        for (e in list) {
            val sold = e.soldName?.let { " §8(replaces $it)" } ?: ""
            FishUtil.addChatMessage(Component.literal("§7- §f" + e.name + " §6" + DianaTracker.short(e.netCost()) + " §7+§d" + e.mpGain + " MP §8(" + DianaTracker.short(e.coinsPerMp()) + "/MP)" + sold))
        }
    }
}
