package fishmod.features.dungeon.puzzles.odin

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import java.util.concurrent.CopyOnWriteArraySet

object WeirdosSolver {

    private var correctPos: BlockPos? = null
    private val wrongPositions = CopyOnWriteArraySet<BlockPos>()
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    fun onNPCMessage(npc: String, msg: String) {
        if (solutions.none { it.matches(msg) } && wrong.none { it.matches(msg) }) return
        val cleanNpc = COLOR.replace(npc, "").trim()
        val correctNPC = Minecraft.getInstance().level?.entitiesForRendering()?.find {
            it is ArmorStand && COLOR.replace(it.name.string, "").trim() == cleanNpc
        } ?: return
        val room = OdinScan.currentRoom ?: return
        val relativePos = room.getRelativeCoords(BlockPos(correctNPC.x.toInt() - 1, 69, correctNPC.z.toInt() - 1))
        val pos = room.getRealCoords(relativePos.offset(1, 0, 0))

        if (solutions.any { it.matches(msg) }) {
            correctPos = pos
            Minecraft.getInstance().player?.playSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 1f)
        } else wrongPositions.add(pos)
    }

    fun onRenderWorld() {
        if (OdinScan.currentRoomName != "Three Weirdos") return
        val style = ORender.style()
        correctPos?.let { ORender.styledBox(AABB(it), FishSettings.weirdosCorrectColor, style) }
        wrongPositions.forEach { ORender.styledBox(AABB(it), FishSettings.weirdosWrongColor, style) }
    }

    fun reset() {
        correctPos = null
        wrongPositions.clear()
    }

    private val solutions = listOf(
        Regex("The reward is not in my chest!"),
        Regex("At least one of them is lying, and the reward is not in .+'s chest.?"),
        Regex("My chest doesn't have the reward. We are all telling the truth.?"),
        Regex("My chest has the reward and I'm telling the truth!"),
        Regex("The reward isn't in any of our chests.?"),
        Regex("Both of them are telling the truth. Also, .+ has the reward in their chest.?"),
    )

    private val wrong = listOf(
        Regex("One of us is telling the truth!"),
        Regex("They are both telling the truth. The reward isn't in .+'s chest."),
        Regex("We are all telling the truth!"),
        Regex(".+ is telling the truth and the reward is in his chest."),
        Regex("My chest doesn't have the reward. At least one of the others is telling the truth!"),
        Regex("One of the others is lying."),
        Regex("They are both telling the truth, the reward is in .+'s chest."),
        Regex("They are both lying, the reward is in my chest!"),
        Regex("The reward is in my chest."),
        Regex("The reward is not in my chest. They are both lying."),
        Regex(".+ is telling the truth."),
        Regex("My chest has the reward."),
    )
}
