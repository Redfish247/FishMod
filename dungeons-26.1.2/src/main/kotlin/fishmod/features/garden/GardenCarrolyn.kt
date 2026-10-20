package fishmod.features.garden

import fishmod.utils.Location
import fishmod.utils.Misc
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import fishmod.features.garden.GardenSettings as S

// Items that go to Carrolyn get a tooltip hint; right-clicking one offers a clickable warp to the Crimson Isle (SkyHanni's Help Carrolyn, no pathfinding)
object GardenCarrolyn {

    private fun isCarrolynItem(stack: ItemStack): Boolean =
        !stack.isEmpty && Garden.lore(stack).any { it.contains("of these to Carrolyn", true) }

    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (S.gardenCarrolyn && Location.inSkyblock() && isCarrolynItem(stack)) {
                lines += Component.literal("")
                lines += Component.literal("§eRight-click to warp to Carrolyn (Crimson Isle)")
            }
        })
        UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
            if (level.isClientSide && S.gardenCarrolyn && Location.inSkyblock() && isCarrolynItem(player.getItemInHand(hand))) {
                Misc.addChatMessage(
                    Component.literal("Carrolyn is on the Crimson Isle. Click here to warp there!").withStyle { st ->
                        st.withColor(ChatFormatting.LIGHT_PURPLE)
                            .withClickEvent(ClickEvent.RunCommand("/warp crimson"))
                            .withHoverEvent(HoverEvent.ShowText(Component.literal("§7/warp crimson")))
                    }
                )
            }
            InteractionResult.PASS
        })
    }
}
