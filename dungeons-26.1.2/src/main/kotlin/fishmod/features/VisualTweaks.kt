package fishmod.features

import fishmod.utils.config.values.Visual
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.level.block.Blocks

/**
 * Render-Optimizer client tweaks (gated behind [Visual.renderOptimizer]):
 *  - stopShovelFlattening: cancel the shovel "make path" interaction on dirt-likes
 * (Hide Nearby Players / Hide Dead Entities live in EntityRendererMixin;
 *  No Swing Animation is handled in ItemInHandRendererMixin.)
 */
object VisualTweaks {

    private val FLATTENABLE = setOf(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT,
        Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.DIRT_PATH,
    )

    @JvmStatic
    fun init() {
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
