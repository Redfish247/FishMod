package fishmod.compat

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

typealias EndermanT = net.minecraft.world.entity.monster.EnderMan

val ClientboundLevelParticlesPacket.speed: Float get() = maxSpeed
val ClientboundLevelParticlesPacket.limiterOverridden: Boolean get() = isOverrideLimiter
val LivingEntity.swingingNow: Boolean get() = swinging
fun ItemStack.isShovel(): Boolean = item is net.minecraft.world.item.ShovelItem
fun BlockState.blocksMotionCompat(): Boolean = blocksMotion()
fun LocalPlayer.dropHeld(all: Boolean) { drop(all) }

fun ClientboundMoveEntityPacket.movedTo(e: Entity): Vec3? {
    if (xa.toInt() == 0 && ya.toInt() == 0 && za.toInt() == 0) return null
    return Vec3(e.x + xa / 4096.0, e.y + ya / 4096.0, e.z + za / 4096.0)
}

fun SignBlockEntity.frontLine(i: Int): String = frontText.getMessage(i, false).string
fun signUpdatePacket(sign: SignBlockEntity, lines: List<String>): ServerboundSignUpdatePacket =
    ServerboundSignUpdatePacket(sign.blockPos, true, lines[0], lines[1], lines[2], lines[3])

fun com.mojang.blaze3d.vertex.PoseStack.rotate(q: org.joml.Quaternionf) { mulPose(q) }
fun glTextureId(t: Any?): Int = (t as? com.mojang.blaze3d.opengl.GlTexture)?.glId() ?: 0
