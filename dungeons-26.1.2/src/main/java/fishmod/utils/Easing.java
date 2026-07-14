package fishmod.utils;

/** Cubic ease-in-out timing for smooth open/close animations. */
public final class Easing {

    private Easing() {}

    public static float easeInOutCubic(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    /** Tracks a boolean-driven 0..1 progress value, eased over {@code durationMs}. */
    public static final class Anim {
        private final long durationMs;
        private boolean target = false;
        private float startValue = 0f;
        private long startTime = 0L;

        public Anim(long durationMs) { this.durationMs = durationMs; }

        public void setTarget(boolean expand) {
            if (expand == target) return;
            startValue = progress();
            target = expand;
            startTime = System.currentTimeMillis();
        }

        public boolean target() { return target; }

        public float progress() {
            long elapsed = System.currentTimeMillis() - startTime;
            float t = durationMs <= 0 ? 1f : Math.min(1f, elapsed / (float) durationMs);
            float eased = easeInOutCubic(t);
            float end = target ? 1f : 0f;
            return startValue + (end - startValue) * eased;
        }

        public boolean isAnimating() {
            float p = progress();
            return p > 0.001f && p < 0.999f;
        }
    }
}
