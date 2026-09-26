package fishmod.utils.rendering

object TextFit {
    @JvmStatic
    fun prefixLength(s: String, suffix: String, maxW: Float, minLen: Int, width: (String) -> Float): Int {
        var lo = minLen
        var hi = s.length
        if (lo >= hi) return hi
        if (width(s.substring(0, lo) + suffix) > maxW) return lo
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (width(s.substring(0, mid) + suffix) <= maxW) lo = mid else hi = mid - 1
        }
        return lo
    }
}
