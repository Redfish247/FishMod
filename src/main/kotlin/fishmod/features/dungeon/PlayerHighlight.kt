package fishmod.features.dungeon

import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonRole
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.player.Player
import fishmod.utils.debug.FishDiag

object PlayerHighlight {

    @JvmStatic
    fun outlineColor(player: Player): Int {
        if (!FishSettings.playerHighlightEnabled || !SkyblockLocation.inDungeon() || !player.isAlive) return EntityRenderState.NO_OUTLINE
        if (!DungeonRole.isTeammate(player)) return EntityRenderState.NO_OUTLINE
        val me = Minecraft.getInstance().player ?: return EntityRenderState.NO_OUTLINE
        val range = FishSettings.playerHighlightRange.toDouble()
        if (me.distanceToSqr(player) > range * range) return EntityRenderState.NO_OUTLINE
        val cls = DungeonRole.getClass(player) ?: return EntityRenderState.NO_OUTLINE
        return try { DungeonRole.getColor(cls) and 0xFFFFFF } catch (e: Exception) { FishDiag.fail("PlayerHighlight.1", "class colour lookup failed for $cls", e); EntityRenderState.NO_OUTLINE }
    }
}
