package fishmod.features.dungeon.f7

import fishmod.utils.events.chatRaw
import com.mojang.blaze3d.vertex.PoseStack
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.phys.Vec3
import fishmod.utils.debug.FishDiag

object GateDisplay {

    private enum class GateState { HIDDEN, DESTROY, DONE }

    private const val GATE_MESSAGE = "The gate has been destroyed!"

    private val GATE_POS = arrayOf(
        Vec3(100.5, 122.5, 121.5),
        Vec3(19.5, 122.5, 132.5),
        Vec3(8.5, 113.5, 51.5)
    )

    private val state = arrayOf(GateState.HIDDEN, GateState.HIDDEN, GateState.HIDDEN)

    private var armed = false

    private var lastTermTime = 0.0

    @JvmStatic
    fun init() {
        FishEvents.locationChange.register { _ -> reset(); armed = false; lastTermTime = 0.0; false }
        FishEvents.phaseChange.register {
            if (BossPhase.inTerminals()) {
                val t = BossPhase.getPhaseTime(6)
                if (!armed || t < lastTermTime - 1.0) {
                    reset()
                    show(0)
                    armed = true
                }
                lastTermTime = t
            } else {
                reset()
                armed = false
                lastTermTime = 0.0
            }
            false
        }
        FishEvents.sectionChange.register {
            if (!SkyblockLocation.inDungeon()) return@register false
            val s = GoldorSection.getSection()
            FishDiag.check(s in 1..5, "GateDisplay.1") { "section change to out-of-range section $s" }
            if (s == 2 || s == 3) show(s - 1)
            false
        }
        FishEvents.chat.register { message ->
            if (Floor7Settings.gateDisplayEnabled && SkyblockLocation.inDungeon() && BossPhase.inTerminals()
                && message.chatRaw == GATE_MESSAGE
            ) {
                if (fishmod.utils.debug.FishDebug.termInfo) {
                    val p = net.minecraft.client.Minecraft.getInstance().player?.position()
                    fishmod.utils.FishUtil.addChatMessage(Component.literal(
                        "[GateDisplay] destroyed at section=${GoldorSection.getSection()} you@" +
                            "${p?.x?.let { "%.1f".format(it) }}, ${p?.y?.let { "%.1f".format(it) }}, ${p?.z?.let { "%.1f".format(it) }}" +
                            " state=${state.toList()}"
                    ))
                }
                markDestroyed()
            }
            false
        }
        fishmod.utils.rendering.RenderHooks.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!Floor7Settings.gateDisplayEnabled || !SkyblockLocation.inDungeon() || !BossPhase.inTerminals()) return@register
            if (state.all { it == GateState.HIDDEN }) return@register
            try { render(ctx, matrices) } catch (e: Exception) { FishDiag.fail("GateDisplay.2", "gate display render threw (state=${state.toList()})", e) }
        }
    }

    private fun show(index: Int) {
        if (index !in GATE_POS.indices) { FishDiag.fail("GateDisplay.4", "show() gate index out of range: $index"); return }
        state[index] = GateState.DESTROY
    }

    private fun markDestroyed() {
        for (i in state.indices) {
            if (state[i] == GateState.DESTROY) {
                state[i] = GateState.DONE
                return
            }
        }
    }

    private fun reset() {
        for (i in state.indices) state[i] = GateState.HIDDEN
    }

    private fun render(ctx: LevelRenderContext, matrices: PoseStack) {
        if (!Floor7Settings.gateDisplayEnabled || !SkyblockLocation.inDungeon()) return
        val i = GoldorSection.getSection() - 1
        if (i !in GATE_POS.indices) return
        val text = when (state[i]) {
            GateState.DESTROY -> styled("✖", FishConstants.RED)
            GateState.DONE -> styled("✔", FishConstants.GREEN)
            GateState.HIDDEN -> return
        }
        FishDraw.renderText(ctx, matrices, text, GATE_POS[i], Floor7Settings.gateDisplayScale)
    }

    private fun styled(symbol: String, argb: Int): Component =
        Component.literal(symbol).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(argb and 0xFFFFFF)).withBold(true))
}
