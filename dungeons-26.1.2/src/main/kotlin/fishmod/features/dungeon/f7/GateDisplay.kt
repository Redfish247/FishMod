package fishmod.features.dungeon.f7

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.phys.Vec3

/**
 * Big bulky world-space ✖/✔ over the Goldor gate for the section you're currently in, fixed to the
 * 3 known gate coordinates. The active gate shows a red ✖ while its section is live and a green ✔
 * once "The gate has been destroyed!" fires; only that one gate renders — earlier gates disappear
 * as you move past them.
 *
 * The ✔ flip marks the lowest-index gate still showing ✖ rather than reading [Section]'s current
 * section at message time: [Section] may increment its own section (and thus trigger our next
 * gate's ✖) on the very same chat message, as a side effect that runs *before* our own listener on
 * that message — so by the time we'd read it, it could already point at the wrong gate.
 */
object GateDisplay {

    private enum class GateState { HIDDEN, DESTROY, DONE }

    private const val GATE_MESSAGE = "The gate has been destroyed!"

    // S1, S2, S3 gate centers.
    private val GATE_POS = arrayOf(
        Vec3(100.5, 122.5, 121.5),
        Vec3(19.5, 122.5, 132.5),
        Vec3(8.5, 113.5, 51.5)
    )

    private val state = arrayOf(GateState.HIDDEN, GateState.HIDDEN, GateState.HIDDEN)

    // guards the once-per-terminals-entry reset: ON_PHASE_CHANGE can fire repeatedly in the phase and re-running it would wipe live S2/S3 progress
    private var armed = false

    // terminals phase time (split 6) at the last phase-change; a practice-mode replay restarts terminals in-phase and the timer jumps back to ~0
    private var lastTermTime = 0.0

    @JvmStatic
    fun init() {
        Events.ON_LOCATION_CHANGE.register { _ -> reset(); armed = false; lastTermTime = 0.0; false }
        Events.ON_PHASE_CHANGE.register {
            // reset on the transition into terminals, not P2 entry: without a fresh P2 phase-change or location change stale DONE state rendered gates as already-blown
            if (Phase.inTerminals()) {
                val t = Phase.getPhaseTime(6)
                if (!armed || t < lastTermTime - 1.0) {
                    reset()
                    show(0) // S1 starts
                    armed = true
                }
                lastTermTime = t
            } else {
                // outside terminals there's no live progress to protect; full clear stops a finished run's DONE state bleeding into the next run's pre-terminals phases
                reset()
                armed = false
                lastTermTime = 0.0
            }
            false
        }
        Events.ON_SECTION_CHANGE.register {
            val s = Section.getSection()
            if (s == 2 || s == 3) show(s - 1) // S2 / S3 start
            false
        }
        Events.ON_GAME_MESSAGE.register { message ->
            if (Floor7.gateDisplayEnabled && Location.inDungeon() && Phase.inTerminals()
                && message.string == GATE_MESSAGE
            ) {
                if (fishmod.utils.debug.Debug.termInfo) {
                    val p = net.minecraft.client.Minecraft.getInstance().player?.position()
                    fishmod.utils.Misc.addChatMessage(Component.literal(
                        "[GateDisplay] destroyed at section=${Section.getSection()} you@" +
                            "${p?.x?.let { "%.1f".format(it) }}, ${p?.y?.let { "%.1f".format(it) }}, ${p?.z?.let { "%.1f".format(it) }}" +
                            " state=${state.toList()}"
                    ))
                }
                markDestroyed()
            }
            false
        }
        // text renders from the END_MAIN pass where submitText's node collector is still live; the handler's matrices are already -camera translated
        fishmod.utils.rendering.RenderingEvents.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!Floor7.gateDisplayEnabled || !Location.inDungeon() || !Phase.inTerminals()) return@register
            if (state.all { it == GateState.HIDDEN }) return@register
            render(ctx, matrices)
        }
    }

    private fun show(index: Int) {
        if (index !in GATE_POS.indices) return
        state[index] = GateState.DESTROY
    }

    /** Marks the lowest-index gate still showing ✖ as ✔ — see class doc for why not "the active gate". */
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
        if (!Floor7.gateDisplayEnabled || !Location.inDungeon()) return
        // Only the gate for the section you're currently in — sections 1-3 map to gates 0-2.
        val i = Section.getSection() - 1
        if (i !in GATE_POS.indices) return
        val text = when (state[i]) {
            GateState.DESTROY -> styled("✖", Constants.RED)
            GateState.DONE -> styled("✔", Constants.GREEN)
            GateState.HIDDEN -> return
        }
        RenderUtils.renderText(ctx, matrices, text, GATE_POS[i], Floor7.gateDisplayScale)
    }

    private fun styled(symbol: String, argb: Int): Component =
        Component.literal(symbol).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(argb and 0xFFFFFF)).withBold(true))
}
