package fishmod.utils

/** Cubic ease-in-out timing for smooth open/close animations. */
object Easing {

    @JvmStatic
    fun easeInOutCubic(x: Float): Float {
        return if (x < 0.5f) 4f * x * x * x else 1f - Math.pow((-2f * x + 2f).toDouble(), 3.0).toFloat() / 2f
    }

    /** Tracks a boolean-driven 0..1 progress value, eased over `durationMs`. */
    class Anim(private val durationMs: Long) {
        private var target: Boolean = false
        private var startValue: Float = 0f
        private var startTime: Long = 0L

        fun setTarget(expand: Boolean) {
            if (expand == target) return
            startValue = progress()
            target = expand
            startTime = System.currentTimeMillis()
        }

        fun target(): Boolean = target

        fun progress(): Float {
            val elapsed = System.currentTimeMillis() - startTime
            val t = if (durationMs <= 0) 1f else Math.min(1f, elapsed / durationMs.toFloat())
            val eased = easeInOutCubic(t)
            val end = if (target) 1f else 0f
            return startValue + (end - startValue) * eased
        }

        fun isAnimating(): Boolean {
            val p = progress()
            return p > 0.001f && p < 0.999f
        }
    }
}
