package fishmod.features

import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemTools
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object HuntaxeLock {
    private var lastBlockedMs = 0L
    private var decidedAtMs = 0L
    private var decidedBlock = false

    private fun isHuntaxe(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (ItemTools.getId(stack)?.contains("HUNTAXE", ignoreCase = true) == true) return true
        return stack.hoverName.string.contains("huntaxe", ignoreCase = true)
    }

    private fun shouldBlock(player: Player, hand: InteractionHand): Boolean {
        if (!FishSettings.huntaxeLockEnabled || hand != InteractionHand.MAIN_HAND) return false
        if (!SkyblockLocation.inSkyblock() || !isHuntaxe(player.getItemInHand(hand))) return false
        val now = System.currentTimeMillis()
        if (now - decidedAtMs < 50) return decidedBlock
        decidedAtMs = now
        decidedBlock = now - lastBlockedMs > FishSettings.huntaxeLockWindowMs
        if (decidedBlock) lastBlockedMs = now else lastBlockedMs = 0L
        return decidedBlock
    }

    fun init() {
        UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
            if (level.isClientSide && shouldBlock(player, hand)) InteractionResult.FAIL else InteractionResult.PASS
        })
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, _ ->
            if (level.isClientSide && shouldBlock(player, hand)) InteractionResult.FAIL else InteractionResult.PASS
        })
    }
}
