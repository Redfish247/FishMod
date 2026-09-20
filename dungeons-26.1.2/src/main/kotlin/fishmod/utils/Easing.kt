package fishmod.utils

object Easing {

    @JvmStatic
    fun easeInOutCubic(x: Float): Float {
        return if (x < 0.5f) 4f * x * x * x else 1f - Math.pow((-2f * x + 2f).toDouble(), 3.0).toFloat() / 2f
    }

    @JvmStatic
    fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val d = x - 1f
        return 1f + c3 * d * d * d + c1 * d * d
    }

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
            if (!fishmod.utils.config.values.FishSettings.fmAnimations) return if (target) 1f else 0f
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
