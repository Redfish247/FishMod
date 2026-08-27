package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Secret Clicked (ported from Odin's SecretClicked): a box + chime when you click a dungeon secret.
 * Odin fires off its own SecretPickupEvent (interact / bat kill / item pickup); this port covers the
 * interact case via Fabric's [UseBlockCallback] — chest / lever / skull, which is the large majority.
 * Bat- and item-secret detection is a follow-up.
 */
object SecretClicked {

    private class Secret(val pos: BlockPos, @JvmField var locked: Boolean = false)

    private val clicked = CopyOnWriteArrayList<Secret>()
    private var lastChime = 0L
    private val COLOR = Regex("§.")

    @JvmStatic
    fun init() {
        UseBlockCallback.EVENT.register(UseBlockCallback { _, _, hand, hit ->
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) onInteract(hit.blockPos)
            InteractionResult.PASS
        })

        Events.ON_GAME_MESSAGE.register { text ->
            if (COLOR.replace(text.string, "").trim() == "That chest is locked!") {
                clicked.lastOrNull()?.locked = true
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { clicked.clear(); false }

        RenderingEvents.FILLED_BLOCK.register { _, m, vc -> if (!FishSettings.secretClickedDepthCheck) render(m, vc) }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.secretClickedDepthCheck) render(m, vc) }
    }

    private fun active(): Boolean =
        FishSettings.secretClickedEnabled && Location.inDungeon() &&
            (FishSettings.secretClickedInBoss || !Phase.inBoss())

    private fun onInteract(pos: BlockPos) {
        if (!active()) return
        val mc = net.minecraft.client.Minecraft.getInstance()
        val block = mc.level?.getBlockState(pos)?.block ?: return
        if (block !is ChestBlock && block !is LeverBlock && block !is AbstractSkullBlock) return
        chime()
        if (!FishSettings.secretClickedBoxes || clicked.any { it.pos == pos }) return
        clicked.add(Secret(pos.immutable()))
        Scheduler.scheduleTask({ clicked.removeFirstOrNull() }, FishSettings.secretClickedTimeToStay.coerceIn(1, 20) * 20)
    }

    private fun chime() {
        if (!FishSettings.secretClickedChime) return
        if (Phase.inBoss() && !FishSettings.secretClickedChimeInBoss) return
        val now = System.currentTimeMillis()
        if (now - lastChime <= 10) return
        lastChime = now
        SoundManager.play(
            SoundEvents.BLAZE_HURT,
            FishSettings.secretClickedVolume.coerceIn(0, 100) / 100f,
            FishSettings.secretClickedPitch.toFloat().coerceIn(0f, 2f),
            "secretChime", 0
        )
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer) {
        if (!active() || !FishSettings.secretClickedBoxes || clicked.isEmpty()) return
        val style = FishSettings.secretClickedStyle
        for (s in clicked) {
            val argb = if (s.locked) FishSettings.secretClickedLockedColor else FishSettings.secretClickedColor
            val box = AABB(s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble(),
                s.pos.x + 1.0, s.pos.y + 1.0, s.pos.z + 1.0).inflate(0.002)
            val rgba = RenderUtils.toFloats(argb)
            if (style != "Outline") RenderUtils.renderFilled(matrices, vc, box, rgba)
            if (style != "Filled") RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(0xFF000000.toInt() or (argb and 0xFFFFFF)))
        }
    }
}
