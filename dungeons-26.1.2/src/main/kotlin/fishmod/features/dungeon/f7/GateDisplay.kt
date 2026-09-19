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

/** Flips the lowest-index gate still showing ✖ rather than trusting [Section]'s current section, since it can increment on the same chat message before our listener runs. */
object GateDisplay {

    private enum class GateState { HIDDEN, DESTROY, DONE }

    private const val GATE_MESSAGE = "The gate has been destroyed!"

    private val GATE_POS = arrayOf(
        Vec3(100.5, 122.5, 121.5),
        Vec3(19.5, 122.5, 132.5),
        Vec3(8.5, 113.5, 51.5)
    )

    private val state = arrayOf(GateState.HIDDEN, GateState.HIDDEN, GateState.HIDDEN)

    // guards the once-per-terminals-entry reset: ON_PHASE_CHANGE fires repeatedly in-phase and would wipe live progress
    private var armed = false

    // practice-mode replay restarts terminals in-phase and this timer jumps back to ~0
    private var lastTermTime = 0.0

    @JvmStatic
    fun init() {
        Events.ON_LOCATION_CHANGE.register { _ -> reset(); armed = false; lastTermTime = 0.0; false }
        Events.ON_PHASE_CHANGE.register {
            if (Phase.inTerminals()) {
                val t = Phase.getPhaseTime(6)
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
        Events.ON_SECTION_CHANGE.register {
            val s = Section.getSection()
            if (s == 2 || s == 3) show(s - 1)
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
        // END_MAIN pass: submitText's node collector is still live; matrices are already -camera translated
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
