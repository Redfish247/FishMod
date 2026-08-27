package fishmod.features

import fishmod.utils.config.values.Visual
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.level.block.Blocks

/**
 * Small client tweaks gated behind the [Visual.renderOptimizer] master switch (the "Render
 * Optimizer" feature):
 *  - noSwingAnimation: zero the first-person hand-swing state each tick
 *  - stopShovelFlattening: cancel the shovel "make path" interaction on dirt-likes
 * (Hide Nearby Players / Hide Dead Entities live in EntityRendererMixin.)
 */
object VisualTweaks {

    private val FLATTENABLE = setOf(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT,
        Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.DIRT_PATH,
    )

    @JvmStatic
    fun init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val p = mc.player ?: return@register
            if (!Visual.renderOptimizer || !Visual.noSwingAnimation) return@register
            if (Visual.noSwingTerminatorOnly && ItemUtil.getId(p.mainHandItem) != "TERMINATOR") return@register
            p.swinging = false
            p.swingTime = 0
            p.attackAnim = 0f
            p.oAttackAnim = 0f
        }

        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, hit ->
            if (Visual.renderOptimizer && Visual.stopShovelFlattening
                && player === Minecraft.getInstance().player
                && player.getItemInHand(hand).item is ShovelItem
                && level.getBlockState(hit.blockPos).block in FLATTENABLE
            ) {
                return@UseBlockCallback InteractionResult.FAIL
            }
            InteractionResult.PASS
        })
    }
}
