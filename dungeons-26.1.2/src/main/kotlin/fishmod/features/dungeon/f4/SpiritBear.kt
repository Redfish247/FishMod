package fishmod.features.dungeon.f4

import fishmod.features.FishHudEditor
import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

/**
 * Spirit Bear state HUD. On F4/M4 (Thorn) the ring of blocks around the arena flips
 * COAL_BLOCK -> SEA_LANTERN per kill; when the last one flips the bear starts spawning (~68t).
 * We poll the ring each server tick rather than reconstructing block-update deltas.
 */
object SpiritBear {

    private const val NAME = "Spirit Bear"
    private val LAST = BlockPos(7, 77, 34)

    private var timer = -1   // -1 = not spawned, 0 = alive, >0 = spawning countdown (ticks)
    private var kills = 0
    private var lastWasLantern = false

    private fun mm() = DungeonState.isMasterMode()
    private fun maxKills() = if (mm()) 30 else 25
    private fun ring() = if (mm()) M4 else F4
    private fun active() =
        FishSettings.spiritBearEnabled && DungeonState.isInBoss() && DungeonState.floorNumber() == 4

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.spiritBearHudX }, { v -> FishSettings.spiritBearHudX = v },
            { FishSettings.spiritBearHudY }, { v -> FishSettings.spiritBearHudY = v },
            60, 12,
            { FishSettings.spiritBearScale }, { v -> FishSettings.spiritBearScale = v },
            { FishSettings.spiritBearEnabled },
        )

        Events.ON_SERVER_TICK.register {
            if (timer > 0) timer--
            if (!active()) return@register false
            val level = Minecraft.getInstance().level ?: return@register false

            var lanterns = 0
            for (p in ring()) if (level.getBlockState(p).block == Blocks.SEA_LANTERN) lanterns++
            kills = lanterns.coerceAtMost(maxKills())

            val lastLantern = level.getBlockState(LAST).block == Blocks.SEA_LANTERN
            if (lastLantern && !lastWasLantern) timer = 68
            else if (!lastLantern && lastWasLantern) timer = -1
            lastWasLantern = lastLantern
            false
        }

        Events.ON_WORLD_CHANGE.register {
            timer = -1; kills = 0; lastWasLantern = false; false
        }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!active()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val body = when {
            timer < 0 -> "§f$kills/${maxKills()}"
            timer > 0 -> "§e${"%.1f".format(timer / 20.0)}s"
            else -> "§aAlive!"
        }
        val sc = FishSettings.spiritBearScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.spiritBearHudX.toFloat(), FishSettings.spiritBearHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "§cBear: $body", 0, 0, -1, true)
        ctx.pose().popMatrix()
    }

    private val F4 = hashSetOf(
        BlockPos(-3, 77, 33), BlockPos(-9, 77, 31), BlockPos(-16, 77, 26), BlockPos(-20, 77, 20), BlockPos(-23, 77, 13),
        BlockPos(-24, 77, 6), BlockPos(-24, 77, 0), BlockPos(-22, 77, -7), BlockPos(-18, 77, -13), BlockPos(-12, 77, -19),
        BlockPos(-5, 77, -22), BlockPos(1, 77, -24), BlockPos(8, 77, -24), BlockPos(14, 77, -23), BlockPos(21, 77, -19),
        BlockPos(27, 77, -14), BlockPos(31, 77, -8), BlockPos(33, 77, -1), BlockPos(34, 77, 5), BlockPos(33, 77, 12),
        BlockPos(31, 77, 19), BlockPos(27, 77, 25), BlockPos(20, 77, 30), BlockPos(14, 77, 33), BlockPos(7, 77, 34),
    )
    private val M4 = hashSetOf(
        BlockPos(-2, 77, 33), BlockPos(-7, 77, 32), BlockPos(-13, 77, 28), BlockPos(-17, 77, 24), BlockPos(-21, 77, 18),
        BlockPos(-23, 77, 13), BlockPos(-24, 77, 7), BlockPos(-24, 77, 2), BlockPos(-23, 77, -4), BlockPos(-21, 77, -9),
        BlockPos(-17, 77, -14), BlockPos(-12, 77, -19), BlockPos(-6, 77, -22), BlockPos(-1, 77, -23), BlockPos(5, 77, -24),
        BlockPos(10, 77, -24), BlockPos(16, 77, -22), BlockPos(21, 77, -19), BlockPos(27, 77, -15), BlockPos(30, 77, -10),
        BlockPos(32, 77, -5), BlockPos(34, 77, 1), BlockPos(34, 77, 7), BlockPos(33, 77, 12), BlockPos(31, 77, 18),
        BlockPos(28, 77, 23), BlockPos(23, 77, 28), BlockPos(18, 77, 31), BlockPos(12, 77, 33), BlockPos(7, 77, 34),
    )
}
