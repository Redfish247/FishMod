package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Etherwarp Helper (ported from Odin's EtherWarpHelper, visual/audio feedback core only — the
 * rotator / trigger-bot bits are intentionally left out). Steps the look vector up to ~61 blocks,
 * boxes the block you'd warp onto (green when you could stand there, red when you couldn't), and
 * can play a cue on the teleport.
 */
object EtherwarpHelper {

    private val ITEMS = setOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END", "ETHERWARP_CONDUIT")

    // Hypixel confirms a successful etherwarp by sending exactly this sound packet: the ender dragon
    // hurt sound at volume 1.0 and this one magic pitch. Odin's EtherWarpHelper keys off the same
    // signature (1.8 "mob.enderdragon.hit" / vol 1 / pitch 0.53968257) — no sneak/hold check needed,
    // the triple is unique enough on its own.
    private const val ETHERWARP_PITCH = 0.53968257f

    @Volatile private var target: BlockPos? = null
    @Volatile private var valid = false
    private var lastCue = 0L

    private fun holdingEtherItem(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return ItemUtil.getId(p.mainHandItem) in ITEMS
    }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            target = null
            if (!FishSettings.etherwarpHelperEnabled || !FishSettings.etherwarpShowGuess) return@register
            if (!Location.inSkyblock()) return@register
            val p = mc.player ?: return@register
            if (!p.isShiftKeyDown || !holdingEtherItem()) return@register
            raycast(mc)
        }

        Events.ON_SOUND.register { event, volume, pitch ->
            if (!FishSettings.etherwarpHelperEnabled || !FishSettings.etherwarpSoundEnabled) return@register false
            if (volume != 1f || pitch != ETHERWARP_PITCH || !Location.inSkyblock()) return@register false
            // vol 1.0 + pitch 0.53968257 is already a near-unique fingerprint; also require either the
            // dragon-hurt id (name may not survive Hypixel's 1.8->modern sound translation) or that
            // an ether item is/was in hand.
            val looksRight = event == SoundEvents.ENDER_DRAGON_HURT ||
                event.location.path.let { it.contains("dragon") && (it.contains("hurt") || it.contains("hit")) } ||
                holdingEtherItem()
            if (!looksRight) return@register false
            // Emit directly (not SoundManager.play, which is gated by the global sound-master toggle
            // and, importantly, preset() only *resolves* a name — it never played anything, which is
            // why this was silent). This feature's own two toggles are gate enough.
            val now = System.currentTimeMillis()
            if (now - lastCue < 150L) return@register true
            lastCue = now
            val vol = FishSettings.etherwarpSoundVolume.coerceIn(0, 500) / 100f
            val pit = FishSettings.etherwarpSoundPitch.toFloat().coerceIn(0.5f, 2f)
            fishmod.utils.Misc.sendSound(SoundManager.preset(FishSettings.etherwarpSoundName), vol, pit)
            true // swallow Hypixel's dragon-hurt cue; we replaced it with the chosen sound
        }

        RenderingEvents.FILLED_BLOCK.register { _, m, vc -> if (!FishSettings.etherwarpDepth) render(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> if (FishSettings.etherwarpDepth) render(m, vc, fill = true) }
        RenderingEvents.LINE.register { _, m, vc -> if (!FishSettings.etherwarpDepth) render(m, vc, fill = false) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (FishSettings.etherwarpDepth) render(m, vc, fill = false) }
    }

    private fun raycast(mc: Minecraft) {
        val p = mc.player ?: return
        val level = mc.level ?: return
        val start: Vec3 = p.getEyePosition(1f)
        val dir: Vec3 = p.getViewVector(1f).normalize()
        val max = FishSettings.etherwarpRange.coerceIn(1, 61).toDouble()

        var last: BlockPos? = null
        var d = 0.0
        while (d <= max) {
            val pt = start.add(dir.scale(d))
            val bp = BlockPos.containing(pt.x, pt.y, pt.z)
            if (bp != last) {
                last = bp
                val state = level.getBlockState(bp)
                if (!state.isAir && !state.getCollisionShape(level, bp).isEmpty) {
                    target = bp.immutable()
                    valid = level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty &&
                        level.getBlockState(bp.above(2)).getCollisionShape(level, bp.above(2)).isEmpty
                    return
                }
            }
            d += 0.20
        }
    }

    private val FULL = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    private fun render(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        if (!FishSettings.etherwarpHelperEnabled || !FishSettings.etherwarpShowGuess) return
        val bp = target ?: return
        if (!valid && !FishSettings.etherwarpShowFail) return
        val color = if (valid) FishSettings.etherwarpColor else FishSettings.etherwarpFailColor
        val rgba = RenderUtils.toFloats(color)
        val fillRgba = floatArrayOf(rgba[0], rgba[1], rgba[2], rgba[3] * 0.4f)
        val lineRgba = floatArrayOf(rgba[0], rgba[1], rgba[2], 1f)
        val lvl = Minecraft.getInstance().level ?: return

        // Trace the block's real shape so slabs/stairs/heads/lanterns/walls draw (and read) as the
        // box you'd actually stand on, not a full cube. Use the visual outline shape first: a wall's
        // collision box is 1.5 blocks tall for mob pathing, which would poke the guess above the
        // block — the outline shape is the ~1-tall post+arms you actually see. Full Block forces a cube.
        val boxes: List<AABB> = if (FishSettings.etherwarpFullBlock) {
            listOf(FULL)
        } else {
            val st = lvl.getBlockState(bp)
            var shape = st.getShape(lvl, bp)
            if (shape.isEmpty) shape = st.getCollisionShape(lvl, bp)
            if (shape.isEmpty) listOf(FULL) else shape.toAabbs()
        }

        for (b in boxes) {
            val box = b.move(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble()).inflate(0.002)
            if (fill) RenderUtils.renderFilled(matrices, vc, box, fillRgba)
            else RenderUtils.renderOutline(matrices, vc, box, lineRgba)
        }
    }
}
