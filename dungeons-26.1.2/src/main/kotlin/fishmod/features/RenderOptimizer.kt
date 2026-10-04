package fishmod.features

import fishmod.utils.Location
import fishmod.utils.config.values.Visual
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object RenderOptimizer {

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

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (Visual.renderOptimizer && (Visual.roRemoveDamageIndicator || Visual.roFormatDamageIndicator) && Location.inSkyblock())
                FishDiag.guard("RenderOptimizer.5", "damage indicator pass failed") { tickDamageIndicators(mc) }
        }

        Events.ON_PACKET.register { packet ->
            if (!Visual.renderOptimizer) return@register false
            val mc = Minecraft.getInstance()
            try {
            when (packet) {
                is ClientboundSetEntityDataPacket -> {
                    if (Visual.roHideArcherPassive && Location.inDungeon()) {
                        val item = packet.packedItems().firstOrNull { it.id() == 8 }?.value() as? ItemStack
                        if (item != null && !item.isEmpty && item.`is`(Items.BONE_MEAL)) {
                            mc.execute { FishDiag.guard("RenderOptimizer.1", "archer passive removal failed") { mc.level?.removeEntity(packet.id(), Entity.RemovalReason.DISCARDED) } }
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
                            if (hide) mc.execute { FishDiag.guard("RenderOptimizer.2", "hidden entity removal failed") { mc.level?.removeEntity(packet.entity, Entity.RemovalReason.DISCARDED) } }
                        }
                    }
                }
            }
            } catch (e: Exception) {
                FishDiag.fail("RenderOptimizer.3", "render optimizer packet handling failed (${packet.javaClass.simpleName})", e)
            }
            false
        }
    }

    // Hypixel damage numbers: armor stands named like "1,234" or "✧1,234,567✧" (crit, one colour per char)
    private val DAMAGE = Regex("""^(\D{0,2})([\d,]+)(\D{0,2})$""")
    private val handledStands = HashSet<Int>()

    private fun tickDamageIndicators(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (handledStands.size > 2048) handledStands.clear()
        for (e in level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(48.0))) {
            if (e.tickCount > 40 || e.id in handledStands) continue
            val name = e.customName ?: continue
            val m = DAMAGE.matchEntire(name.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()) ?: continue
            val n = m.groupValues[2].replace(",", "").toLongOrNull() ?: continue
            handledStands.add(e.id)
            // Hidden rather than removed so Melee Hit Sound can still see the hit landed
            if (Visual.roRemoveDamageIndicator) { e.isCustomNameVisible = false; continue }
            val color = name.toFlatList().firstOrNull { it.string.any(Char::isDigit) }?.style ?: Style.EMPTY
            val crit = m.groupValues[1].isNotEmpty()
            val text = Component.literal(shorten(n)).withStyle(color)
            e.customName = if (crit) Component.literal("✧").withStyle(ChatFormatting.WHITE).append(text).append(Component.literal("✧").withStyle(ChatFormatting.WHITE)) else text
        }
    }

    private fun shorten(n: Long): String = when {
        n >= 1_000_000_000 -> "%.2fB".format(n / 1e9)
        n >= 1_000_000 -> "%.2fM".format(n / 1e6)
        n >= 10_000 -> "%.1fk".format(n / 1e3)
        else -> "%,d".format(n)
    }.replace(".00", "").replace(".0k", "k")

    private fun skullTexture(stack: ItemStack): String? {
        val profile = stack.get(DataComponents.PROFILE) ?: return null
        return profile.partialProfile().properties["textures"].firstOrNull()?.value()
    }

    @JvmStatic fun shouldDisableFireOverlay(): Boolean = Visual.renderOptimizer && Visual.roHideFireOverlay
    @JvmStatic fun hideDeathAnimation(): Boolean = Visual.renderOptimizer && Visual.roHideDeathAnimation
    @JvmStatic fun hideDyingArmorStands(): Boolean = Visual.renderOptimizer && Visual.roHideDyingArmorStands

    @JvmStatic
    fun filterBossBars(bars: Collection<LerpingBossEvent>): Collection<LerpingBossEvent> {
        if (!Visual.renderOptimizer || !Visual.roHideObjective) return bars
        return FishDiag.guard("RenderOptimizer.4", "boss bar filter failed") {
            bars.filterNot { it.name.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trimStart().startsWith("Objective:") }
        } ?: bars
    }
}
