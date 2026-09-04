package fishmod.features.item

import net.minecraft.client.Minecraft

/**
 * Ticks per-item animated dye state and interpolates the current color. Legacy-only: the current
 * item customizer writes no animated dyes; this only drives entries left in a pre-port config.
 */
object AnimatedDyeAnimator {

    private class State(var progress: Float, var onBackCycle: Boolean, var lastColor: Int, var lastFrame: Int)

    private val states = HashMap<String, State>()
    private var frames = 0

    @JvmStatic
    fun tickFrame() { frames++ }

    @JvmStatic
    fun colorFor(uuid: String, dye: ItemCustomizationStore.AnimatedDye): Int {
        // Interpolation below indexes keyframes[k] and keyframes[k+1]; needs at least two.
        if (dye.keyframes.size < 2) {
            val c = dye.keyframes.firstOrNull()?.color ?: 0xFFFFFF
            return (0xFF shl 24) or (c and 0xFFFFFF)
        }
        val state = states.getOrPut(uuid) {
            var progress = 0f
            var onBackCycle = false
            if (dye.delay > 0) {
                if (dye.cycleBack) {
                    onBackCycle = true
                    progress = dye.delay / dye.duration
                } else {
                    progress = 1 - dye.delay / dye.duration
                }
                progress = progress.coerceIn(0f, 1f)
            }
            State(progress, onBackCycle, 0, 0)
        }

        if (state.lastFrame == frames) return state.lastColor
        state.lastFrame = frames

        val deltaTicks = Minecraft.getInstance().deltaTracker.getGameTimeDeltaTicks()
        update(state, dye, deltaTicks)

        var keyframe = 0
        while (keyframe < dye.keyframes.size - 2 && dye.keyframes[keyframe + 1].time < state.progress) keyframe++

        val current = if (state.onBackCycle) dye.keyframes[keyframe + 1] else dye.keyframes[keyframe]
        val next = if (state.onBackCycle) dye.keyframes[keyframe] else dye.keyframes[keyframe + 1]

        var colorProgress = (state.progress - current.time) / (next.time - current.time)
        colorProgress = colorProgress.coerceIn(0f, 1f)

        val color = lerpArgb(current.color, next.color, colorProgress)
        state.lastColor = color
        return color
    }

    private fun update(state: State, dye: ItemCustomizationStore.AnimatedDye, deltaTicks: Float) {
        val v = deltaTicks * 0.05f / dye.duration
        if (state.onBackCycle) {
            state.progress -= v
            if (state.progress <= 0f) {
                state.onBackCycle = false
                state.progress = kotlin.math.abs(state.progress)
            }
        } else {
            state.progress += v
            if (state.progress >= 1f) {
                if (dye.cycleBack) {
                    state.onBackCycle = true
                    state.progress = 2f - state.progress
                } else {
                    state.progress %= 1f
                }
            }
        }
        state.progress = state.progress.coerceIn(0f, 1f)
    }

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    @JvmStatic
    fun clear() { states.clear() }
}
