package fishmod.features.rift

import fishmod.utils.debug.FishDiag
import fishmod.utils.Misc
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import fishmod.features.rift.RiftSettings as S

// Half-Eaten Cave hay warning. No SkyHanni/Skyblocker equivalent exists; cleared hay cells come from block-update packets.
object RiftHay {

    private val cleared = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private var lastWarn = 0L
    @Volatile private var hayUpdates = 0
    @Volatile private var warns = 0

    fun init() {
        Events.ON_PACKET.register { packet ->
            if (!S.riftHay || !Rift.inRift()) return@register false
            try {
                when (packet) {
                    is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                    is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::onBlock)
                }
            } catch (e: Exception) { FishDiag.fail("RiftHay.1", "hay packet handler threw", e) }
            false
        }
        Events.ON_WORLD_CHANGE.register { cleared.clear(); false }
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    // The packet is read before the client applies it, so the level still holds the old state
    private fun onBlock(pos: BlockPos, new: BlockState) {
        val key = pos.asLong()
        if (new.block == Blocks.HAY_BLOCK) { hayUpdates++; cleared.remove(key); return }
        val old = Minecraft.getInstance().level?.getBlockState(pos) ?: return
        if (old.block == Blocks.HAY_BLOCK && cleared.size < 4096) cleared.add(key)
    }

    fun debug() = "hay: cleared=${cleared.size} hayUpdates=$hayUpdates warns=$warns"

    // Cleared hay cells the player's body overlaps
    private fun standingIn(): Boolean {
        val me = Minecraft.getInstance().player ?: return false
        if (cleared.isEmpty()) return false
        val box = me.boundingBox
        for (x in Math.floor(box.minX).toInt()..Math.floor(box.maxX - 1e-3).toInt())
            for (y in Math.floor(box.minY).toInt()..Math.floor(box.maxY - 1e-3).toInt())
                for (z in Math.floor(box.minZ).toInt()..Math.floor(box.maxZ - 1e-3).toInt())
                    if (BlockPos.asLong(x, y, z) in cleared) return true
        return false
    }

    private fun tick() {
        if (!S.riftHay || !Rift.inRift() || !standingIn()) return
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
        for (k in cleared) {
            val p = BlockPos.of(k)
            if (me.distanceToSqr(p.x + 0.5, p.y + 0.5, p.z + 0.5) > 144.0) continue
            RenderUtils.gizmoBox(AABB(p), (c and 0xFFFFFF) or 0x40000000, c, false)
        }
    }
}
