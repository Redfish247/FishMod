package fishmod.compat

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignBlockEntity
//? if >=26.3 {
/*import net.minecraft.world.level.block.entity.SignTextSlot
*///?}
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

//? if <26.3 {
typealias EndermanT = net.minecraft.world.entity.monster.EnderMan
//?} else {
/*typealias EndermanT = net.minecraft.world.entity.monster.Enderman
*///?}

//? if <26.3 {
val ClientboundLevelParticlesPacket.speed: Float get() = maxSpeed
val ClientboundLevelParticlesPacket.limiterOverridden: Boolean get() = isOverrideLimiter
val LivingEntity.swingingNow: Boolean get() = swinging
fun ItemStack.isShovel(): Boolean = item is net.minecraft.world.item.ShovelItem
fun BlockState.blocksMotionCompat(): Boolean = blocksMotion()
fun LocalPlayer.dropHeld(all: Boolean) { drop(all) }
//?} else {
/*val ClientboundLevelParticlesPacket.speed: Float get() = xMaxSpeed()
val ClientboundLevelParticlesPacket.limiterOverridden: Boolean get() = overrideLimiter()
val LivingEntity.swingingNow: Boolean get() = isSwinging
fun ItemStack.isShovel(): Boolean = `is`(net.minecraft.tags.ItemTags.SHOVELS)
fun BlockState.blocksMotionCompat(): Boolean = isSolid

fun LocalPlayer.dropHeld(all: Boolean) { net.minecraft.client.Minecraft.getInstance().gameMode?.dropItem(this, all) }
*///?}

fun ClientboundMoveEntityPacket.movedTo(e: Entity): Vec3? {
    //? if <26.3 {
    if (xa.toInt() == 0 && ya.toInt() == 0 && za.toInt() == 0) return null
    return Vec3(e.x + xa / 4096.0, e.y + ya / 4096.0, e.z + za / 4096.0)
    //?} else {
    /*if (!hasPosition()) return null
    val end = positionDelta.decode(e.positionCodec).endPosition()
    return if (end == e.position()) null else end
    *///?}
}

//? if <26.3 {
fun SignBlockEntity.frontLine(i: Int): String = frontText.getMessage(i, false).string
//?} else {
/*fun SignBlockEntity.frontLine(i: Int): String = getText(SignTextSlot.FRONT).getMessages(false)[i].string
*///?}
fun signUpdatePacket(sign: SignBlockEntity, lines: List<String>): ServerboundSignUpdatePacket =
    //? if <26.3 {
    ServerboundSignUpdatePacket(sign.blockPos, true, lines[0], lines[1], lines[2], lines[3])
    //?} else {
    /*ServerboundSignUpdatePacket(sign.blockPos, lines, SignTextSlot.FRONT)
    *///?}

//? if <26.3 {
fun com.mojang.blaze3d.vertex.PoseStack.rotate(q: org.joml.Quaternionf) { mulPose(q) }
//?} else {
/*fun com.mojang.blaze3d.vertex.PoseStack.rotate(q: org.joml.Quaternionf) { mulPose(org.joml.Matrix4f().rotation(q)) }
*///?}

//? if <26.3 {
fun net.minecraft.client.Minecraft.showItemActivation(stack: ItemStack) { gameRenderer.displayItemActivation(stack) }
//?} else {
/*
fun net.minecraft.client.Minecraft.showItemActivation(stack: ItemStack) { player?.displayItemActivation(stack) }
*///?}
