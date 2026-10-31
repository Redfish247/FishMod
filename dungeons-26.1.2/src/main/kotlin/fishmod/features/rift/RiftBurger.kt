package fishmod.features.rift

import fishmod.compat.curScreen
import fishmod.utils.Misc
import fishmod.utils.config.FishConfig
import fishmod.utils.data.ItemUtil
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import fishmod.features.rift.RiftSettings as S

// Warns when the Motes Grubber sell menu opens with Highlite in the inventory and fewer than 5 McGrubber stacks
object RiftBurger {

    // SkyHanni ShowMotesNpcSellPrice.burgerPattern: chat "You have N Grubber Stacks"
    private val STACKS_CHAT = Regex("You have (\\d) Grubber Stacks")
    // Skyblocker McGrubberUpdater.ORB_PICKUP: orb gives 5 motes + 60 per stack
    private val ORB = Regex("ORB! Picked up \\+(\\d+) Motes, recovered \\+2ф Rift Time!")
    // Skyblocker McGrubberUpdater.MCGRUBBER_PROGRESS: /sblevels item lore, 20% per stack
    private val PROGRESS = Regex("Total Progress: (\\d+)%")

    private const val GRUBBER_TITLE = "Motes Grubber"
    private const val CONSUMABLES_TITLE = "Miscellaneous ➜ Consumable Items"

    private var warnedThisOpen = false
    private var scannedThisOpen = false

    private fun setStacks(n: Int) {
        if (n !in 0..5 || n == S.riftGrubberStacks) return
        S.riftGrubberStacks = n
        try { FishConfig.manager.save() } catch (t: Throwable) { FishDiag.fail("RiftBurger.2", "config save failed", t) }
    }

    private fun hasHighlite(mc: net.minecraft.client.Minecraft): Boolean {
        val inv = mc.player?.inventory ?: return false
        for (i in 0 until inv.containerSize) if (ItemUtil.getId(inv.getItem(i)) == "HIGHLITE") return true
        return false
    }

    private fun tick(mc: net.minecraft.client.Minecraft) {
        val screen = mc.curScreen as? ContainerScreen
        val title = screen?.title?.string
        if (title == null) { warnedThisOpen = false; scannedThisOpen = false; return }
        if (title == CONSUMABLES_TITLE && !scannedThisOpen) {
            val menu = screen.menu
            for (slot in menu.slots) {
                val item = slot.item
                if (ItemUtil.getId(item) != "MCGRUBBER_BURGER") continue
                val lore = item.get(DataComponents.LORE)?.lines() ?: continue
                val pct = lore.firstNotNullOfOrNull { PROGRESS.find(Rift.strip(it.string)) }?.groupValues?.get(1)?.toIntOrNull() ?: continue
                setStacks(pct / 20); scannedThisOpen = true; break
            }
            return
        }
        if (!S.riftBurgerWarn || title != GRUBBER_TITLE || warnedThisOpen || !Rift.inRift()) return
        if (!hasHighlite(mc)) return
        warnedThisOpen = true
        val n = S.riftGrubberStacks
        if (n >= 5) return
        val known = if (n < 0) "unknown (eat/check a burger once)" else "$n/5"
        Misc.addChatMessage(Component.literal("§c[Rift] You are selling Highlite with McGrubber burgers at $known!"))
        if (S.riftBurgerTitle) Misc.forceTitle(Component.literal("§cBurgers $known"), Component.literal("§7Highlite sells for more with 5/5"), 2500)
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            try { tick(mc) } catch (t: Throwable) { FishDiag.fail("RiftBurger.1", "burger tick failed", t) }
        }
        ClientReceiveMessageEvents.ALLOW_GAME.register { msg, overlay ->
            if (overlay || !Rift.inRift()) return@register true
            val plain = Rift.strip(msg.string)
            STACKS_CHAT.find(plain)?.let { setStacks(it.groupValues[1].toInt()) }
            ORB.find(plain)?.let { setStacks((it.groupValues[1].toInt() - 5) / 60) }
            true
        }
    }
}
