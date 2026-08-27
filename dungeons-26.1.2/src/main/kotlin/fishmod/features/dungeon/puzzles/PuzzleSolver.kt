package fishmod.features.dungeon.puzzles

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import net.minecraft.client.Minecraft

/**
 * One dungeon puzzle's solve logic. Lifecycle is driven entirely by [PuzzleSolvers]:
 * [onEnter] fires when the player walks into a room whose `RoomData.name` == [roomName],
 * [onTick]/[onChat]/[renderWorld] run only while they're inside it, [onExit] when they leave,
 * and [reset] on a world/run change.
 *
 * `RoomData.name` values for the puzzle rooms (from the dungeon-secrets data set):
 * "Water Board", "Higher Or Lower", "Creeper Beams", "Tic Tac Toe", "Boulder", "Ice Fill",
 * "Teleport Maze", "Three Weirdos", "Bomb Defuse".
 */
interface PuzzleSolver {

    /** Exact `RoomData.name` this solver handles. */
    val roomName: String

    fun onEnter(room: Room) {}

    fun onExit() {}

    fun onTick(mc: Minecraft) {}

    /** @param message already colour-stripped. @return true to swallow the chat line. */
    fun onChat(message: String): Boolean = false

    /** A block the player right-clicked while in this room. */
    fun onBlockClick(pos: net.minecraft.core.BlockPos) {}

    /** World-space geometry on the depth-tested filled-block layer (see [fishmod.utils.rendering.RenderingEvents.FILLED_BLOCK]). */
    fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {}

    /** World-space text/lines. [matrices] is already camera-translated (world coords). */
    fun renderWorldText(
        ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext,
        matrices: PoseStack,
    ) {}

    /** Clear all per-run state. */
    fun reset() {}
}
