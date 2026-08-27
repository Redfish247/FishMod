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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.phys.Vec3

/**
 * Big bulky world-space ✖/✔ over each Goldor gate (S1-S3), fixed to the 3 known gate coordinates.
 * A gate shows a red ✖ as soon as its section starts, then flips to a green ✔ on "The gate has been
 * destroyed!". That flip marks the lowest-index gate still showing ✖ rather than reading [Section]'s
 * current section at message time: [Section] may increment its own section (and thus trigger our
 * next gate's ✖) on the very same chat message, as a side effect that runs *before* our own listener
 * on that message — so by the time we'd read it, it could already point at the wrong gate.
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

    @JvmStatic
    fun init() {
        Events.ON_LOCATION_CHANGE.register { _ -> reset(); false }
        Events.ON_PHASE_CHANGE.register {
            if (Phase.inP2()) reset() // fresh run into terminals; clear any stale state from a prior attempt
            if (Phase.inTerminals()) show(0) // S1 starts
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
                markDestroyed()
            }
            false
        }
        // Text renders from the RenderingEvents (END_MAIN) pass — that's where submitText's node
        // collector is still live (same as DungeonWaypoints). The matrices handed to the handler
        // are already -camera translated.
        fishmod.utils.rendering.RenderingEvents.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!Floor7.gateDisplayEnabled || !Location.inDungeon()) return@register
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
        for (i in GATE_POS.indices) {
            val text = when (state[i]) {
                GateState.DESTROY -> styled("✖", Constants.RED)
                GateState.DONE -> styled("✔", Constants.GREEN)
                GateState.HIDDEN -> continue
            }
            RenderUtils.renderText(ctx, matrices, text, GATE_POS[i], Floor7.gateDisplayScale)
        }
    }

    private fun styled(symbol: String, argb: Int): Component =
        Component.literal(symbol).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(argb and 0xFFFFFF)).withBold(true))
}
