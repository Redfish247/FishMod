package fishmod.features.dungeon.map

import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity

object Mimic {

    @JvmStatic
    fun register() {
        ClientTickEvents.END_LEVEL_TICK.register(ClientTickEvents.EndLevelTick { level ->
            try {
                if (!DungeonMapSettings.mapEnabled) return@EndLevelTick
                if (!DungeonState.isInDungeon()) return@EndLevelTick

                val floor = DungeonState.floorNumber()
                if (floor != 6 && floor != 7) return@EndLevelTick
                if (DungeonScore.mimicKilled) return@EndLevelTick
                if (Scan.chest != null) return@EndLevelTick

                val mc = Minecraft.getInstance()
                if (mc.level == null) return@EndLevelTick

                for (room in ArrayList(Scan.rooms)) {
                    val data = room.data
                    val rot = room.rotation != Room.Rotation.NONE
                    val chestPositions: List<BlockPos>? =
                        if (data != null && data.secretDetails != null)
                            @Suppress("UNCHECKED_CAST")
                            (data.secretDetails!!["chest"] as? List<BlockPos>)
                        else null

                    if (rot && chestPositions != null) {
                        for (local in chestPositions) {
                            val world = room.offset(local) ?: continue
                            val be: BlockEntity? = mc.level!!.getBlockEntity(world)
                            if (be is TrappedChestBlockEntity) {
                                room.setMimic(true)
                                Scan.chest = world
                                return@EndLevelTick
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
            }
        })
    }
}
