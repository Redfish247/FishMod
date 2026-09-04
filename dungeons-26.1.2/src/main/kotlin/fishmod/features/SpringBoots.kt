package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.phys.AABB

/**
 * Spring Boots charge tracker. The jump charge is read from the note-block sound pitches Hypixel
 * plays while you hold the crouch charge; a firework sound resets it. Shows the charge on a HUD and
 * a box at the predicted landing height.
 */
object SpringBoots {

    private const val NAME = "Spring Boots"
    private const val LOW_PITCH = 0.6984127f
    private val HIGH_PITCHES = floatArrayOf(0.82539684f, 0.8888889f, 0.93650794f, 1.0476191f, 1.1746032f, 1.3174603f, 1.7777778f)
    private val RESET_PITCHES = floatArrayOf(0.0952381f, 1.6984127f)
    private val HEIGHTS = floatArrayOf(
        0f, 3f, 6.5f, 9f, 11.5f, 13.5f, 16f, 18f, 19f, 20.5f, 22.5f, 25f, 26.5f, 28f, 29f, 30f, 31f, 33f,
        34f, 35.5f, 37f, 38f, 39.5f, 40f, 41f, 42.5f, 43.5f, 44f, 45f, 46f, 47f, 48f, 49f, 50f, 51f, 52f,
        53f, 54f, 55f, 56f, 57f, 58f, 59f, 60f, 61f,
    )

    private val NOTE_PLING = SoundEvents.NOTE_BLOCK_PLING.value().location
    private val FIREWORK = SoundEvents.FIREWORK_ROCKET_LAUNCH.location

    @Volatile private var currentHeight = 0f
    private var highs = 0
    private var lows = 0

    private fun reset() { currentHeight = 0f; highs = 0; lows = 0 }

    private fun wearingSpringBoots(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return ItemUtil.getId(p.getItemBySlot(EquipmentSlot.FEET)) == "SPRING_BOOTS"
    }

    @JvmStatic
    fun init() {
        fishmod.features.FishHudEditor.register(
            NAME,
            { FishSettings.springBootsHudX }, { v -> FishSettings.springBootsHudX = v },
            { FishSettings.springBootsHudY }, { v -> FishSettings.springBootsHudY = v },
            80, 12,
            { FishSettings.springBootsScale }, { v -> FishSettings.springBootsScale = v }
        )

        // on-ground gate: only the charge plings played while standing count — airborne ones inflate the charge
        Events.ON_SOUND.register { event, _, pitch ->
            if (!FishSettings.springBootsEnabled || !Location.inSkyblock()) return@register false
            val p = Minecraft.getInstance().player ?: return@register false
            if (!p.onGround()) return@register false
            when (event.location) {
                NOTE_PLING -> if (p.isCrouching && wearingSpringBoots()) {
                    when {
                        pitch == LOW_PITCH -> lows = (lows + 1).coerceAtMost(2)
                        HIGH_PITCHES.any { it == pitch } -> highs++
                    }
                    currentHeight = HEIGHTS[(lows + highs).coerceIn(HEIGHTS.indices)]
                }
                FIREWORK -> if (RESET_PITCHES.any { it == pitch }) reset()
            }
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val p = mc.player ?: return@register
            if (!p.isCrouching || !wearingSpringBoots() || !Location.inSkyblock()) reset()
        }

        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> renderBox(m, vc) }
    }

    private fun renderBox(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.springBootsEnabled || !FishSettings.springBootsBox || currentHeight <= 0f) return
        val p = Minecraft.getInstance().player ?: return
        val y = p.y + currentHeight
        val box = AABB(p.x - 0.5, y, p.z - 0.5, p.x + 0.5, y + 1.0, p.z + 0.5)
        RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(FishSettings.springBootsBoxColor))
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.springBootsEnabled || currentHeight <= 0f) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val pct = currentHeight / HEIGHTS.last() * 100f
        val label = if (FishSettings.springBootsShowBlocks) "§aSpring: §f${String.format("%.1f", currentHeight)}"
        else "§aCharge: §f${String.format("%.0f", pct)}%"
        val sc = FishSettings.springBootsScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.springBootsHudX.toFloat(), FishSettings.springBootsHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, label, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
