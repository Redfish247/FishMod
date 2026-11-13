package fishmod.compat

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

typealias EndermanT = net.minecraft.world.entity.monster.Enderman

val ClientboundLevelParticlesPacket.speed: Float get() = xMaxSpeed()
val ClientboundLevelParticlesPacket.limiterOverridden: Boolean get() = overrideLimiter()
val LivingEntity.swingingNow: Boolean get() = isSwinging
fun ItemStack.isShovel(): Boolean = `is`(net.minecraft.tags.ItemTags.SHOVELS)
fun BlockState.blocksMotionCompat(): Boolean = isSolid

fun LocalPlayer.dropHeld(all: Boolean) { net.minecraft.client.Minecraft.getInstance().gameMode?.dropItem(this, all) }

fun ClientboundMoveEntityPacket.movedTo(e: Entity): Vec3? {
    if (!hasPosition()) return null
    val end = positionDelta.decode(e.positionCodec).endPosition()
    return if (end == e.position()) null else end
}

fun SignBlockEntity.frontLine(i: Int): String = getText(SignTextSlot.FRONT).getMessages(false)[i].string
fun signUpdatePacket(sign: SignBlockEntity, lines: List<String>): ServerboundSignUpdatePacket =
    ServerboundSignUpdatePacket(sign.blockPos, lines, SignTextSlot.FRONT)

fun com.mojang.blaze3d.vertex.PoseStack.rotate(q: org.joml.Quaternionf) { mulPose(org.joml.Matrix4f().rotation(q)) }
fun glTextureId(t: Any?): Int = (t as? com.mojang.renderpearl.backend.opengl.GlTexture)?.glId() ?: 0
