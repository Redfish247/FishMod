package fishmod.features.rift

import fishmod.utils.Misc
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import fishmod.features.rift.RiftSettings as S

// Half-Eaten Cave hay warning. No SkyHanni/Skyblocker equivalent; block-update packets never showed clears (debug 2026-10-24), so hay is scanned from the loaded level.
object RiftHay {

    // Main-thread only
    private val seen = HashSet<Long>()
    private val cleared = HashMap<Long, Long>()
    private var learnedMs = 0L
    private var lastWarn = 0L
    private var tick = 0
    private var warns = 0

    fun debug() = "hay: seen=${seen.size} cleared=${cleared.size} warns=$warns regrow=${learnedMs}ms"

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        Events.ON_WORLD_CHANGE.register { Minecraft.getInstance().execute { seen.clear(); cleared.clear() }; false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    // Hay seen in range marks a cell; a marked cell that is no longer hay is a cleared cell that will refill
    private fun scan() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val me = mc.player ?: return
        val c = me.blockPosition()
        val pos = BlockPos.MutableBlockPos()
        for (x in c.x - 10..c.x + 10) for (z in c.z - 10..c.z + 10) {
            pos.set(x, c.y, z)
            if (!level.hasChunkAt(pos)) continue
            for (y in c.y - 5..c.y + 6) {
                pos.set(x, y, z)
                val key = pos.asLong()
                if (level.getBlockState(pos).block == Blocks.HAY_BLOCK) {
                    if (seen.size < 20000) seen.add(key)
                    // Regrow delay learned from the last clear-to-refill gap
                    cleared.remove(key)?.let { t -> (System.currentTimeMillis() - t).takeIf { it in 1000L..120000L }?.let { learnedMs = it } }
                } else if (key in seen) cleared.putIfAbsent(key, System.currentTimeMillis())
            }
        }
    }

    // Age at which a cleared cell counts as about to refill
    private fun dueMs() = (if (learnedMs > 0) learnedMs else S.riftHayDelay * 1000L) - S.riftHayLead * 1000L

    // Cleared hay cells the player's body overlaps
    private fun standingIn(): Boolean {
        val me = Minecraft.getInstance().player ?: return false
        if (cleared.isEmpty()) return false
        val now = System.currentTimeMillis()
        val due = dueMs()
        val box = me.boundingBox
        for (x in Math.floor(box.minX).toInt()..Math.floor(box.maxX - 1e-3).toInt())
            for (y in Math.floor(box.minY).toInt()..Math.floor(box.maxY - 1e-3).toInt())
                for (z in Math.floor(box.minZ).toInt()..Math.floor(box.maxZ - 1e-3).toInt())
                    if (cleared[BlockPos.asLong(x, y, z)]?.let { now - it >= due } == true) return true
        return false
    }

    private fun tick() {
        if (!S.riftHay || !Rift.inRift()) return
        if (tick++ % 4 == 0) scan()
        if (!standingIn()) return
        val now = System.currentTimeMillis()
        if (now - lastWarn < 800L) return
        lastWarn = now; warns++
        if (S.riftHayTitle) Misc.forceTitle(Component.literal(S.riftHayText), Component.empty(), 700)
        if (S.riftHaySound) Minecraft.getInstance().player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.6f)
    }

    private fun render() {
        if (!S.riftHay || !S.riftHayBoxes || !Rift.inRift() || cleared.isEmpty()) return
        val me = Minecraft.getInstance().player ?: return
        val c = S.riftHayColor
        val now = System.currentTimeMillis(); val due = dueMs()
        for ((k, t) in cleared) {
            if (now - t < due) continue
            val p = BlockPos.of(k)
            if (me.distanceToSqr(p.x + 0.5, p.y + 0.5, p.z + 0.5) > 144.0) continue
            RenderUtils.gizmoBox(AABB(p), (c and 0xFFFFFF) or 0x40000000, c, false)
        }
    }
}
