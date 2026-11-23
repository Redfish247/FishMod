package fishmod.features.end

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import kotlin.math.abs
import kotlin.math.floor

// Ender Node finder: nodes emit PORTAL + WITCH particles a quarter block off one face; outline that block
object EndNodeFinder {
    private class Node(var portal: Int = 0, var witch: Int = 0, var lastSeen: Long = 0L)

    private const val MIN_EACH = 3
    private const val EXPIRE_MS = 10_000L
    private val nodes = HashMap<BlockPos, Node>()

    fun init() {
        FishEvents.worldChange.register { nodes.clear(); false }
        FishEvents.particle.register { p ->
            if (S.endNodeFinder && SkyblockLocation.`in`(SkyblockLocation.THE_END)) FishDiag.guard("EndNodeFinder.1", "ender node particle failed") { onParticle(p) }
            false
        }
        RenderHooks.GIZMO.register { _ -> FishDiag.guard("EndNodeFinder.2", "ender node render failed") { render() } }
    }

    private fun near(a: Double, b: Double) = abs(a - b) < 0.01

    // which face the particle hugs: 0.25 / 0.75 fractional offset on exactly one axis
    private fun faceOf(x: Double, y: Double, z: Double): Direction? {
        val fx = x - floor(x); val fy = y - floor(y); val fz = z - floor(z)
        return when {
            near(fy, 0.25) -> Direction.DOWN
            near(fy, 0.75) -> Direction.UP
            near(fx, 0.25) -> Direction.WEST
            near(fx, 0.75) -> Direction.EAST
            near(fz, 0.25) -> Direction.NORTH
            near(fz, 0.75) -> Direction.SOUTH
            else -> null
        }
    }

    private fun onParticle(p: ClientboundLevelParticlesPacket) {
        val type = p.particle.type
        val isPortal = type === ParticleTypes.PORTAL
        if (!isPortal && type !== ParticleTypes.WITCH) return
        val dir = faceOf(p.x, p.y, p.z) ?: return
        val pos = BlockPos(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt()).relative(dir)
        val level = Minecraft.getInstance().level ?: return
        val block = level.getBlockState(pos).block
        if (block !== Blocks.END_STONE && block !== Blocks.OBSIDIAN) return
        val n = nodes.getOrPut(pos) { Node() }
        if (isPortal) n.portal++ else n.witch++
        n.lastSeen = System.currentTimeMillis()
    }

    private fun render() {
        if (!S.endNodeFinder || nodes.isEmpty()) return
        val level = Minecraft.getInstance().level ?: return
        val now = System.currentTimeMillis()
        nodes.entries.removeIf { (pos, n) -> now - n.lastSeen > EXPIRE_MS || level.getBlockState(pos).isAir }
        for ((pos, n) in nodes) {
            if (n.portal < MIN_EACH || n.witch < MIN_EACH) continue
            FishDraw.gizmoBox(AABB(pos), (S.endNodeFinderColor and 0x00FFFFFF) or 0x40000000, S.endNodeFinderColor, true)
        }
    }
}
