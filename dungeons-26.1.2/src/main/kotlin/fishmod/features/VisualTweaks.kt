package fishmod.features

import fishmod.utils.config.values.Visual
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.level.block.Blocks

/**
 * Small quality-of-life client tweaks ported from Odin / NoammAddons. The first three are gated
 * behind the [Visual.renderOptimizer] master switch (the "Render Optimizer" feature):
 *  - noSwingAnimation: zero the first-person hand-swing state each tick
 *  - stopShovelFlattening: cancel the shovel "make path" interaction on dirt-likes
 * [highlightProtectedItem] is independent (its own Visuals feature).
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

        DrawEvents.INVENTORY_SLOT_BEFORE.register(::drawProtected)
    }

    @JvmStatic
    fun drawProtected(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (!Visual.highlightProtectedItem || stack == null || stack.isEmpty) return
        if (!ItemUtil.containsIgnoreCaseLore(stack, "this item is protected")) return
        ctx.fill(x, y, x + 16, y + 16, 0x66FF3030.toInt())
    }
}
