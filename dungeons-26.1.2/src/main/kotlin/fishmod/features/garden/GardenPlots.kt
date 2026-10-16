package fishmod.features.garden

import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import fishmod.features.garden.GardenExtraSettings as X

// Plot number icons + prices in the plot menu, and a /gp <plot> shortcut the player types (nothing is sent automatically)
object GardenPlots {

    private val PLOT = Regex("""^Plot\s*-?\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val PRICE = Regex("""([\d,]+)\s+(Copper|Coins|Gems|Garden Experience)""", RegexOption.IGNORE_CASE)

    private fun menu() = GardenExtras.inMenu("Plot", "Desk")

    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!X.gardenPlotIcons || stack.isEmpty || !menu()) return@register
            val n = PLOT.find(GardenExtras.name(stack))?.groupValues?.get(1) ?: return@register
            ctx.text(Minecraft.getInstance().font, n, x + 1, y + 1, X.gardenPlotIconColor, true)
        }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!X.gardenPlotPrices || stack.isEmpty || !menu() || PLOT.find(GardenExtras.name(stack)) == null) return@ItemTooltipCallback
            val prices = Garden.lore(stack).mapNotNull { PRICE.find(it) }
            if (prices.isEmpty()) return@ItemTooltipCallback
            lines += Component.literal("")
            lines += Component.literal("§6§lFishMod Plot Price")
            for (p in prices) lines += Component.literal("§7${p.groupValues[2]}: §f${GardenExtras.floorShort(GardenExtras.num(p.groupValues[1]))}")
        })
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("gp").then(ClientCommands.argument("plot", StringArgumentType.word()).executes { ctx ->
                if (!X.gardenPlotCommands) { fishmod.utils.FishMsg.send("§7Enable Plot Shortcuts in the Farming tab."); return@executes 1 }
                Minecraft.getInstance().player?.connection?.sendCommand("plottp " + StringArgumentType.getString(ctx, "plot"))
                1
            }))
        }
    }
}
