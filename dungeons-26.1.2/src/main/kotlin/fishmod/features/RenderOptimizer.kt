package fishmod.features

import fishmod.utils.Location
import fishmod.utils.config.values.Visual
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Hides clutter entities / particles / overlays (ported 1:1 from Odin's `RenderOptimizer`):
 *  - falling blocks / lightning / xp orbs — cancelled in [fishmod.mixin.ClientPlayNetworkHandlerMixin]
 *    (handleAddEntity).
 *  - explosion particles — cancelled here via [Events.ON_PARTICLE].
 *  - archer-passive bone meal + skull-equipped fairy / soul weaver / tentacle mobs — the carrying
 *    entity is dropped from the client world here.
 *  - death animation / dying-mob armor stands — [fishmod.mixin.EntityRendererMixin].
 *  - fire overlay — [fishmod.mixin.ScreenEffectRendererMixin].
 */
object RenderOptimizer {

    // Base64 skull textures, straight from Odin.
    const val TENTACLE_TEXTURE =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxOTg1NzI3NzI0OSwKICAicHJvZmlsZUlkIiA6ICIxODA1Y2E2MmM0ZDI0M2NiOWQxYmY4YmM5N2E1YjgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJSdWxsZWQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdkODM2NzQ5MjZiODk3MTRlNmI1YTU1NDcwNTAxYzA0YjA2NmRkODdiZjZjMzM1Y2RkYzZlNjBhMWExYTVmNSIKICAgIH0KICB9Cn0="
    const val HEALER_FAIRY_TEXTURE =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxOTQ2MzA5MTA0NywKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJxdWludHVwbGV0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJlZWRjZmZjNmExMWEzODM0YTI4ODQ5Y2MzMTZhZjdhMjc1MmEzNzZkNTM2Y2Y4NDAzOWNmNzkxMDhiMTY3YWUiCiAgICB9CiAgfQp9"
    const val SOUL_WEAVER_TEXTURE =
        "eyJ0aW1lc3RhbXAiOjE1NTk1ODAzNjI1NTMsInByb2ZpbGVJZCI6ImU3NmYwZDlhZjc4MjQyYzM5NDY2ZDY3MjE3MzBmNDUzIiwicHJvZmlsZU5hbWUiOiJLbGxscmFoIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZjI0ZWQ2ODc1MzA0ZmE0YTFmMGM3ODViMmNiNmE2YTcyNTYzZTlmM2UyNGVhNTVlMTgxNzg0NTIxMTlhYTY2In19fQ=="

    @JvmStatic
    fun init() {
        Events.ON_PARTICLE.register { packet ->
            Visual.renderOptimizer && Visual.roHideExplosionParticles &&
                (packet.particle.type === ParticleTypes.EXPLOSION || packet.particle.type === ParticleTypes.EXPLOSION_EMITTER)
        }

        Events.ON_PACKET.register { packet ->
            if (!Visual.renderOptimizer) return@register false
            val mc = Minecraft.getInstance()
            when (packet) {
                is ClientboundSetEntityDataPacket -> {
                    if (Visual.roHideArcherPassive && Location.inDungeon()) {
                        val item = packet.packedItems().firstOrNull { it.id() == 8 }?.value() as? ItemStack
                        if (item != null && !item.isEmpty && item.`is`(Items.BONE_MEAL)) {
                            mc.execute { mc.level?.removeEntity(packet.id(), Entity.RemovalReason.DISCARDED) }
                        }
                    }
                }
                is ClientboundSetEquipmentPacket -> {
                    if (Location.inDungeon()) {
                        for (pair in packet.slots) {
                            val slot = pair.first ?: continue
                            val stack = pair.second ?: continue
                            if (stack.isEmpty) continue
                            val tex = skullTexture(stack) ?: continue
                            val hide =
                                (Visual.roHideHealerFairy && slot == EquipmentSlot.MAINHAND && tex == HEALER_FAIRY_TEXTURE) ||
                                (Visual.roHideSoulWeaver && slot == EquipmentSlot.HEAD && tex == SOUL_WEAVER_TEXTURE) ||
                                (Visual.roHideTentacleHead && slot == EquipmentSlot.HEAD && tex == TENTACLE_TEXTURE)
                            if (hide) mc.execute { mc.level?.removeEntity(packet.entity, Entity.RemovalReason.DISCARDED) }
                        }
                    }
                }
            }
            false
        }
    }

    private fun skullTexture(stack: ItemStack): String? {
        val profile = stack.get(DataComponents.PROFILE) ?: return null
        return profile.partialProfile().properties["textures"].firstOrNull()?.value()
    }

    @JvmStatic fun shouldDisableFireOverlay(): Boolean = Visual.renderOptimizer && Visual.roHideFireOverlay
    @JvmStatic fun hideDeathAnimation(): Boolean = Visual.renderOptimizer && Visual.roHideDeathAnimation
    @JvmStatic fun hideDyingArmorStands(): Boolean = Visual.renderOptimizer && Visual.roHideDyingArmorStands
}
