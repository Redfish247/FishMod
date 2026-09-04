package fishmod.features.dungeon.f7

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.regex.Pattern

/**
 * M7 Relics — drives `Floor7.enableRelicStartTimer` / `relicSpawnTicks` / `renderRelicHighlight`.
 *
 * - Spawn Timer: countdown of [Floor7.relicSpawnTicks] ticks after Necron's "All this, for nothing..."
 *   (P5 start), shown on a small HUD.
 * - Relic Box: while holding a Corrupted <colour> Relic, box + tracer its cauldron in P5.
 */
object M7Relics {

    private enum class Relic(val itemName: String, val cauldron: Vec3, val argb: Int) {
        RED("Corrupted Red Relic", Vec3(51.5, 7.0, 42.5), 0xC0FF0000.toInt()),
        ORANGE("Corrupted Orange Relic", Vec3(57.5, 7.0, 42.5), 0xC0FF7200.toInt()),
        GREEN("Corrupted Green Relic", Vec3(49.5, 7.0, 44.5), 0xC000FF00.toInt()),
        BLUE("Corrupted Blue Relic", Vec3(59.5, 7.0, 44.5), 0xC0008AFF.toInt()),
        PURPLE("Corrupted Purple Relic", Vec3(54.5, 7.0, 41.5), 0xC081006F.toInt());
    }

    private const val NAME = "Relic Spawn Timer"
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val P5_START = Pattern.compile("\\[BOSS] Necron: All this, for nothing\\.\\.\\.")

    @Volatile private var spawnEndMs = 0L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.relicTimerHudX }, { v -> FishSettings.relicTimerHudX = v },
            { FishSettings.relicTimerHudY }, { v -> FishSettings.relicTimerHudY = v },
            60, 12,
            { FishSettings.relicTimerScale }, { v -> FishSettings.relicTimerScale = v }
        )

        Events.ON_GAME_MESSAGE.register { text ->
            if (Floor7.enableRelicStartTimer && P5_START.matcher(COLOR.replace(text.string, "")).find()) {
                spawnEndMs = System.currentTimeMillis() + Floor7.relicSpawnTicks.coerceIn(1, 200) * 50L
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { spawnEndMs = 0L; false }

        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> renderBox(m, vc) }
    }

    private fun heldRelic(): Relic? {
        val p = Minecraft.getInstance().player ?: return null
        val name = COLOR.replace(p.mainHandItem.hoverName.string, "").trim()
        return Relic.entries.firstOrNull { name == it.itemName }
    }

    private fun renderBox(matrices: PoseStack, vc: VertexConsumer) {
        if (!Floor7.renderRelicHighlight || !Location.inDungeon() || !Phase.inP5()) return
        val relic = heldRelic() ?: return
        val c = relic.cauldron
        val box = AABB(c.x - 0.5, c.y - 0.5, c.z - 0.5, c.x + 0.5, c.y + 0.5, c.z + 0.5)
        RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats((0x66 shl 24) or (relic.argb and 0xFFFFFF)))
        RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(relic.argb))
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Floor7.enableRelicStartTimer) return
        val left = spawnEndMs - System.currentTimeMillis()
        if (left <= 0L) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val sc = FishSettings.relicTimerScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.relicTimerHudX.toFloat(), FishSettings.relicTimerHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "§dRelic §f" + String.format("%.2fs", left / 1000.0), 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
