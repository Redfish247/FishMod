package fishmod.features.dungeon.f7.dragons

import fishmod.utils.Misc
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

enum class WitherDragonState { SPAWNING, ALIVE, DEAD }

/**
 * The five M7 Wither dragons. Coordinates / AABBs / spawn ranges are calibrated — do not adjust.
 */
enum class WitherDragon(
    val spawnPos: Vec3,
    val box: AABB,
    val colorCode: Char,
    val rgb: Int,
    val displayName: String,
    val xRange: ClosedFloatingPointRange<Double>,
    val zRange: ClosedFloatingPointRange<Double>,
    val skipKillTime: Long,
    val bottomChin: BlockPos,
) {
    RED(Vec3(27.0, 14.0, 59.0), AABB(14.5, 13.0, 45.5, 39.5, 28.0, 70.5), 'c', 0xFF5555, "Power Dragon", 24.0..30.0, 56.0..62.0, 50, BlockPos(32, 19, 59)),
    ORANGE(Vec3(85.0, 14.0, 56.0), AABB(72.0, 8.0, 47.0, 102.0, 28.0, 77.0), '6', 0xFFAA00, "Flame Dragon", 82.0..88.0, 53.0..59.0, 62, BlockPos(80, 19, 56)),
    GREEN(Vec3(27.0, 14.0, 94.0), AABB(7.0, 8.0, 80.0, 37.0, 28.0, 110.0), 'a', 0x55FF55, "Apex Dragon", 23.0..29.0, 91.0..97.0, 52, BlockPos(32, 18, 94)),
    BLUE(Vec3(84.0, 14.0, 94.0), AABB(71.5, 16.0, 82.5, 96.5, 26.0, 107.5), 'b', 0x55FFFF, "Ice Dragon", 82.0..88.0, 91.0..97.0, 47, BlockPos(79, 19, 94)),
    PURPLE(Vec3(56.0, 14.0, 125.0), AABB(45.5, 13.0, 113.5, 68.5, 23.0, 136.5), '5', 0xAA00AA, "Soul Dragon", 53.0..59.0, 122.0..128.0, 38, BlockPos(56, 18, 128)),
    NONE(Vec3.ZERO, AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 'f', 0xFFFFFF, "None", 0.0..0.0, 0.0..0.0, 0, BlockPos(-1, -1, -1));

    @Volatile var state: WitherDragonState = WitherDragonState.DEAD
    @Volatile var timeToSpawn: Int = 100
    @Volatile var entityId: Int? = null
    @Volatile var entity: EnderDragon? = null
    @Volatile var health: Float = 1_000_000_000f
    @Volatile var spawnedTick: Long = 0
    @Volatile var sprayedTick: Long? = null
    @Volatile var arrowsHit: Int = 0
    @Volatile var offScoreboardTicks: Int = 0

    fun setAlive(id: Int, tick: Long) {
        state = WitherDragonState.ALIVE
        timeToSpawn = 100
        entityId = id
        spawnedTick = tick
        sprayedTick = null
        arrowsHit = 0
        offScoreboardTicks = 0
    }

    fun setDead(silent: Boolean, tick: Long) {
        if (state == WitherDragonState.DEAD) return
        state = WitherDragonState.DEAD
        timeToSpawn = 100
        entityId = null
        entity = null
        if (!silent) WitherDragons.onDragonDead(this, tick)
        if (WitherDragons.priorityDragon == this) WitherDragons.priorityDragon = NONE
    }

    fun reset() {
        state = WitherDragonState.DEAD
        timeToSpawn = 100
        entityId = null
        entity = null
        health = 1_000_000_000f
        spawnedTick = 0
        sprayedTick = null
        arrowsHit = 0
        offScoreboardTicks = 0
    }

    companion object {
        val real: List<WitherDragon> = entries.filter { it != NONE }
        fun resetAll() = entries.forEach { it.reset() }
        fun byEntityId(id: Int): WitherDragon? = real.firstOrNull { it.entityId == id }
    }
}

internal fun modMessage(msg: String) =
    Misc.addChatMessage(Component.literal("§5§lWD §7» §r" + msg.replace("&", "§")))
