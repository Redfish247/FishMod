package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.player.Player

// Glow outline (visible through walls) on nearby dungeon teammates in their class colour.
object PlayerHighlight {

    @JvmStatic
    fun outlineColor(player: Player): Int {
        if (!FishSettings.playerHighlightEnabled || !Location.inDungeon() || !player.isAlive) return EntityRenderState.NO_OUTLINE
        if (!DungeonClass.isTeammate(player)) return EntityRenderState.NO_OUTLINE
        val me = Minecraft.getInstance().player ?: return EntityRenderState.NO_OUTLINE
        val range = FishSettings.playerHighlightRange.toDouble()
        if (me.distanceToSqr(player) > range * range) return EntityRenderState.NO_OUTLINE
        val cls = DungeonClass.getClass(player) ?: return EntityRenderState.NO_OUTLINE
        return DungeonClass.getColor(cls) and 0xFFFFFF
    }
}
